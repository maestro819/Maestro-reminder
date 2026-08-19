package com.maestro.reminder;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.media.AudioAttributes;
import android.media.RingtoneManager;

import androidx.core.app.NotificationCompat;

import java.util.Calendar;
import java.util.List;

public class AlarmReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "maestro_alarm_channel";
    public static final int BASE_NOTIFICATION_ID = 1001;
    private static Vibrator activeVibrator;
    private static NotificationManager activeNotificationManager;
    private static int activeNotificationId = -1;
    private static long activeReminderId = -1L;

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_TIME_CHANGED.equals(action) || Intent.ACTION_TIMEZONE_CHANGED.equals(action) || android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action)) {
            AlarmScheduler.rescheduleAll(context); return;
        }
        if (!AlarmScheduler.ACTION_FIRE.equals(action)) return;
        long id = intent.getLongExtra("REMINDER_ID", -1L);
        Reminder reminder = ReminderStore.find(context, id);
        if (reminder == null || !reminder.enabled) return;

        int notificationId = notificationId(id);
        activeReminderId = id;
        createChannel(context);
        String ownerName = context.getSharedPreferences("maestro_user", Context.MODE_PRIVATE).getString("name", "Maestro");
        Intent screen = new Intent(context, AlarmScreenActivity.class)
                .putExtra("ACTIVITY_NAME", reminder.title)
                .putExtra("ACTIVITY_ICON", reminder.icon)
                .putExtra("KIND", reminder.kind)
                .putExtra("USER_NAME", ownerName)
                .putExtra("NOTE", reminder.note)
                .putExtra("NOTIFICATION_ID", notificationId)
                .putExtra("REMINDER_ID", id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent screenPending = PendingIntent.getActivity(context, notificationId, screen, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(com.maestro.reminder.R.drawable.ic_alarm)
                .setContentTitle(reminder.icon + "  " + reminder.title)
                .setContentText(Reminder.SLEEP.equals(reminder.kind) ? ownerName + ", waktunya bangun." : ownerName + ", waktunya " + reminder.title.toLowerCase() + ".")
                .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setFullScreenIntent(screenPending, true)
                .setContentIntent(screenPending).setAutoCancel(true).setOngoing(true);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(notificationId, builder.build());
        activeNotificationManager = manager;
        activeNotificationId = notificationId;
        activeVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (activeVibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) activeVibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 900, 500, 900}, 0));
            else activeVibrator.vibrate(new long[]{0, 900, 500, 900}, 0);
        }

        if (Reminder.ONCE.equals(reminder.repeat)) {
            reminder.enabled = false;
        } else {
            reminder.triggerAt = nextTrigger(reminder);
            AlarmScheduler.schedule(context, reminder);
        }
        List<Reminder> items = ReminderStore.load(context);
        for (int i = 0; i < items.size(); i++) if (items.get(i).id == reminder.id) items.set(i, reminder);
        ReminderStore.save(context, items);
    }

    private Reminder copy(Reminder r) { return r; }

    private long nextTrigger(Reminder reminder) {
        Calendar next = Calendar.getInstance();
        Calendar anchor = Calendar.getInstance(); anchor.setTimeInMillis(reminder.anchorAt > 0 ? reminder.anchorAt : reminder.triggerAt);
        next.set(Calendar.HOUR_OF_DAY, anchor.get(Calendar.HOUR_OF_DAY)); next.set(Calendar.MINUTE, anchor.get(Calendar.MINUTE)); next.set(Calendar.SECOND, 0); next.set(Calendar.MILLISECOND, 0);
        next.add(Calendar.DAY_OF_YEAR, 1);
        if (reminder.repeat != null && reminder.repeat.startsWith("WEEKLY:")) {
            String days = reminder.repeat.substring("WEEKLY:".length());
            while (next.getTimeInMillis() <= System.currentTimeMillis() || !days.contains(String.valueOf(next.get(Calendar.DAY_OF_WEEK)))) next.add(Calendar.DAY_OF_YEAR, 1);
            return next.getTimeInMillis();
        }
        if (Reminder.DAILY.equals(reminder.repeat)) return next.getTimeInMillis();
        if (Reminder.WEEKLY.equals(reminder.repeat)) { next.add(Calendar.DAY_OF_YEAR, 6); return next.getTimeInMillis(); }
        while (next.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || next.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) next.add(Calendar.DAY_OF_YEAR, 1);
        return next.getTimeInMillis();
    }

    public static void stopActiveAlarm(Context context) {
        if (activeVibrator != null) { activeVibrator.cancel(); activeVibrator = null; }
        NotificationManager manager = activeNotificationManager;
        if (manager == null) manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null && activeNotificationId >= 0) manager.cancel(activeNotificationId);
        activeNotificationManager = null;
        activeNotificationId = -1;
        activeReminderId = -1L;
    }

    public static void snoozeActiveAlarm(Context context, int minutes) {
        long id = activeReminderId;
        if (id < 0) return;
        Reminder reminder = ReminderStore.find(context, id);
        if (reminder == null) return;
        reminder.triggerAt = System.currentTimeMillis() + Math.max(1, minutes) * 60000L;
        java.util.List<Reminder> items = ReminderStore.load(context);
        for (int i = 0; i < items.size(); i++) if (items.get(i).id == id) items.set(i, reminder);
        ReminderStore.save(context, items);
        AlarmScheduler.cancel(context, id);
        AlarmScheduler.schedule(context, reminder);
        stopActiveAlarm(context);
    }

    private int notificationId(long id) { return BASE_NOTIFICATION_ID + (int) (id % 100000); }

    private void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Maestro Reminder Alarm", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Notifikasi reminder Maestro Reminder");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 900, 500, 900});
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), audioAttributes);
            NotificationManager manager = context.getSystemService(NotificationManager.class); if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}
