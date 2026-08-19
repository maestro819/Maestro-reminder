package com.maestro.reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;

public final class AlarmScheduler {
    public static final String PREFS = "maestro_alarm_prefs";
    public static final String KEY_TRIGGER_AT = "trigger_at";
    public static final String KEY_ACTIVITY_NAME = "activity_name";
    public static final String KEY_USER_NAME = "user_name";
    public static final String ACTION_FIRE = "com.maestro.reminder.ACTION_FIRE";
    public static final int REQUEST_CODE = 7001;

    private AlarmScheduler() {}

    public static void saveAndSchedule(Context context, long triggerAt, String activityName, String userName) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putLong(KEY_TRIGGER_AT, triggerAt)
                .putString(KEY_ACTIVITY_NAME, activityName)
                .putString(KEY_USER_NAME, userName)
                .apply();
        schedule(context, triggerAt, activityName, userName);
    }

    public static void schedule(Context context, long triggerAt, String activityName, String userName) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null || triggerAt <= 0) return;

        Intent intent = new Intent(context, AlarmReceiver.class)
                .setAction(ACTION_FIRE)
                .putExtra("ACTIVITY_NAME", activityName)
                .putExtra("USER_NAME", userName);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
    }

    public static void cancel(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            Intent intent = new Intent(context, AlarmReceiver.class).setAction(ACTION_FIRE);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context, REQUEST_CODE, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            alarmManager.cancel(pendingIntent);
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    public static void reschedule(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long triggerAt = prefs.getLong(KEY_TRIGGER_AT, 0L);
        if (triggerAt == 0L) return;
        if (triggerAt <= System.currentTimeMillis()) {
            schedule(context, System.currentTimeMillis() + 1000L,
                    prefs.getString(KEY_ACTIVITY_NAME, "Aktivitas"),
                    prefs.getString(KEY_USER_NAME, "Maestro"));
        } else {
            schedule(context, triggerAt,
                    prefs.getString(KEY_ACTIVITY_NAME, "Aktivitas"),
                    prefs.getString(KEY_USER_NAME, "Maestro"));
        }
    }

    public static boolean canScheduleExactAlarms(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || (alarmManager != null && alarmManager.canScheduleExactAlarms());
    }

    public static Intent exactAlarmSettings(Context context) {
        return new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(android.net.Uri.parse("package:" + context.getPackageName()));
    }
}
