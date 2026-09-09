package com.maestro.reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

import java.util.List;

public final class AlarmScheduler {
    public static final String ACTION_FIRE = "com.maestro.reminder.ACTION_FIRE";
    public static final int BASE_REQUEST_CODE = 7000;
    // Alarm sekali (ONCE) yang terlewat karena HP mati/reboot tetap dibunyikan
    // telat selama masih dalam jendela waktu ini, alih-alih diam-diam mati.
    public static final long CATCH_UP_WINDOW_MS = 60L * 60L * 1000L;
    private AlarmScheduler() {}

    public static void schedule(Context context, Reminder reminder) {
        if (!reminder.enabled || reminder.triggerAt <= 0) return;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        Intent intent = new Intent(context, AlarmReceiver.class)
                .setAction(ACTION_FIRE)
                .putExtra("REMINDER_ID", reminder.id);
        PendingIntent pending = pendingIntent(context, reminder.id, intent);
        // Bersihkan sisa alarm dari pemetaan request code versi lama (id % 100000)
        // supaya tidak ada alarm ganda setelah aplikasi diperbarui.
        Intent legacy = new Intent(context, AlarmReceiver.class)
                .setAction(ACTION_FIRE)
                .putExtra("REMINDER_ID", reminder.id);
        manager.cancel(PendingIntent.getBroadcast(context, BASE_REQUEST_CODE + (int) (reminder.id % 100000L), legacy,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        try {
            // Prioritaskan alarm TEPAT WAKTU; bila izin exact alarm belum aktif
            // (Android 12+), lanjut ke jalur non-exact agar alarm tetap terpasang.
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAt, pending);
        } catch (SecurityException e) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAt, pending);
        }
    }

    public static void cancel(Context context, long reminderId) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) {
            Intent intent = new Intent(context, AlarmReceiver.class).setAction(ACTION_FIRE);
            manager.cancel(pendingIntent(context, reminderId, intent));
        }
    }

    public static void rescheduleAll(Context context) {
        List<Reminder> reminders = ReminderStore.load(context);
        long now = System.currentTimeMillis();
        for (Reminder reminder : reminders) {
            if (!reminder.enabled) continue;
            if (reminder.triggerAt <= now && Reminder.ONCE.equals(reminder.repeat)) {
                // Alarm ONCE yang terlewat (HP mati/reboot): selama belum terlalu
                // lama, bunyikan telat alih-alih mematikannya diam-diam.
                if (now - reminder.triggerAt <= CATCH_UP_WINDOW_MS) {
                    reminder.triggerAt = now + 30000L;
                    schedule(context, reminder);
                } else {
                    reminder.enabled = false;
                }
            } else {
                schedule(context, reminder);
            }
        }
        ReminderStore.save(context, reminders);
    }

    public static boolean canScheduleExactAlarms(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || (manager != null && manager.canScheduleExactAlarms());
    }

    public static Intent exactAlarmSettings(Context context) {
        return new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(android.net.Uri.parse("package:" + context.getPackageName()));
    }

    private static PendingIntent pendingIntent(Context context, long id, Intent intent) {
        return PendingIntent.getBroadcast(context, requestCode(id), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static int requestCode(long id) {
        // ID dari WebView berukuran 13 digit (Date.now()). Pemetaan lama
        // id % 100000 membuat dua pengingat yang dibuat berdekatan saling
        // menimpa alarmnya (alarm hilang/berpindah waktu). Gunakan sebaran
        // hash 64-bit yang stabil agar tiap ID mendapat kode uniknya sendiri.
        long h = id ^ (id >>> 32);
        h = (h * 0x9E3779B97F4A7C15L) >>> 33;
        return BASE_REQUEST_CODE + (int) (h % 1000000000L);
    }

    public static int notificationId(long id) {
        // Konsisten dengan requestCode: tiap pengingat harus punya ID notifikasi
        // unik agar tidak saling menimpa dan dismiss salah pengingat.
        return AlarmReceiver.BASE_NOTIFICATION_ID + (requestCode(id) - BASE_REQUEST_CODE);
    }
}
