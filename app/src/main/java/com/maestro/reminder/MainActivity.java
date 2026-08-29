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
import android.view.Window;
import android.view.WindowManager;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 100;
    private static final int OVERLAY_PERMISSION_REQUEST = 101;
    private WebView webView;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
        window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(false);
            controller.setAppearanceLightNavigationBars(false);
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.systemBars());
        }
        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.mainWebView); 
        WebSettings settings = webView.getSettings(); 
        settings.setJavaScriptEnabled(true); 
        settings.setDomStorageEnabled(true); 
        settings.setAllowFileAccess(true); 
        settings.setAllowContentAccess(true);
        
        webView.setWebViewClient(new WebViewClient()); 
        webView.addJavascriptInterface(new ReminderBridge(this), "AndroidBridge"); 
        webView.loadUrl("file:///android_asset/index.html");
        
        webView.postDelayed(this::requestPermissionsAndExplain, 1000L);
    }

    private void requestPermissionsAndExplain() {
        // 1. Notifikasi
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
        }
        
        // 2. Exact Alarm
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager manager = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (manager != null && !manager.canScheduleExactAlarms()) showAlarmAccessDialog();
        }
        
        // 3. Overlay Permission (Crucial for Android 10+ Full Screen Alarm)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                showOverlayAccessDialog();
            }
        }
    }

    private void showAlarmAccessDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Izin Alarm Tepat Waktu")
            .setMessage("Maestro membutuhkan izin ini agar alarm berbunyi tepat pada waktunya.")
            .setPositiveButton("Buka Pengaturan", (d, w) -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
                }
            })
            .setNegativeButton("Nanti", null)
            .show();
    }
    
    private void showOverlayAccessDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Izin Layar Penuh")
            .setMessage("Agar layar alarm Maestro bisa muncul saat Anda menggunakan HP, silakan aktifkan izin 'Tampil di atas aplikasi lain'.")
            .setPositiveButton("Buka Pengaturan", (d, w) -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST);
                }
            })
            .setNegativeButton("Nanti", null)
            .show();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.evaluateJavascript("if(typeof refreshPermissionPanel==='function'){refreshPermissionPanel();}", null);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            if (webView != null) {
                webView.reload(); // Refresh UI to update permission status
            }
        }
    }
}
