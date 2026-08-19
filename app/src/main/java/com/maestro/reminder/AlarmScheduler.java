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
    private AlarmScheduler() {}

    public static void schedule(Context context, Reminder reminder) {
        if (!reminder.enabled || reminder.triggerAt <= 0) return;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        Intent intent = new Intent(context, AlarmReceiver.class)
                .setAction(ACTION_FIRE)
                .putExtra("REMINDER_ID", reminder.id);
        PendingIntent pending = pendingIntent(context, reminder.id, intent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAt, pending);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAt, pending);
        } else {
            manager.setExact(AlarmManager.RTC_WAKEUP, reminder.triggerAt, pending);
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
        for (Reminder reminder : reminders) {
            if (reminder.enabled) {
                if (reminder.triggerAt <= System.currentTimeMillis() && Reminder.ONCE.equals(reminder.repeat)) {
                    reminder.enabled = false;
                } else {
                    schedule(context, reminder);
                }
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
        return BASE_REQUEST_CODE + (int) (id % 100000);
    }
}
