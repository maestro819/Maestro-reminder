package com.maestro.reminder;

import android.Manifest;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 100;
    private WebView webView;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_main);
        webView = findViewById(R.id.mainWebView); WebSettings settings = webView.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true); settings.setAllowFileAccess(true); settings.setAllowContentAccess(true);
        webView.setWebViewClient(new WebViewClient()); webView.addJavascriptInterface(new ReminderBridge(this), "AndroidBridge"); webView.loadUrl("file:///android_asset/index.html");
        webView.postDelayed(this::requestPermissionsAndExplain, 700L);
    }

    private void requestPermissionsAndExplain() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager manager = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (manager != null && !manager.canScheduleExactAlarms()) showAlarmAccessDialog();
        }
    }

    private void showAlarmAccessDialog() {
        new AlertDialog.Builder(this).setTitle("Izin agar alarm berjalan")
                .setMessage("Maestro Reminder membutuhkan izin Alarm tepat waktu. Pada beberapa HP izin ini tidak tampil sebagai popup dan harus diaktifkan manual.")
                .setPositiveButton("Buka Pengaturan Alarm", (d, w) -> { try { startActivity(AlarmScheduler.exactAlarmSettings(this)); } catch (Exception ignored) { openAppDetails(); } })
                .setNegativeButton("Nanti", null).show();
    }

    private void openAppDetails() { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST && (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED)) {
            new AlertDialog.Builder(this).setTitle("Notifikasi belum diizinkan").setMessage("Aktifkan notifikasi agar alarm Maestro Reminder dapat tampil.").setPositiveButton("Buka Pengaturan", (d, w) -> startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()))).setNegativeButton("Nanti", null).show();
        }
    }

    @Override protected void onResume() { super.onResume(); AlarmScheduler.rescheduleAll(this); if (webView != null) webView.postDelayed(() -> webView.evaluateJavascript("typeof refreshPermissionPanel === 'function' && refreshPermissionPanel();", null), 250L); }
    @Override public void onBackPressed() { if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed(); }
}
