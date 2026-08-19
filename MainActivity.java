package com.maestro.reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    private TextView txtUsername, txtGreeting, txtProgressInfo;
    private EditText editInputName;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Menggunakan layout utama Android

        sharedPreferences = getSharedPreferences("MaestroPrefs", MODE_PRIVATE);

        // Hubungkan komponen UI
        txtUsername = findViewById(R.id.displayUsername);
        txtGreeting = findViewById(R.id.greetingText);
        txtProgressInfo = findViewById(R.id.progressInfo);
        editInputName = findViewById(R.id.inputNameSetting);

        // Muat data profil tersimpan
        loadUserData();

        // Contoh tombol untuk mendaftarkan alarm tes (misal: 1 menit dari sekarang)
        Button btnTestAlarm = findViewById(R.id.btnTestAlarm);
        if (btnTestAlarm != null) {
            btnTestAlarm.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setLocalAlarm("Olahraga", 0, 1); // 1 menit lagi untuk uji coba
                    Toast.makeText(MainActivity.java.this, "Alarm berhasil diset 1 menit ke depan!", Toast.LENGTH_SHORT).show();
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
            txtGreeting.setText("Selamat siang 👋");
        }
    }

    // Fungsi untuk menyimpan nama panggilan baru
    public void saveUserName(String newName) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("USER_NAME", newName);
        editor.apply();
        loadUserData();
    }

    // Fungsi inti Alarm Manager Android (Bekerja secara Offline-First)
    public void setLocalAlarm(String activityName, int hour, int minute) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, AlarmReceiver.class);
        
        String userName = sharedPreferences.getString("USER_NAME", "Anto");
        intent.putExtra("ACTIVITY_NAME", activityName);
        intent.putExtra("USER_NAME", userName);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, 
                0, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        
        // Jika parameter jam/menit digunakan, atau tambah durasi menit untuk testing
        if (hour > 0 || minute > 0) {
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, minute);
            calendar.set(Calendar.SECOND, 0);
        } else {
            // Default tambah 1 menit dari waktu sekarang untuk uji coba
            calendar.add(Calendar.MINUTE, 1);
        }

        if (alarmManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            calendar.getTimeInMillis(),
                            pendingIntent
                    );
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
}
