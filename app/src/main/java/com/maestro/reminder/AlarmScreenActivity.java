package com.maestro.reminder;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class AlarmScreenActivity extends AppCompatActivity {

    private static final int NOTIFICATION_ID = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Ini pengganti android:showOnLockScreen / android:turnScreenOn yang
        // dulu ditulis (salah) di AndroidManifest.xml. Sejak API 27, dua
        // perilaku ini WAJIB diatur lewat kode, bukan atribut manifest.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            );
        }

        setContentView(R.layout.activity_alarm_screen);

        // Notifikasi full-screen sudah menjalankan tugasnya (membuka activity
        // ini), jadi boleh langsung dibersihkan dari status bar.
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }

        String activityName = getIntent().getStringExtra("ACTIVITY_NAME");
        String userName = getIntent().getStringExtra("USER_NAME");
        if (activityName == null) activityName = "Aktivitas";
        if (userName == null) userName = "Maestro";

        TextView txtAlarmActivity = findViewById(R.id.txtAlarmActivity);
        TextView txtAlarmUser = findViewById(R.id.txtAlarmUser);
        if (txtAlarmActivity != null) {
            txtAlarmActivity.setText(activityName);
        }
        if (txtAlarmUser != null) {
            txtAlarmUser.setText("Untuk: " + userName);
        }

        Button btnDismiss = findViewById(R.id.btnDismissAlarm);
        if (btnDismiss != null) {
            btnDismiss.setOnClickListener(v -> finish());
        }
    }
}
