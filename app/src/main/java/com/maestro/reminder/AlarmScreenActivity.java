package com.maestro.reminder;

import android.app.NotificationManager;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
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
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
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
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class AlarmScreenActivity extends AppCompatActivity {
    private static final int DEFAULT_NOTIFICATION_ID = 1001;

    private int notificationId = DEFAULT_NOTIFICATION_ID;
    private Vibrator vibrator;
    private Ringtone ringtone;
    private final Handler soundHandler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private int basePaddingLeft;
    private int basePaddingTop;
    private int basePaddingRight;
    private int basePaddingBottom;

    private final Runnable replaySound = new Runnable() {
        @Override public void run() {
            if (ringtone != null) {
                if (!ringtone.isPlaying()) ringtone.play();
                soundHandler.postDelayed(this, 2500L);
            }
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureAlarmWindow();
        setContentView(R.layout.activity_alarm_screen);

        View root = findViewById(R.id.alarmRoot);
        applyResponsiveSafeArea(root);
        root.post(() -> applyVercelBackground(root));

        notificationId = getIntent().getIntExtra("NOTIFICATION_ID", DEFAULT_NOTIFICATION_ID);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        startAlarmSound();
        startAlarmVibration();

        String name = valueOrDefault(getIntent().getStringExtra("USER_NAME"), "Maestro");
        String activity = valueOrDefault(getIntent().getStringExtra("ACTIVITY_NAME"), "Aktivitas");
        String icon = valueOrDefault(getIntent().getStringExtra("ACTIVITY_ICON"), "⏰");
        String kind = getIntent().getStringExtra("KIND");
        String category = valueOrDefault(getIntent().getStringExtra("CATEGORY"), "GENERAL");
        String message = getIntent().getStringExtra("ALARM_MESSAGE");
        String alarmTime = getIntent().getStringExtra("ALARM_TIME");
        if (alarmTime == null || alarmTime.trim().isEmpty()) alarmTime = currentTime();

        TextView time = findViewById(R.id.txtAlarmTime);
        TextView title = findViewById(R.id.txtAlarmActivity);
        TextView greeting = findViewById(R.id.txtAlarmGreeting);
        TextView action = findViewById(R.id.txtAlarmAction);
        TextView messageView = findViewById(R.id.txtAlarmMessage);

        String period = timePeriod(alarmTime);
        boolean sleep = Reminder.SLEEP.equals(kind) || "SLEEP".equalsIgnoreCase(category);

        time.setText(alarmTime);
        title.setText(icon + " " + activity.toUpperCase(Locale.getDefault()));
        greeting.setText(sleep
                ? name + ", bangun yuk 🌅"
                : name + ", selamat " + period + " " + periodEmoji(period));
        action.setText(sleep
                ? "Waktunya memulai hari."
                : "Waktunya " + activity.toLowerCase(Locale.getDefault()) + ".");
        messageView.setText(resolveMessage(message, category, name));

        Button dismiss = findViewById(R.id.btnDismissAlarm);
        dismiss.setOnClickListener(v -> {
            stopAlarmSignal();
            finish();
        });

        Button snooze = findViewById(R.id.btnSnoozeAlarm);
        snooze.setOnClickListener(v -> {
            AlarmReceiver.snoozeActiveAlarm(this, 5);
            finish();
        });
    }

    private void configureAlarmWindow() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
        window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        WindowCompat.setDecorFitsSystemWindows(window, false);

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(false);
            controller.setAppearanceLightNavigationBars(false);
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.systemBars());
        }
    }

    private void applyResponsiveSafeArea(@NonNull View root) {
        basePaddingLeft = root.getPaddingLeft();
        basePaddingTop = root.getPaddingTop();
        basePaddingRight = root.getPaddingRight();
        basePaddingBottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(
                    basePaddingLeft + bars.left,
                    basePaddingTop + bars.top,
                    basePaddingRight + bars.right,
                    basePaddingBottom + bars.bottom
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void applyVercelBackground(@NonNull View root) {
        GradientDrawable background = new GradientDrawable();
        background.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        background.setColors(new int[]{0xFF37306D, 0xFF0B1028});
        background.setGradientCenter(0.50f, 0.10f);
        background.setGradientRadius(Math.max(root.getWidth(), root.getHeight()) * 0.95f);
        root.setBackground(background);
    }

    private String currentTime() {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
    }

    private String timePeriod(String value) {
        try {
            int hour = Integer.parseInt(value.split(":")[0]);
            if (hour >= 5 && hour < 11) return "pagi";
            if (hour >= 11 && hour < 15) return "siang";
            if (hour >= 15 && hour < 18) return "sore";
        } catch (Exception ignored) {
            // Gunakan malam sebagai fallback yang sama dengan HTML.
        }
        return "malam";
    }

    private String periodEmoji(String period) {
        if ("pagi".equals(period)) return "☀️";
        if ("siang".equals(period)) return "🌤️";
        if ("sore".equals(period)) return "🌇";
        return "🌙";
    }

    private String resolveMessage(String explicitMessage, String category, String name) {
        if (explicitMessage != null && !explicitMessage.trim().isEmpty()) {
            return explicitMessage.replace("{name}", name);
        }

        String[] messages;
        if ("HEALTH".equalsIgnoreCase(category)) {
            messages = new String[]{"Jangan lupa luangkan waktu untuk tubuhmu.", "Sedikit konsisten hari ini berdampak besar untuk kesehatanmu."};
        } else if ("SPORT".equalsIgnoreCase(category)) {
            messages = new String[]{"Tubuhmu akan berterima kasih untuk gerakan kecil hari ini.", "Tidak perlu sempurna, yang penting terus bergerak."};
        } else if ("MEDICINE".equalsIgnoreCase(category)) {
            messages = new String[]{"Jangan sampai jadwal obatmu terlewat.", "Tetap ikuti jadwal agar tubuhmu tetap terjaga."};
        } else if ("STUDY".equalsIgnoreCase(category)) {
            messages = new String[]{"Fokus sebentar hari ini akan membantumu melangkah lebih jauh.", "Yuk selesaikan hal kecil yang sudah kamu rencanakan."};
        } else {
            messages = new String[]{"Jangan lupa menyelesaikan aktivitasmu.", "Yuk selesaikan hal kecil yang sudah kamu rencanakan."};
        }
        return messages[random.nextInt(messages.length)].replace("{name}", name);
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private void startAlarmVibration() {
        try {
            if (vibrator == null || !vibrator.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createWaveform(new long[]{0, 500, 300, 500}, 0));
            } else {
                vibrator.vibrate(new long[]{0, 500, 300, 500}, 0);
            }
        } catch (Exception ignored) {}
    }

    private void startAlarmSound() {
        try {
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            ringtone = RingtoneManager.getRingtone(this, alarmUri);
            if (ringtone != null) {
                ringtone.setStreamType(AudioManager.STREAM_ALARM);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ringtone.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build());
                }
                ringtone.play();
                soundHandler.postDelayed(replaySound, 1800L);
            }
        } catch (Exception ignored) {}
    }

    @Override protected void onDestroy() {
        stopAlarmSignal();
        super.onDestroy();
    }

    private void stopAlarmSignal() {
        soundHandler.removeCallbacks(replaySound);
        if (ringtone != null) {
            if (ringtone.isPlaying()) ringtone.stop();
            ringtone = null;
        }
        if (vibrator != null) vibrator.cancel();
        AlarmReceiver.stopActiveAlarm(this);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(notificationId);
    }
}
