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
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class AlarmReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "maestro_alarm_channel";
    private static final int NOTIFICATION_ID = 1001;

    @Override
    public void onReceive(Context context, Intent intent) {
        String activityName = intent.getStringExtra("ACTIVITY_NAME");
        String userName = intent.getStringExtra("USER_NAME");

        if (activityName == null) {
            activityName = "Aktivitas";
        }
        if (userName == null) {
            userName = "Maestro";
        }

        Log.d("MaestroAlarm", "Alarm berbunyi untuk: " + userName + ", aktivitas: " + activityName);

        // Getar HP saat alarm berbunyi
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 1000, 500, 1000}, 0));
            } else {
                vibrator.vibrate(new long[]{0, 1000, 500, 1000}, 0);
            }
        }

        // Intent untuk membuka AlarmScreenActivity secara full-screen
        Intent alarmScreenIntent = new Intent(context, AlarmScreenActivity.class);
        alarmScreenIntent.putExtra("ACTIVITY_NAME", activityName);
        alarmScreenIntent.putExtra("USER_NAME", userName);
        alarmScreenIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                0,
                alarmScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        createNotificationChannel(context);

        // PENTING: di Android 10+ (API 29+), memanggil context.startActivity()
        // langsung dari BroadcastReceiver (background) BISA DIBLOKIR sistem.
        // Cara yang reliable adalah lewat Notification.setFullScreenIntent(),
        // yang diizinkan OS untuk membuka activity di atas lock screen.
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Waktunya: " + activityName)
                .setContentText("Pengingat untuk " + userName)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setContentIntent(fullScreenPendingIntent)
                .setAutoCancel(true)
                .setOngoing(true);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Maestro Reminder Alarm",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifikasi alarm pengingat Maestro Reminder");
            channel.enableVibration(true);
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}

