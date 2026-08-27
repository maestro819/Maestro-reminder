package com.maestro.reminder;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;

import androidx.core.app.NotificationCompat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AlarmReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "maestro_alarm_channel";
    public static final int BASE_NOTIFICATION_ID = 1001;
    private static Vibrator activeVibrator;
    private static NotificationManager activeNotificationManager;
    private static int activeNotificationId = -1;
    private static long activeReminderId = -1L;

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_TIME_CHANGED.equals(action) || Intent.ACTION_TIMEZONE_CHANGED.equals(action) || "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED".equals(action)) {
            AlarmScheduler.rescheduleAll(context); return;
        }
        if (!AlarmScheduler.ACTION_FIRE.equals(action)) return;
        long id = intent.getLongExtra("REMINDER_ID", -1L);
        Reminder reminder = ReminderStore.find(context, id);
        if (reminder == null || !reminder.enabled) {
            AlarmScheduler.cancel(context, id);
            return;
        }

        int notificationId = notificationId(id);
        activeReminderId = id;
        createChannel(context);
        String ownerName = context.getSharedPreferences("maestro_user", Context.MODE_PRIVATE).getString("name", "Maestro");
        
        String formattedTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(reminder.triggerAt));
        
        Intent screen = new Intent(context, AlarmScreenActivity.class)
                .putExtra("ACTIVITY_NAME", reminder.title)
                .putExtra("ACTIVITY_ICON", reminder.icon)
                .putExtra("KIND", reminder.kind)
                .putExtra("USER_NAME", ownerName)
                .putExtra("CATEGORY", reminder.category)
                .putExtra("ALARM_MESSAGE", alarmMessage(reminder, ownerName))
                .putExtra("ALARM_TIME", formattedTime)
                .putExtra("NOTIFICATION_ID", notificationId)
                .putExtra("REMINDER_ID", id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK 
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP 
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        
        // Gunakan FLAG_IMMUTABLE untuk keamanan, tapi pastikan sistem bisa membukanya
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        
        PendingIntent screenPending = PendingIntent.getActivity(context, notificationId, screen, flags);
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(com.maestro.reminder.R.drawable.ic_alarm)
                .setContentTitle(reminder.icon + "  " + reminder.title)
                .setContentText(alarmMessage(reminder, ownerName))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(screenPending, true) // Kunci untuk full screen
                .setContentIntent(screenPending)
                .setAutoCancel(false)
                .setOngoing(true);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(notificationId, builder.build());
            activeNotificationManager = manager;
            activeNotificationId = notificationId;
        }

        // Coba buka activity secara langsung
        // Ini seringkali butuh izin "Display over other apps" pada Android 10+
        try {
            context.startActivity(screen);
        } catch (Exception e) {
            // Jika gagal, sistem akan tetap mencoba lewat FullScreenIntent notifikasi
        }

        // Vibrasi tetap dijalankan di sini sebagai backup jika activity belum terbuka
        activeVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (activeVibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activeVibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 1000, 500, 1000}, 0));
            } else {
                activeVibrator.vibrate(new long[]{0, 1000, 500, 1000}, 0);
            }
        }

        // Reschedule
        if (Reminder.ONCE.equals(reminder.repeat)) {
            reminder.enabled = false;
        } else {
            reminder.triggerAt = nextTrigger(reminder);
            AlarmScheduler.schedule(context, reminder);
        }
        List<Reminder> items = ReminderStore.load(context);
        for (int i = 0; i < items.size(); i++) if (items.get(i).id == reminder.id) items.set(i, reminder);
        ReminderStore.save(context, items);
    }

    private String alarmMessage(Reminder reminder, String ownerName) {
        String period = period(reminder.triggerAt);
        if (Reminder.SLEEP.equals(reminder.kind)) return ownerName + ", bangun yuk. Selamat " + period + ".";
        if (reminder.message != null && !reminder.message.trim().isEmpty()) return reminder.message.replace("{name}", ownerName);
        String[] messages;
        if ("HEALTH".equals(reminder.category)) messages = new String[]{"{name}, waktunya menjaga kesehatan.", "Yuk lakukan kebiasaan sehatmu sekarang."};
        else if ("SPORT".equals(reminder.category)) messages = new String[]{"{name}, waktunya bergerak.", "Ayo olahraga sebentar agar tubuh tetap aktif."};
        else if ("MEDICINE".equals(reminder.category)) messages = new String[]{"{name}, jangan lupa obatmu.", "Waktunya minum obat sesuai jadwal."};
        else if ("WORK".equals(reminder.category)) messages = new String[]{"{name}, waktunya fokus pada pekerjaan.", "Satu tugas dulu, pelan-pelan yang penting selesai."};
        else if ("STUDY".equals(reminder.category)) messages = new String[]{"{name}, waktunya belajar.", "Saatnya fokus pada pelajaranmu."};
        else if ("WORSHIP".equals(reminder.category)) messages = new String[]{"{name}, waktunya ibadah.", "Luangkan waktu untuk menenangkan hati."};
        else if ("MENGAJI".equals(reminder.category)) messages = new String[]{"{name}, waktunya mengaji.", "Luangkan waktu untuk membaca dan memahami Al-Qur'an."};
        else messages = new String[]{"{name}, ada pengingat untukmu.", "Jangan sampai jadwalmu terlewat."};
        return messages[(int)(Math.abs(reminder.id) % messages.length)].replace("{name}", ownerName);
    }

    private String period(long time) {
        Calendar c=Calendar.getInstance(); c.setTimeInMillis(time); int h=c.get(Calendar.HOUR_OF_DAY);
        if(h>=5&&h<11) return "pagi"; if(h>=11&&h<15) return "siang"; if(h>=15&&h<18) return "sore"; return "malam";
    }

    private long nextTrigger(Reminder reminder) {
        Calendar next = Calendar.getInstance();
        Calendar anchor = Calendar.getInstance(); anchor.setTimeInMillis(reminder.anchorAt > 0 ? reminder.anchorAt : reminder.triggerAt);
        next.set(Calendar.HOUR_OF_DAY, anchor.get(Calendar.HOUR_OF_DAY)); next.set(Calendar.MINUTE, anchor.get(Calendar.MINUTE)); next.set(Calendar.SECOND, 0); next.set(Calendar.MILLISECOND, 0);
        next.add(Calendar.DAY_OF_YEAR, 1);
        if (reminder.repeat != null && reminder.repeat.startsWith("WEEKLY:")) {
            String days = reminder.repeat.substring("WEEKLY:".length());
            while (next.getTimeInMillis() <= System.currentTimeMillis() || !days.contains(String.valueOf(next.get(Calendar.DAY_OF_WEEK)))) next.add(Calendar.DAY_OF_YEAR, 1);
            return next.getTimeInMillis();
        }
        if (Reminder.DAILY.equals(reminder.repeat)) return next.getTimeInMillis();
        if (Reminder.WEEKLY.equals(reminder.repeat)) { next.add(Calendar.DAY_OF_YEAR, 6); return next.getTimeInMillis(); }
        while (next.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || next.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) next.add(Calendar.DAY_OF_YEAR, 1);
        return next.getTimeInMillis();
    }

    public static void stopActiveAlarm(Context context) {
        if (activeVibrator != null) { activeVibrator.cancel(); activeVibrator = null; }
        NotificationManager manager = activeNotificationManager;
        if (manager == null) manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null && activeNotificationId >= 0) manager.cancel(activeNotificationId);
        activeNotificationManager = null;
        activeNotificationId = -1;
        activeReminderId = -1L;
    }

    public static void snoozeActiveAlarm(Context context, int minutes) {
        long id = activeReminderId;
        if (id < 0) return;
        Reminder reminder = ReminderStore.find(context, id);
        if (reminder == null) return;
        reminder.triggerAt = System.currentTimeMillis() + Math.max(1, minutes) * 60000L;
        java.util.List<Reminder> items = ReminderStore.load(context);
        for (int i = 0; i < items.size(); i++) if (items.get(i).id == id) items.set(i, reminder);
        ReminderStore.save(context, items);
        AlarmScheduler.cancel(context, id);
        AlarmScheduler.schedule(context, reminder);
        stopActiveAlarm(context);
    }

    private int notificationId(long id) { return BASE_NOTIFICATION_ID + (int) (id % 100000); }

    private void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Maestro Reminder Alarm", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Notifikasi reminder Maestro Reminder");
            channel.enableVibration(true);
            channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            channel.setVibrationPattern(new long[]{0, 1000, 500, 1000});
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            // Sound akan ditangani oleh Activity untuk bypass headset
            channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), audioAttributes);
            NotificationManager manager = context.getSystemService(NotificationManager.class); if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}
