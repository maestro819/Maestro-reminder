package com.maestro.reminder;

import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AlarmScreenActivity extends AppCompatActivity {
    private static final int DEFAULT_NOTIFICATION_ID = 1001;
    private int notificationId = DEFAULT_NOTIFICATION_ID;
    private Vibrator vibrator;
    private Ringtone ringtone;
    private final Handler soundHandler = new Handler(Looper.getMainLooper());
    private final Runnable replaySound = new Runnable() {
        @Override public void run() {
            if (ringtone != null) { if (!ringtone.isPlaying()) ringtone.play(); soundHandler.postDelayed(this, 2500L); }
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { setShowWhenLocked(true); setTurnScreenOn(true); }
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        setContentView(R.layout.activity_alarm_screen);
        notificationId = getIntent().getIntExtra("NOTIFICATION_ID", DEFAULT_NOTIFICATION_ID);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        startAlarmSound();
        startAlarmVibration();

        String name = getIntent().getStringExtra("USER_NAME"); if (name == null) name = "Maestro";
        String activity = getIntent().getStringExtra("ACTIVITY_NAME"); if (activity == null) activity = "Aktivitas";
        String icon = getIntent().getStringExtra("ACTIVITY_ICON"); if (icon == null) icon = "⏰";
        String kind = getIntent().getStringExtra("KIND"); boolean sleep = Reminder.SLEEP.equals(kind); String message = getIntent().getStringExtra("ALARM_MESSAGE"); String category = getIntent().getStringExtra("CATEGORY");
        TextView time = findViewById(R.id.txtAlarmTime); TextView title = findViewById(R.id.txtAlarmActivity); TextView greeting = findViewById(R.id.txtAlarmGreeting); TextView user = findViewById(R.id.txtAlarmUser); TextView messageView = findViewById(R.id.txtAlarmMessage);
        time.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        title.setText(icon + "  " + activity.toUpperCase(Locale.getDefault()));
        String period = period(new Date()); greeting.setText(sleep ? name + ", bangun yuk. Selamat " + period + "." : name + ", selamat " + period + ". Waktunya " + activity.toLowerCase(Locale.getDefault()) + ".");
        user.setText(sleep ? "MAESTRO • Alarm tidur • istirahat sudah cukup" : "MAESTRO • " + (category == null ? "Pengingat" : category) + " • tetap konsisten"); messageView.setText(message == null || message.trim().isEmpty() ? "Jangan sampai jadwalmu terlewat." : message.replace("{name}", name));

        Button dismiss = findViewById(R.id.btnDismissAlarm); dismiss.setOnClickListener(v -> { stopAlarmSignal(); finish(); });
        Button snooze = findViewById(R.id.btnSnoozeAlarm); snooze.setOnClickListener(v -> { AlarmReceiver.snoozeActiveAlarm(this, 5); finish(); });
    }

    private String period(Date date) { int h=date.getHours(); if(h>=5&&h<11)return "pagi"; if(h>=11&&h<15)return "siang"; if(h>=15&&h<18)return "sore"; return "malam"; }

    private void startAlarmVibration() {
        try {
            if (vibrator == null || !vibrator.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(android.os.VibrationEffect.createWaveform(new long[]{0, 900, 500, 900}, 0));
            else vibrator.vibrate(new long[]{0, 900, 500, 900}, 0);
        } catch (Exception ignored) {}
    }

    private void startAlarmSound() {
        try {
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM); if (alarmUri == null) alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            ringtone = RingtoneManager.getRingtone(this, alarmUri);
            if (ringtone != null) {
                ringtone.setStreamType(AudioManager.STREAM_ALARM);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) ringtone.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                ringtone.play(); soundHandler.postDelayed(replaySound, 1800L);
            }
        } catch (Exception ignored) {}
    }

    @Override protected void onDestroy() { stopAlarmSignal(); super.onDestroy(); }

    private void stopAlarmSignal() {
        soundHandler.removeCallbacks(replaySound); if (ringtone != null) { if (ringtone.isPlaying()) ringtone.stop(); ringtone = null; }
        if (vibrator != null) vibrator.cancel(); AlarmReceiver.stopActiveAlarm(this);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE); if (manager != null) manager.cancel(notificationId);
    }
}
