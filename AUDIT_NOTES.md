# Temuan audit Maestro Reminder

Tanggal audit: 19 Agustus 2026.

## Kondisi repositori

- `app/src/main/java/com/maestro/reminder/MainActivity.java` berisi XML layout, bukan kode Java. Ini menyebabkan proyek tidak dapat dikompilasi sebagai Activity.
- `app/src/main/res/layout/activity_main.xml` berisi layout yang sama.
- `AlarmReceiver` hanya menangani alarm yang sudah dipicu; tidak ada kode penjadwalan `AlarmManager` di Activity.
- Receiver dengan intent-filter `BOOT_COMPLETED` menggunakan `AlarmReceiver`, tetapi tidak memiliki logika membaca alarm tersimpan dan menjadwalkannya kembali. Setelah reboot, ia akan menerima intent tanpa data aktivitas dan hanya memunculkan alarm default.
- Tidak ada penyimpanan jadwal lokal (`SharedPreferences`/database), sehingga mode offline dan pemulihan reboot belum memiliki sumber data.
- Manifest sudah mendeklarasikan izin yang relevan, tetapi exact-alarm permission harus diperiksa sebelum `setExactAndAllowWhileIdle`; pada Android 14+ `SCHEDULE_EXACT_ALARM` tidak otomatis diberikan untuk banyak instalasi baru.
- Full-screen intent benar secara konsep untuk alarm, tetapi Android 14+ dapat membatasi izin FSI; aplikasi perlu memeriksa `NotificationManager.canUseFullScreenIntent()` dan menyediakan fallback notifikasi heads-up.

## Dasar teknis resmi

- Android `AlarmManager` berjalan di luar lifecycle aplikasi dan dapat memicu BroadcastReceiver walaupun aplikasi ditutup atau perangkat sedang tidur.
- Untuk alarm yang harus tepat waktu, gunakan exact alarm hanya bila memang fungsi utama pengguna memerlukannya; `setExactAndAllowWhileIdle` cocok untuk alarm user-facing yang harus menembus Doze.
- `SCHEDULE_EXACT_ALARM` harus dicek dengan `canScheduleExactAlarms()` dan user dapat diarahkan ke `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`.
- Alarm harus dijadwalkan ulang setelah `BOOT_COMPLETED`; data jadwal harus disimpan lokal.
- Full-screen intent hanya boleh dipakai untuk use case alarm/panggilan, dan pada Android 14+ izin FSI perlu dapat diperiksa.

Referensi:
- https://developer.android.com/develop/background-work/services/alarms
- https://developer.android.com/about/versions/14/changes/schedule-exact-alarms
- https://source.android.com/docs/core/permissions/fsi-limits
