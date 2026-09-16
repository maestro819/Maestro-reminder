package com.maestro.reminder;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;

import androidx.core.app.NotificationCompat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AlarmReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "maestro_alarm_channel";
    public static final int BASE_NOTIFICATION_ID = 1001;
    private static Vibrator activeVibrator;
    private static NotificationManager activeNotificationManager;
    private static int activeNotificationId = -1;
    private static long activeReminderId = -1L;
    // Pemutar suara alarm agar notifikasi tetap BERBUNYI terus (bukan sekali
    // saja) selama layar alarm belum terbuka, termasuk saat layar sedang nyala.
    private static MediaPlayer receiverPlayer;
    private static final Handler receiverHandler = new Handler(Looper.getMainLooper());
    private static Runnable ringWatchdog;
    private static long ringStartElapsed;
    private static final long RING_TIMEOUT_MS = 5L * 60L * 1000L;
    private static final long[] VIBRATION_PATTERN = {0, 1000, 500, 1000};

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_TIME_CHANGED.equals(action) || Intent.ACTION_TIMEZONE_CHANGED.equals(action) || "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED".equals(action)) {
            // Bersihkan skipDate yang kadaluwarsa (hari sudah berganti) supaya
            // alarm yang tadi dimatikan "hari ini saja" otomatis aktif lagi.
            com.maestro.reminder.ReminderBridge.clearExpiredSkip(context);
            AlarmScheduler.rescheduleAll(context); return;
        }
        if (!AlarmScheduler.ACTION_FIRE.equals(action)) return;
        long id = intent.getLongExtra("REMINDER_ID", -1L);
        Reminder reminder = ReminderStore.find(context, id);
        if (reminder == null || !reminder.enabled) {
            AlarmScheduler.cancel(context, id);
            return;
        }

        // VALIDASI HARI (PENTING: Mencegah alarm bunyi di hari yang tidak dijadwalkan)
        Calendar now = Calendar.getInstance();
        if (reminder.repeat != null && reminder.repeat.startsWith("WEEKLY:")) {
            String days = reminder.repeat.substring("WEEKLY:".length());
            int currentDay = now.get(Calendar.DAY_OF_WEEK);
            if (!days.contains(String.valueOf(currentDay))) {
                // Bukan harinya, jadwalkan ulang untuk besok dan abaikan
                reminder.triggerAt = nextTrigger(reminder);
                AlarmScheduler.schedule(context, reminder);
                updateStore(context, reminder);
                return;
            }
        }

        // Kunci CPU sebentar agar proses pembukaan layar alarm dan penyiapan
        // suara tidak tertunda oleh deep sleep (salah satu penyebab alarm telat).
        try {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MaestroReminder:AlarmFire");
                wakeLock.acquire(60000L);
            }
        } catch (Exception ignored) {}

        int notificationId = notificationId(id);
        activeReminderId = id;
        createChannel(context);
        String ownerName = context.getSharedPreferences("maestro_user", Context.MODE_PRIVATE).getString("name", "Maestro");
        
        String formattedTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(reminder.triggerAt));
        
        Intent screen = new Intent(context, AlarmScreenActivity.class)
                .putExtra("ACTIVITY_NAME", reminder.title)
                .putExtra("ACTIVITY_ICON", reminder.icon)
                .putExtra("KIND", reminder.kind)
                .putExtra("USER_NAME", ownerName)
                .putExtra("CATEGORY", reminder.category)
                .putExtra("ALARM_MESSAGE", alarmMessage(reminder, ownerName))
                .putExtra("ALARM_TIME", formattedTime)
                .putExtra("NOTIFICATION_ID", notificationId)
                .putExtra("REMINDER_ID", id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK 
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP 
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        
        PendingIntent screenPending = PendingIntent.getActivity(context, notificationId, screen, flags);
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(com.maestro.reminder.R.drawable.ic_alarm)
                .setContentTitle(reminder.icon + "  " + reminder.title)
                .setContentText(alarmMessage(reminder, ownerName))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(screenPending, true)
                .setContentIntent(screenPending)
                .setAutoCancel(false)
                .setOngoing(true);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(notificationId, builder.build());
            activeNotificationManager = manager;
            activeNotificationId = notificationId;
        }

        // Buka layar alarm sesegera mungkin
        try {
            context.startActivity(screen);
        } catch (Exception ignored) {}

        // Suara alarm berbunyi TERUS-MENERUS dari sisi notifikasi, sehingga
        // tetap terdengar jelas walau layar nyala dan layar alarm belum terbuka.
        startReceiverSound(context);

        activeVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (activeVibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activeVibrator.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, 0));
            } else {
                activeVibrator.vibrate(VIBRATION_PATTERN, 0);
            }
        }

        // Jaga agar suara & getar tetap lanjut sampai pengguna menindaklanjuti
        startRingingLoop(context);

        // Reschedule
        if (Reminder.ONCE.equals(reminder.repeat)) {
            reminder.enabled = false;
        } else {
            reminder.triggerAt = nextTrigger(reminder);
            AlarmScheduler.schedule(context, reminder);
        }
        updateStore(context, reminder);
    }

    private void startReceiverSound(Context context) {
        try {
            stopReceiverSound();
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            if (alarmUri == null) return;
            MediaPlayer player = new MediaPlayer();
            player.setDataSource(context, alarmUri);
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            player.setAudioAttributes(attrs);
            player.setLooping(true);
            player.setOnErrorListener((mp, what, extra) -> { stopReceiverSound(); return true; });
            player.prepare();
            player.start();
            receiverPlayer = player;
        } catch (Exception e) {
            stopReceiverSound();
        }
    }

    public static void stopReceiverSound() {
        MediaPlayer player = receiverPlayer;
        receiverPlayer = null;
        stopRingingLoop();
        if (player != null) {
            try { if (player.isPlaying()) player.stop(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
        }
    }

    /**
     * Dipanggil layar alarm saat suaranya sendiri sudah mulai berbunyi:
     * menghentikan HANYA suara loop dari notifikasi (tanpa mematikan getaran,
     * notifikasi, atau pengingat agar tetap berbunyi) supaya tidak dobel.
     */
    public static void stopReceiverPlayerOnly() {
        MediaPlayer player = receiverPlayer;
        receiverPlayer = null;
        if (player != null) {
            try { if (player.isPlaying()) player.stop(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
        }
    }

    private void startRingingLoop(final Context context) {
        stopRingingLoop();
        ringStartElapsed = SystemClock.elapsedRealtime();
        ringWatchdog = new Runnable() {
            @Override public void run() {
                boolean stillRinging = receiverPlayer != null || activeVibrator != null;
                if (!stillRinging) return;
                if (SystemClock.elapsedRealtime() - ringStartElapsed >= RING_TIMEOUT_MS) {
                    stopActiveAlarm(context);
                    return;
                }
                // Pola getar bisa diputus oleh getar aplikasi lain; nyalakan
                // kembali agar getaran tidak berhenti setelah beberapa detik.
                if (activeVibrator != null) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            activeVibrator.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, 0));
                        } else {
                            activeVibrator.vibrate(VIBRATION_PATTERN, 0);
                        }
                    } catch (Exception ignored) {}
                }
                if (receiverPlayer != null) {
                    try { if (!receiverPlayer.isPlaying()) receiverPlayer.start(); } catch (Exception ignored) {}
                }
                receiverHandler.postDelayed(this, 4000L);
            }
        };
        receiverHandler.postDelayed(ringWatchdog, 4000L);
    }

    private static void stopRingingLoop() {
        if (ringWatchdog != null) {
            receiverHandler.removeCallbacks(ringWatchdog);
            ringWatchdog = null;
        }
    }

    private void updateStore(Context context, Reminder reminder) {
        List<Reminder> items = ReminderStore.load(context);
        for (int i = 0; i < items.size(); i++) if (items.get(i).id == reminder.id) items.set(i, reminder);
        ReminderStore.save(context, items);
    }

    private String alarmMessage(Reminder reminder, String ownerName) {
        String period = period(reminder.triggerAt);
        if (Reminder.SLEEP.equals(reminder.kind)) return ownerName + ", bangun yuk. Selamat " + period + ".";
        if (reminder.message != null && !reminder.message.trim().isEmpty()) return reminder.message.replace("{name}", ownerName);
        String[] messages;
        if ("HEALTH".equals(reminder.category)) messages = new String[]{"{name}, waktunya menjaga kesehatan.", "Yuk lakukan kebiasaan sehatmu sekarang."};
        else if ("SPORT".equals(reminder.category)) messages = new String[]{"{name}, waktunya bergerak.", "Ayo olahraga sebentar agar tubuh tetap aktif."};
        else if ("MEDICINE".equals(reminder.category)) messages = new String[]{"{name}, jangan lupa obatmu.", "Waktunya minum obat sesuai jadwal."};
        else if ("WORK".equals(reminder.category)) messages = new String[]{"{name}, waktunya fokus pada pekerjaan.", "Satu tugas dulu, pelan-pelan yang penting selesai."};
        else if ("STUDY".equals(reminder.category)) messages = new String[]{"{name}, waktunya belajar.", "Saatnya fokus pada pelajaranmu."};
        else if ("WORSHIP".equals(reminder.category)) messages = new String[]{"{name}, waktunya ibadah.", "Luangkan waktu untuk menenangkan hati."};
        else if ("MENGAJI".equals(reminder.category)) messages = new String[]{"{name}, waktunya mengaji.", "Luangkan waktu untuk membaca dan memahami Al-Qur'an."};
        else messages = new String[]{"{name}, ada pengingat untukmu.", "Jangan sampai jadwalmu terlewat."};
        return messages[(int)(Math.abs(reminder.id) % messages.length)].replace("{name}", ownerName);
    }

    private String period(long time) {
        Calendar c=Calendar.getInstance(); c.setTimeInMillis(time); int h=c.get(Calendar.HOUR_OF_DAY);
        if(h>=5&&h<11) return "pagi"; if(h>=11&&h<15) return "siang"; if(h>=15&&h<18) return "sore"; return "malam";
    }

    private long nextTrigger(Reminder reminder) {
        Calendar next = Calendar.getInstance();
        Calendar anchor = Calendar.getInstance(); anchor.setTimeInMillis(reminder.anchorAt > 0 ? reminder.anchorAt : reminder.triggerAt);
        next.set(Calendar.HOUR_OF_DAY, anchor.get(Calendar.HOUR_OF_DAY)); next.set(Calendar.MINUTE, anchor.get(Calendar.MINUTE)); next.set(Calendar.SECOND, 0); next.set(Calendar.MILLISECOND, 0);
        
        // Selalu mulai cek dari besok jika hari ini sudah lewat
        if (next.getTimeInMillis() <= System.currentTimeMillis()) {
            next.add(Calendar.DAY_OF_YEAR, 1);
        }

        if (reminder.repeat != null && reminder.repeat.startsWith("WEEKLY:")) {
            String days = reminder.repeat.substring("WEEKLY:".length());
            // Cari hari berikutnya yang sesuai filter
            while (!days.contains(String.valueOf(next.get(Calendar.DAY_OF_WEEK)))) {
                next.add(Calendar.DAY_OF_YEAR, 1);
            }
            return next.getTimeInMillis();
        }
        
        if (Reminder.DAILY.equals(reminder.repeat)) return next.getTimeInMillis();
        if (Reminder.WEEKLY.equals(reminder.repeat)) { 
            // Jika mingguan (tanpa filter hari spesifik), tambah 7 hari
            next.add(Calendar.DAY_OF_YEAR, 6); 
            return next.getTimeInMillis(); 
        }
        
        // WORKDAYS
        while (next.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || next.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            next.add(Calendar.DAY_OF_YEAR, 1);
        }
        return next.getTimeInMillis();
    }

    public static void stopActiveAlarm(Context context) {
        stopReceiverSound();
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

    private int notificationId(long id) { return AlarmScheduler.notificationId(id); }

    private void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Maestro Reminder Alarm", NotificationManager.IMPORTANCE_HIGH);
            channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            channel.enableVibration(true);
            // Suara alarm dibunyikan sendiri oleh MediaPlayer di receiver
            // (agar tetap terdengar saat layar nyala). TIDAK memasang suara
            // di channel supaya tidak terdengar double/echo dari sistem.
            NotificationManager manager = context.getSystemService(NotificationManager.class); if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    /**
     * Dipakai UI di JavaScript untuk menyusun label hari berulang dari
     * string days (mis. "2") atau "1,2,3" menjadi label Bahasa Indonesia
     * seperti "Setiap Senin saja" atau "Setiap Senin, Rabu saja".
     */
    @JavascriptInterface
    public static String repeatLabelFromBridge(String days) {
        if (days == null || days.trim().isEmpty()) return "🔁 Setiap hari";
        String[] parts = days.split(",");
        java.util.LinkedHashSet<Integer> nums = new java.util.LinkedHashSet<>();
        for (String p : parts) {
            try {
                int n = Integer.parseInt(p.trim());
                if (n >= 1 && n <= 7) nums.add(n);
            } catch (Exception ignored) {}
        }
        if (nums.isEmpty()) return "🔁 Setiap hari";
        // Java Calendar: SUNDAY=1, MONDAY=2 ... SATURDAY=7
        String[] names = {"Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu"};
        if (nums.size() == 7) return "🔁 Setiap hari";
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (int n : nums) out.add(names[n - 1]);
        if (out.size() == 1) return "🗓️ Hanya " + out.get(0) + " saja";
        StringBuilder sb = new StringBuilder("🗓️ Setiap ");
        for (int i = 0; i < out.size(); i++) {
            if (i > 0) sb.append(i == out.size() - 1 ? " dan " : ", ");
            sb.append(out.get(i));
        }
        sb.append(" saja");
        return sb.toString();
    }
}
