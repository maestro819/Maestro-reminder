package com.maestro.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

public class AlarmReceiver extends BroadcastReceiver {

    @Override
    onReceive(Context context, Intent intent) {
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

        // Membuka AlarmScreenActivity secara Full-Screen di atas Lock Screen
        Intent alarmScreenIntent = new Intent(context, AlarmScreenActivity.class);
        alarmScreenIntent.putExtra("ACTIVITY_NAME", activityName);
        alarmScreenIntent.putExtra("USER_NAME", userName);
        alarmScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(alarmScreenIntent);
    }
}

