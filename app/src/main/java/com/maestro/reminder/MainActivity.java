package com.maestro.reminder;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 100;
    private EditText inputName;
    private TextView progressInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        inputName = findViewById(R.id.inputNameSetting);
        progressInfo = findViewById(R.id.progressInfo);
        TextView greeting = findViewById(R.id.greetingText);
        TextView displayName = findViewById(R.id.displayUsername);
        Button testAlarm = findViewById(R.id.btnTestAlarm);

        greeting.setText("Maestro Reminder");
        String savedName = getSharedPreferences(AlarmScheduler.PREFS, MODE_PRIVATE)
                .getString(AlarmScheduler.KEY_USER_NAME, "Maestro");
        inputName.setText(savedName);
        displayName.setText("Pengingat tetap aktif meskipun aplikasi ditutup");
        updateStatus();

        requestNotificationPermissionIfNeeded();
        testAlarm.setOnClickListener(v -> scheduleTestAlarm());
    }

    private void scheduleTestAlarm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && !AlarmScheduler.canScheduleExactAlarms(this)) {
            try {
                startActivity(AlarmScheduler.exactAlarmSettings(this));
                Toast.makeText(this, "Aktifkan izin Alarm & pengingat, lalu tekan tombol lagi.", Toast.LENGTH_LONG).show();
            } catch (Exception ignored) {
                Toast.makeText(this, "Izin alarm tepat tidak tersedia; alarm akan memakai waktu perkiraan.", Toast.LENGTH_LONG).show();
            }
        }

        String name = inputName.getText().toString().trim();
        if (name.isEmpty()) name = "Maestro";
        long triggerAt = System.currentTimeMillis() + 60_000L;
        AlarmScheduler.saveAndSchedule(this, triggerAt, "Tes pengingat", name);
        updateStatus();
        Toast.makeText(this, "Alarm dijadwalkan 1 menit lagi.", Toast.LENGTH_SHORT).show();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST);
        }
    }

    private void updateStatus() {
        long triggerAt = getSharedPreferences(AlarmScheduler.PREFS, MODE_PRIVATE)
                .getLong(AlarmScheduler.KEY_TRIGGER_AT, 0L);
        if (triggerAt > System.currentTimeMillis()) {
            progressInfo.setText("Alarm aktif: " + android.text.format.DateFormat.getTimeFormat(this).format(triggerAt));
        } else {
            progressInfo.setText("Belum ada alarm aktif");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        AlarmScheduler.reschedule(this);
        if (progressInfo != null) updateStatus();
    }
}
