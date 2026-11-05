# AGENTS.md — Glideslope Android

Pendamping Android untuk Glideslope: membaca notifikasi aplikasi keuangan yang dipilih pemilik dan meneruskannya ke webhook server.

Sumber kebenaran: PRD di repo `glideslope`, bagian **F-12.5** (aplikasi ini) dan **F-12.1** (kontrak webhook). Perbarui PRD dulu bila keputusan berubah.

## Perintah
- `./gradlew testDebugUnitTest` — uji unit logika murni (`core/`)
- `./gradlew lintDebug` — lint Android
- `./gradlew assembleDebug` — APK debug (`app/build/outputs/apk/debug/app-debug.apk`)
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` — pasang ke HP

## Aturan
- Aplikasi meneruskan, tidak menafsirkan: penyaringan promo, pembacaan jumlah, dan pencocokan ada di server.
- Periksa daftar izin paket sebelum membaca isi notifikasi; notifikasi paket lain tidak dibaca atau dicatat.
- Logika murni (penangkapan teks, payload, kode HTTP) tinggal di `core/` tanpa tipe Android, dengan uji unit.
- Jangan tulis rahasia webhook, token, atau keystore ke kode, log, atau commit. Rahasia disimpan terenkripsi kunci Android Keystore.
- Kontrak webhook: `{source_app, text, posted_at}`, `posted_at` ISO-8601, judul di depan `text`, header `x-webhook-secret`.
- Satu kunci tanda tangan rilis yang tetap (terdaftar di verifikasi pengembang Google); jangan buat ulang.
- Tambah dependency hanya setelah bertanya.
