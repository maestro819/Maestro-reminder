package com.maestro.reminder;

import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AlarmScreenActivity extends AppCompatActivity implements AudioManager.OnAudioFocusChangeListener {
    private static final int DEFAULT_NOTIFICATION_ID = 1001;

    private int notificationId = DEFAULT_NOTIFICATION_ID;
    private Vibrator vibrator;
    private MediaPlayer mediaPlayer;
    private AudioManager audioManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout snoozeOverlay;
    private int baseBodyLeft, baseBodyTop, baseBodyRight, baseBodyBottom, baseSheetBottom;
    private boolean isAlarmActive = true;
    private Object audioFocusRequest;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AlarmReceiver.stopActiveAlarm(this);
        configureAlarmWindow();
        setContentView(R.layout.activity_alarm_screen);

        View root = findViewById(R.id.alarmRoot);
        LinearLayout body = findViewById(R.id.alarmBody);
        snoozeOverlay = findViewById(R.id.snoozeOverlay);
        applyResponsiveInsets(root, body, findViewById(R.id.snoozeSheet));
        root.post(() -> applyVercelBackground(body));

        notificationId = getIntent().getIntExtra("NOTIFICATION_ID", DEFAULT_NOTIFICATION_ID);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        
        requestHighPriorityAudioFocus();
        startAlarmSound();
        startAlarmVibration();
        bindAlarmContent();
        bindSnoozeActions();
    }

    private void requestHighPriorityAudioFocus() {
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(playbackAttributes)
                        .setAcceptsDelayedFocusGain(true)
                        .setOnAudioFocusChangeListener(this)
                        .build();
                audioManager.requestAudioFocus((AudioFocusRequest) audioFocusRequest);
            } else {
                audioManager.requestAudioFocus(this, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            }
        } catch (Exception ignored) {}
    }

    @Override public void onAudioFocusChange(int focusChange) {
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            // Jika fokus hilang, tunggu sebentar lalu ambil kembali dan pastikan suara jalan
            if (isAlarmActive) {
                handler.postDelayed(() -> {
                    requestHighPriorityAudioFocus();
                    if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                        mediaPlayer.start();
                    }
                }, 1000L);
            }
        } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            if (isAlarmActive && mediaPlayer != null && !mediaPlayer.isPlaying()) {
                mediaPlayer.start();
            }
        }
    }

    private void bindAlarmContent() {
        String name = valueOrDefault(getIntent().getStringExtra("USER_NAME"), "Maestro");
        String activity = valueOrDefault(getIntent().getStringExtra("ACTIVITY_NAME"), "Aktivitas");
        String icon = valueOrDefault(getIntent().getStringExtra("ACTIVITY_ICON"), "⏰");
        String kind = getIntent().getStringExtra("KIND");
        String category = valueOrDefault(getIntent().getStringExtra("CATEGORY"), "GENERAL");
        String message = getIntent().getStringExtra("ALARM_MESSAGE");
        String alarmTime = getIntent().getStringExtra("ALARM_TIME");
        if (alarmTime == null || alarmTime.trim().isEmpty()) alarmTime = currentTime();

        String period = timePeriod(alarmTime);
        boolean sleep = Reminder.SLEEP.equals(kind) || "SLEEP".equalsIgnoreCase(category);

        ((TextView) findViewById(R.id.txtAlarmTime)).setText(alarmTime);
        ((TextView) findViewById(R.id.txtAlarmActivity)).setText(icon + " " + activity.toUpperCase(Locale.getDefault()));
        ((TextView) findViewById(R.id.txtAlarmGreeting)).setText(sleep 
                ? name + ", bangun yuk. Selamat " + period + "." 
                : name + ", selamat " + period + " \uD83C\uDF10");
        ((TextView) findViewById(R.id.txtAlarmAction)).setText("Waktunya " + activity.toLowerCase(Locale.getDefault()) + ".");
        ((TextView) findViewById(R.id.txtAlarmMessage)).setText(message);
    }

    private void bindSnoozeActions() {
        findViewById(R.id.btnSnoozeAlarm).setOnClickListener(v -> openSnoozeSheet());
        findViewById(R.id.btnDismissAlarm).setOnClickListener(v -> { stopAlarmSignal(); finish(); });
        snoozeOverlay.setOnClickListener(v -> closeSnoozeSheet());
        findViewById(R.id.snoozeSheet).setOnClickListener(v -> {});
        findViewById(R.id.btnSnooze5).setOnClickListener(v -> applySnooze(5));
        findViewById(R.id.btnSnooze10).setOnClickListener(v -> applySnooze(10));
        findViewById(R.id.btnSnooze15).setOnClickListener(v -> applySnooze(15));
        findViewById(R.id.btnSnooze30).setOnClickListener(v -> applySnooze(30));
    }

    private void openSnoozeSheet() {
        snoozeOverlay.setVisibility(View.VISIBLE);
        snoozeOverlay.bringToFront();
    }

    private void closeSnoozeSheet() {
        if (snoozeOverlay != null) snoozeOverlay.setVisibility(View.GONE);
    }

    private void applySnooze(int minutes) {
        closeSnoozeSheet();
        AlarmReceiver.snoozeActiveAlarm(this, minutes);
        stopAlarmSignal();
        finish();
    }

    @Override public void onBackPressed() {
        if (snoozeOverlay != null && snoozeOverlay.getVisibility() == View.VISIBLE) closeSnoozeSheet();
    }

    private void configureAlarmWindow() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        WindowCompat.setDecorFitsSystemWindows(window, false);

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(false);
            controller.setAppearanceLightNavigationBars(false);
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.systemBars());
        }
    }

    private void forceMaxVolume() {
        try {
            if (audioManager != null) {
                int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, max, 0);
            }
        } catch (Exception ignored) {}
    }

    private void startAlarmSound() {
        try {
            forceMaxVolume();
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, alarmUri);
            
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                    .build();
            
            mediaPlayer.setAudioAttributes(audioAttributes);
            mediaPlayer.setLooping(true);
            mediaPlayer.setVolume(1.0f, 1.0f);
            
            mediaPlayer.setOnPreparedListener(mp -> {
                if (isAlarmActive) mp.start();
            });
            
            mediaPlayer.prepareAsync();
            
            // Re-enforce volume periodically
            handler.postDelayed(new Runnable() {
                @Override public void run() {
                    if (isAlarmActive) {
                        forceMaxVolume();
                        handler.postDelayed(this, 3000L);
                    }
                }
            }, 3000L);
            
        } catch (Exception ignored) {}
    }

    private void startAlarmVibration() {
        try {
            if (vibrator == null || !vibrator.hasVibrator()) return;
            long[] pattern = {0, 1000, 500, 1000};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, 0));
            } else {
                vibrator.vibrate(pattern, 0);
            }
        } catch (Exception ignored) {}
    }

    private void stopAlarmSignal() {
        isAlarmActive = false;
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        if (vibrator != null) vibrator.cancel();
        if (audioManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest((AudioFocusRequest) audioFocusRequest);
            } else {
                audioManager.abandonAudioFocus(this);
            }
        }
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(notificationId);
    }

    @Override protected void onDestroy() {
        stopAlarmSignal();
        super.onDestroy();
    }

    private void applyResponsiveInsets(@NonNull View root, @NonNull View body, @NonNull View sheet) {
        baseBodyLeft = body.getPaddingLeft();
        baseBodyTop = body.getPaddingTop();
        baseBodyRight = body.getPaddingRight();
        baseBodyBottom = body.getPaddingBottom();
        baseSheetBottom = sheet.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            body.setPadding(baseBodyLeft + bars.left, baseBodyTop + bars.top, baseBodyRight + bars.right, baseBodyBottom + bars.bottom);
            sheet.setPadding(sheet.getPaddingLeft(), sheet.getPaddingTop(), sheet.getPaddingRight(), baseSheetBottom + bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void applyVercelBackground(@NonNull View body) {
        GradientDrawable background = new GradientDrawable();
        background.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        background.setColors(new int[]{0xFF37306D, 0xFF0B1028});
        background.setGradientCenter(0.50f, 0.10f);
        background.setGradientRadius(Math.max(body.getWidth(), body.getHeight()) * 0.95f);
        body.setBackground(background);
    }

    private String currentTime() { return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()); }

    private String timePeriod(String time) {
        try {
            int hour = Integer.parseInt(time.split(":")[0]);
            if (hour >= 5 && hour < 11) return "pagi";
            if (hour >= 11 && hour < 15) return "siang";
            if (hour >= 15 && hour < 18) return "sore";
            return "malam";
        } catch (Exception e) { return "hari"; }
    }

    private String valueOrDefault(String val, String def) { return (val == null || val.trim().isEmpty()) ? def : val; }
}
