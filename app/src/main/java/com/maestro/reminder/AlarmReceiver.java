package com.maestro.reminder;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.core.app.NotificationCompat;

public class AlarmReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "maestro_alarm_channel";
    public static final int NOTIFICATION_ID = 1001;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action)) {
            AlarmScheduler.reschedule(context);
            return;
        }
        if (!AlarmScheduler.ACTION_FIRE.equals(action)) return;

        String activityName = intent.getStringExtra("ACTIVITY_NAME");
        String userName = intent.getStringExtra("USER_NAME");
        if (activityName == null) activityName = "Aktivitas";
        if (userName == null) userName = "Maestro";

        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 1000, 500, 1000}, 0));
            } else {
                vibrator.vibrate(new long[]{0, 1000, 500, 1000}, 0);
            }
        }

        createNotificationChannel(context);
        Intent screenIntent = new Intent(context, AlarmScreenActivity.class)
                .putExtra("ACTIVITY_NAME", activityName)
                .putExtra("USER_NAME", userName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent screenPendingIntent = PendingIntent.getActivity(
                context, 0, screenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Waktunya: " + activityName)
                .setContentText("Pengingat untuk " + userName)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(screenPendingIntent, true)
                .setContentIntent(screenPendingIntent)
                .setAutoCancel(true)
                .setOngoing(true);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, builder.build());

        context.getSharedPreferences(AlarmScheduler.PREFS, Context.MODE_PRIVATE)
                .edit().remove(AlarmScheduler.KEY_TRIGGER_AT).apply();
    }

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Maestro Reminder Alarm", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Notifikasi alarm pengingat Maestro Reminder");
            channel.enableVibration(true);
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}
