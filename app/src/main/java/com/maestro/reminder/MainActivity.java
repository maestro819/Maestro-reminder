package com.maestro.reminder;

import android.Manifest;
import android.app.AlarmManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_POST_NOTIFICATIONS = 501;

    private TextView txtUsername, txtGreeting, txtProgressInfo;
    private EditText editInputName;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sharedPreferences = getSharedPreferences("MaestroPrefs", MODE_PRIVATE);

        // Hubungkan komponen UI
        txtUsername = findViewById(R.id.displayUsername);
        txtGreeting = findViewById(R.id.greetingText);
        txtProgressInfo = findViewById(R.id.progressInfo);
        editInputName = findViewById(R.id.inputNameSetting);

        // Muat data profil tersimpan
        loadUserData();

        // Minta izin yang dibutuhkan supaya alarm & notifikasi benar-benar tampil
        requestRuntimePermissionsIfNeeded();

        // Contoh tombol untuk mendaftarkan alarm tes (misal: 1 menit dari sekarang)
        Button btnTestAlarm = findViewById(R.id.btnTestAlarm);
        if (btnTestAlarm != null) {
            btnTestAlarm.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setLocalAlarm("Olahraga", 0, 1); // 1 menit lagi untuk uji coba
                    Toast.makeText(MainActivity.this, "Alarm berhasil diset 1 menit ke depan!", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void loadUserData() {
        String savedName = sharedPreferences.getString("USER_NAME", "Anto");
        if (txtUsername != null) {
            txtUsername.setText(savedName);
        }
        if (editInputName != null) {
            editInputName.setText(savedName);
        }
        if (txtGreeting != null) {
            txtGreeting.setText("Selamat siang \uD83D\uDC4B");
        }
    }

    // Fungsi untuk menyimpan nama panggilan baru
    public void saveUserName(String newName) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("USER_NAME", newName);
        editor.apply();
        loadUserData();
    }

    /**
     * Android 13+ (API 33) mewajibkan izin runtime POST_NOTIFICATIONS,
     * kalau tidak diminta & diizinkan, notifikasi alarm TIDAK AKAN tampil
     * sama sekali walau kode di AlarmReceiver sudah benar.
     *
     * Android 12+ (API 31) juga mewajibkan izin "Alarms & reminders" (exact
     * alarm) diaktifkan manual oleh user lewat Settings kalau app belum
     * termasuk kategori tertentu.
     */
    private void requestRuntimePermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQ_POST_NOTIFICATIONS
                );
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(this, "Aktifkan izin \"Alarm & pengingat\" agar alarm tepat waktu", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_POST_NOTIFICATIONS) {
            if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Tanpa izin notifikasi, alarm tidak akan muncul", Toast.LENGTH_LONG).show();
            }
        }
    }

    // Fungsi inti Alarm Manager Android (Bekerja secara Offline-First)
    public void setLocalAlarm(String activityName, int hour, int minute) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

        Intent intent = new Intent(this, AlarmReceiver.class);
        String userName = sharedPreferences.getString("USER_NAME", "Anto");
        intent.putExtra("ACTIVITY_NAME", activityName);
        intent.putExtra("USER_NAME", userName);

        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getBroadcast(
                this,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
        );

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());

        if (hour > 0 || minute > 0) {
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, minute);
            calendar.set(Calendar.SECOND, 0);
        } else {
            calendar.add(Calendar.MINUTE, 1);
        }

        if (alarmManager == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
            } else {
                Toast.makeText(this, "Izin alarm belum aktif, buka Pengaturan untuk mengaktifkan", Toast.LENGTH_LONG).show();
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    pendingIntent
            );
        }
    }
}
