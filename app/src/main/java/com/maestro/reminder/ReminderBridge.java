package com.maestro.reminder;

import android.content.Context;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.os.Build;
import android.webkit.JavascriptInterface;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class ReminderBridge {
    private final Context context;
    public ReminderBridge(Context context) { this.context = context.getApplicationContext(); }

    @JavascriptInterface public void setUserName(String name) {
        context.getSharedPreferences("maestro_user", Context.MODE_PRIVATE).edit().putString("name", name == null || name.trim().isEmpty() ? "Maestro" : name.trim()).apply();
    }

    @JavascriptInterface public void syncActivities(String raw) {
        try {
            JSONArray array = new JSONArray(raw); List<Reminder> items = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                long id = obj.getLong("id"); String name = obj.optString("name", "Aktivitas"); String time = obj.optString("time", "08:00");
                String icon = obj.optString("icon", "⏰"); String kind = obj.optString("kind", Reminder.ACTIVITY); String category = obj.optString("category", "OTHER"); String message = obj.optString("message", ""); String note = obj.optString("note", ""); String days = daysValue(obj); boolean enabled = !obj.optBoolean("done", false);
                Reminder r = new Reminder(id, name, note, icon, kind, category, message, nextDaily(time), repeatFor(kind, days), enabled);
                items.add(r); AlarmScheduler.cancel(context, id); if (enabled) AlarmScheduler.schedule(context, r);
            }
            ReminderStore.save(context, items);
        } catch (Exception ignored) {}
    }

    @JavascriptInterface public void scheduleActivity(long id, String name, String time, String icon, String kind, String days, String note, String category) {
        Reminder r = new Reminder(id, name, note == null ? "" : note, icon, kind, category, note == null ? "" : note, nextDaily(time), repeatFor(kind, days), true);
        List<Reminder> items = ReminderStore.load(context); boolean found = false;
        for (int i = 0; i < items.size(); i++) if (items.get(i).id == id) { items.set(i, r); found = true; }
        if (!found) items.add(r); ReminderStore.save(context, items); AlarmScheduler.cancel(context, id); AlarmScheduler.schedule(context, r);
    }

    @JavascriptInterface public void setActivityEnabled(long id, boolean enabled) {
        Reminder r = ReminderStore.find(context, id); if (r == null) return; r.enabled = enabled;
        List<Reminder> items = ReminderStore.load(context); for (int i = items.size() - 1; i >= 0; i--) if (items.get(i).id == id) items.remove(i); items.add(r); ReminderStore.save(context, items);
        AlarmScheduler.cancel(context, id); if (enabled) AlarmScheduler.schedule(context, r);
    }

    @JavascriptInterface public void deleteActivity(long id) { AlarmScheduler.cancel(context, id); ReminderStore.delete(context, id); }
    @JavascriptInterface public void stopAlarm() { AlarmReceiver.stopActiveAlarm(context); }

    @JavascriptInterface public String getPermissionStatus() {
        boolean notification = true;
        boolean exactAlarm = true;
        boolean fullScreen = true;
        boolean overlay = true;
        
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && notificationManager != null) notification = notificationManager.areNotificationsEnabled();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notification = notification && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { 
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE); 
            exactAlarm = alarmManager != null && alarmManager.canScheduleExactAlarms(); 
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && notificationManager != null) {
            fullScreen = notificationManager.canUseFullScreenIntent();
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            overlay = Settings.canDrawOverlays(context);
        }
        
        boolean battery = true;
        android.os.PowerManager powerManager = (android.os.PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) battery = powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
        
        return "{\"notification\":" + notification + ",\"exactAlarm\":" + exactAlarm + ",\"fullScreen\":" + fullScreen + ",\"overlay\":" + overlay + ",\"battery\":" + battery + "}";
    }

    @JavascriptInterface public void openBatteryOptimizationSettings() {
        try {
            context.startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
            try { context.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            catch (Exception ignored2) { openAppDetails(); }
        }
    }

    @JavascriptInterface public void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @JavascriptInterface public void openAlarmSettings() {
        try { context.startActivity(new Intent(AlarmScheduler.exactAlarmSettings(context)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception ignored) { openAppDetails(); }
    }

    @JavascriptInterface public void openFullScreenSettings() {
        try { context.startActivity(new Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT").setData(Uri.parse("package:" + context.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception ignored) { openAppDetails(); }
    }
    
    @JavascriptInterface public void openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    @JavascriptInterface public void openAppDetails() {
        context.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    @JavascriptInterface public void snoozeAlarm(int minutes) {
        List<Reminder> items = ReminderStore.load(context);
        for (Reminder r : items) {
            if (r.enabled && r.triggerAt <= System.currentTimeMillis() + 15000L) {
                r.triggerAt = System.currentTimeMillis() + Math.max(1, minutes) * 60000L;
                ReminderStore.save(context, items); AlarmScheduler.cancel(context, r.id); AlarmScheduler.schedule(context, r); return;
            }
        }
    }

    private String daysValue(JSONObject obj) {
        try { JSONArray arr = obj.optJSONArray("days"); if (arr == null) return obj.optString("days", ""); StringBuilder out = new StringBuilder(); for (int i=0;i<arr.length();i++) { if (i>0) out.append(','); out.append(arr.getInt(i)); } return out.toString(); } catch (Exception ignored) { return ""; }
    }

    private String repeatFor(String kind, String days) {
        if (!Reminder.SLEEP.equals(kind)) return Reminder.DAILY;
        return days == null || days.trim().isEmpty() ? Reminder.ONCE : "WEEKLY:" + days;
    }

    private long nextDaily(String value) {
        String[] parts = value.split(":"); Calendar c = Calendar.getInstance();
        try { c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0])); c.set(Calendar.MINUTE, Integer.parseInt(parts[1])); } catch (Exception ignored) {}
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0); if (c.getTimeInMillis() <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1); return c.getTimeInMillis();
    }
}
