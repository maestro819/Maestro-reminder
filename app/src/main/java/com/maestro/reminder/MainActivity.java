package com.maestro.reminder;

import android.Manifest;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JsResult;
import android.view.Window;
import android.view.WindowManager;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 100;
    private static final int OVERLAY_PERMISSION_REQUEST = 101;
    private static final int BATTERY_PERMISSION_REQUEST = 102;
    private WebView webView;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#f8f9fc")));
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
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> result.confirm())
                        .setNegativeButton(android.R.string.cancel, (dialog, which) -> result.cancel())
                        .setOnCancelListener(dialog -> result.cancel())
                        .show();
                return true;
            }
        });
        webView.addJavascriptInterface(new ReminderBridge(this), "AndroidBridge"); 
        webView.setBackgroundColor(Color.parseColor("#f8f9fc"));

        ViewCompat.setOnApplyWindowInsetsListener(webView, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(webView);

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

        // 4. Full Screen Intent (wajib diizinkan manual mulai Android 14)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null && !nm.canUseFullScreenIntent()) showFullScreenIntentDialog();
        }

        // 5. Bebas dari optimisasi baterai (supaya alarm tetap jalan walau app ditutup/di-swipe)
        android.os.PowerManager powerManager = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
            showBatteryOptimizationDialog();
        }
    }

    private void showFullScreenIntentDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Izin Notifikasi Layar Penuh")
            .setMessage("Mulai Android 14, izin ini harus diaktifkan manual agar alarm langsung membuka layar penuh, bukan cuma notifikasi biasa.")
            .setPositiveButton("Buka Pengaturan", (d, w) -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    try { startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + getPackageName()))); }
                    catch (Exception ignored) {}
                }
            })
            .setNegativeButton("Nanti", null)
            .show();
    }

    private void showBatteryOptimizationDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Nonaktifkan Optimisasi Baterai")
            .setMessage("Agar alarm tetap berbunyi tepat waktu walau aplikasi ditutup atau HP idle lama, izinkan Maestro berjalan bebas dari optimisasi baterai.")
            .setPositiveButton("Buka Pengaturan", (d, w) -> {
                try { startActivityForResult(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())), BATTERY_PERMISSION_REQUEST); }
                catch (Exception ignored) {
                    try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); } catch (Exception ignored2) {}
                }
            })
            .setNegativeButton("Nanti", null)
            .show();
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
        if (requestCode == OVERLAY_PERMISSION_REQUEST || requestCode == BATTERY_PERMISSION_REQUEST) {
            if (webView != null) {
                webView.reload(); // Refresh UI to update permission status
            }
        }
    }
}
