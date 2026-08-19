package com.maestro.reminder;

import android.app.KeyguardManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class AlarmScreenActivity extends AppCompatActivity {

    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Tampilkan alarm di atas layar kunci.
        Window window = getWindow();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            );
        }

        window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        setContentView(R.layout.activity_alarm_screen);

        startVibration();
        startAlarmSound();

        Button stopButton = findViewById(R.id.stopAlarmButton);

        stopButton.setOnClickListener(v -> stopAlarm());
    }

    private void startAlarmSound() {

        try {

            mediaPlayer = MediaPlayer.create(
                    this,
                    android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
            );

            if (mediaPlayer != null) {

                mediaPlayer.setAudioAttributes(
                        new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(
                                        AudioAttributes.CONTENT_TYPE_SONIFICATION
                                )
                                .build()
                );

                mediaPlayer.setLooping(true);
                mediaPlayer.start();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startVibration() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            VibratorManager vibratorManager =
                    (VibratorManager) getSystemService(
                            Context.VIBRATOR_MANAGER_SERVICE
                    );

            vibrator = vibratorManager.getDefaultVibrator();

        } else {

            vibrator = (Vibrator) getSystemService(
                    Context.VIBRATOR_SERVICE
            );
        }

        if (vibrator == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            long[] pattern = {
                    0,
                    800,
                    500,
                    800,
                    500
            };

            vibrator.vibrate(
                    VibrationEffect.createWaveform(
                            pattern,
                            0
                    )
            );

        } else {

            long[] pattern = {
                    0,
                    800,
                    500,
                    800,
                    500
            };

            vibrator.vibrate(pattern, 0);
        }
    }

    private void stopAlarm() {

        if (mediaPlayer != null) {

            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }

            mediaPlayer.release();
            mediaPlayer = null;
        }

        if (vibrator != null) {
            vibrator.cancel();
        }

        finish();
    }

    @Override
    protected void onDestroy() {

        if (mediaPlayer != null) {

            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }

            mediaPlayer.release();
            mediaPlayer = null;
        }

        if (vibrator != null) {
            vibrator.cancel();
        }

        super.onDestroy();
    }
}
