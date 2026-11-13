# Glideslope Android

Aplikasi pendamping kecil untuk [Glideslope](https://github.com/Ghanz08/glideslope): membaca notifikasi dari aplikasi keuangan yang kamu pilih (GoPay, OVO, DANA, ShopeePay, wondr, Livin', dan lain-lain), lalu meneruskan teksnya ke webhook Glideslope (`POST /api/v1/ingest/webhook`, dilayani backend Go). Server yang membaca jumlah, merchant, dan mencocokkan transaksi; tampilan utama tetap PWA.

Rancangan: PRD Glideslope F-12.5.

## Cara kerja
`Listener` (NotificationListenerService) → antrean SQLite (`Queue`) → `SendWorker` (WorkManager, butuh jaringan, backoff) → webhook.
- Hanya paket yang dicentang di Setelan yang dibaca; lainnya diabaikan sebelum isinya dibaca.
- Offline: notifikasi menunggu di antrean dan terkirim saat online. Notifikasi yang sama tidak masuk antrean dua kali.
- 401 menghentikan antrean sampai rahasia diperbarui; 413/422 ditandai ditolak; 429, 5xx, dan galat jaringan diulang.
- Rahasia webhook dienkripsi dengan kunci AES-GCM di Android Keystore. Log disimpan 7 hari dan bisa dihapus.

## Build
Butuh JDK 17+ (JBR Android Studio cukup) dan Android SDK platform 37.
```
./gradlew testDebugUnitTest lintDebug assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Pemasangan di HP (Samsung, Android 16)
1. Pasang APK, buka Glideslope, tab **Setelan**: isi alamat Glideslope (`https://…`), rahasia webhook (nilai `WEBHOOK_SECRET` server), dan centang aplikasi keuangan. Simpan.
2. Tab **Status** → **Akses notifikasi** → Buka setelan akses → izinkan Glideslope.
   Kalau diblokir "Setelan terbatas" (APK dari luar Play): Info aplikasi → ⋮ → **Izinkan setelan terbatas**, lalu ulangi.
   Kalau menu itu tidak ada: `adb shell cmd appops set id.glideslope.app ACCESS_RESTRICTED_SETTINGS allow`.
3. **Baterai** → Izinkan. Di Samsung juga: baterai aplikasi **Tidak dibatasi** dan masukkan ke **Aplikasi yang tidak pernah tidur**. Cek lagi setelah update sistem.
4. **Kirim tes** → status "Terkirim" di tab Log berarti server menerima.

## Batasan yang diketahui
- Android 15+ menyensor isi notifikasi yang berisi kode OTP untuk aplikasi seperti ini; notifikasi transaksi biasa tidak terpengaruh.
- Notifikasi yang dihapus saat aplikasi tidak tersambung tidak bisa diambil lagi.
- Verifikasi pengembang Google berlaku di Indonesia sejak 30 Sep 2026: untuk APK rilis, daftarkan `id.glideslope.app` dan kunci tanda tangannya (akun limited distribution), atau pasang lewat `adb`.


## Server, tema, dan OCR (2026-11-13)
- **API:** semua panggilan ke `/api/v1` backend Go (di VPS, Caddy mengarahkan `/api/v1/*` langsung ke kontainer Go). Galat dibaca dalam dua bentuk: lama `{error:{code,message}}` (masuk, sinkron) dan RFC 9457 `{code,detail}` (layar lain).
- **Tema:** Terang, Gelap, atau Sistem (Lainnya → Tampilan), token sama dengan PWA v2; bilah bawah melayang 64 dp dengan tombol + 60 dp.
- **Scan nota:** foto dikecilkan (1568 px, JPEG < 5 MB) dan dibaca di HP dengan ML Kit (model Latin ikut terpasang, tanpa internet). Teksnya dikirim bersama foto ke `POST /api/v1/receipts` (bagian `ocr`); server mem-parse dan memvalidasi, dan baru membaca fotonya dengan OCR server (RapidOCR) bila hasil di HP tidak lolos. Lalu layar Tinjau nota: koreksi angka, pilih akun/kategori, konfirmasi.
- **Layar online (butuh koneksi), semuanya native Kotlin:** Analitik, Anggaran (ubah per kategori), Langganan (tambah, setujui/tolak/hentikan), Piutang (WhatsApp, tandai lunas), Rencana pengeluaran (tambah/ubah/hapus/tandai bayar), Label, Rekening & wallet (tambah/ubah, titik cek, biaya isi saldo, arsip), Detail aset (harga, beli, cairkan, harga publik, arsip), Bagi tagihan (peserta, pembagian per item, simpan draf, bagikan, WhatsApp), Sumber email (pengirim, email belum terbaca, baca email lama), Pengaturan (rencana keuangan, PIN, kategori, ekspor ZIP, kesehatan sistem), dari menu **Lainnya**.
- **Satu-satunya yang membuka browser:** layar persetujuan Google untuk menghubungkan Gmail (OAuth butuh browser; callback-nya milik server).
- **Pemantau notifikasi:** tetap di menu Lainnya → Pemantau notifikasi HP.
