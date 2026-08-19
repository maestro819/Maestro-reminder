# Perbaikan Android Native Maestro Reminder

## Alur yang benar

Jadwal alarm sekarang disimpan di `SharedPreferences` sehingga perangkat tidak membutuhkan internet pada saat alarm dijalankan. `AlarmManager` menjadwalkan `RTC_WAKEUP` dan `AlarmReceiver` menerima pemicu ketika aplikasi sedang ditutup atau tidak berada di memori. Untuk perangkat yang sedang Doze, aplikasi memakai `setExactAndAllowWhileIdle()` apabila izin exact alarm tersedia; jika izin tersebut belum diberikan, aplikasi memakai `setAndAllowWhileIdle()` sebagai fallback.

Setelah perangkat reboot, perubahan waktu, atau perubahan zona waktu, receiver membaca jadwal lokal dan menjadwalkan ulang alarm. Karena itu, reboot tidak menghapus jadwal yang dibuat pengguna. Jadwal satu kali dihapus dari penyimpanan ketika alarm benar-benar dipicu.

Ketika alarm tiba, receiver membuat notifikasi kategori alarm berprioritas tinggi dengan full-screen intent menuju `AlarmScreenActivity`. Pada Android 14 atau lebih baru, sistem atau toko aplikasi dapat membatasi full-screen intent; notifikasi heads-up tetap menjadi fallback. Pengguna juga perlu mengizinkan notifikasi pada Android 13 atau lebih baru.

## Pengujian manual

Instal APK debug pada perangkat Android. Buka aplikasi, izinkan notifikasi, isi nama, kemudian tekan tombol **Tes Alarm (1 menit lagi)**. Tutup aplikasi dari layar recent apps; alarm tetap harus muncul. Uji berikutnya dengan mengaktifkan mode pesawat, karena jadwal lokal tidak membutuhkan internet. Untuk menguji reboot, buat alarm dengan waktu lebih panjang, restart perangkat, lalu pastikan alarm tetap terdaftar dan berbunyi.

Pada Android 12 atau lebih baru, jika akses **Alarm & pengingat** belum diberikan, aplikasi mengarahkan pengguna ke halaman pengaturan tersebut. Tanpa akses exact alarm, aplikasi masih menjadwalkan fallback inexact yang dapat terlambat sesuai kebijakan penghematan baterai Android.

## Build

Gunakan Android Studio atau jalankan `./gradlew assembleDebug` setelah menambahkan Gradle wrapper. APK debug hasil verifikasi lokal berada di `app/build/outputs/apk/debug/app-debug.apk`.

## Catatan konsep online/offline

Repositori awal memiliki halaman web Vercel, tetapi belum memiliki backend sinkronisasi atau model data cloud. Perbaikan ini membuat **alarm lokal** bekerja online maupun offline. Jika kebutuhan berikutnya adalah membuat jadwal dari perangkat lain dan menyinkronkannya ke perangkat ini, diperlukan backend, autentikasi, sinkronisasi konflik, serta mekanisme push; fitur tersebut tidak dapat dijamin hanya oleh `AlarmManager`.
