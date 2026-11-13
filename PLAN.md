# Rencana: Glideslope Android full native

Sumber kebenaran tetap PRD Glideslope (F-01 sampai F-14). Dokumen ini urutan kerja dan arsitektur untuk membawa **semua** layar PWA ke Kotlin/Compose, setara atau lebih baik dari web mobile. Centang hanya yang sudah diuji di HP.

## Arsitektur

- **Baca:** setiap layar membaca Room. `transactions`, `accounts`, `categories` punya tabel bertipe; tabel lain dibaca dari `sync_rows` (JSON per baris) lewat `Repo` yang memetakan ke data class per layar. Satu pengguna, ratusan sampai ribuan baris: aman dibaca di memori.
- **Tulis (F-14.2):** setiap aksi menulis ke Room lebih dulu (tampil seketika dengan penanda "Menunggu dikirim") dan menambah satu baris ke `outbox` (op_id, type, payload, status) dalam satu transaksi lokal. `PushWorker` mengirim berurutan ke `/api/v1/sync/push` (≤ 50 op), menandai applied/duplicate, menyimpan rejected/conflict ke daftar "Perlu ditinjau", lalu menjalankan pull.
- **Turunan:** runway, anggaran, dan kekayaan memakai ringkasan server (`/api/v1/summary`) sampai port Kotlin (F-14.4) selesai; angka yang dipengaruhi antrean diberi "≈".
- **Navigasi:** tab bawah (Beranda, Inbox, +, Transaksi, Aset) + tumpukan layar detail dengan tombol kembali dan predictive back.
- **Online saja (F-14.2):** unggah nota, sambung Gmail, ekspor/impor, setel PIN, gabung merchant, harga publik.

## Fase

| # | Isi | Server (op baru di `sync/push`) |
|---|---|---|
| A ✅ (dibangun, uji HP menunggu) | Outbox + PushWorker + penerapan lokal; Repo `sync_rows`; tumpukan layar | – |
| B ✅ (dibangun) | Tambah: pengeluaran, pemasukan, transfer (numpad, kategori, akun, tanggal, catatan, sekali jalan, "Dipakai untuk…") | `tx.create`, `tx.transfer` (ada) |
| C ✅ (dibangun) | Inbox: kartu geser, Konfirmasi/Ubah/Abaikan, transfer vs pengeluaran, usulan cocok | `review.*`, `proposal.*`, `tx.convert` (ada) |
| D ✅ (dibangun) | Transaksi: kelompok per hari, cari, filter; detail (bukti, label); ubah, hapus, pulihkan | `tx.edit`, `tx.delete`, `tx.restore` (ada), `label.set` |
| E ◐ (daftar dibangun; detail dan tulis menyusul) | Aset: akun dan saldo, detail akun (titik cek, biaya isi saldo), aset dan detail (harga, beli, cairkan), piutang | `account.*`, `checkpoint.add`, `asset.*`, `repayment.add` |
| F ◐ (2026-11-13: baca online dari /layar, belum offline/tulis) | Analitik (pengeluaran, kategori, kekayaan) dan Anggaran | `budget.set` |
| G | Rencana pengeluaran, Langganan, Label | `planned.*`, `subscription.*`, `label.*` |
| H | Bagi tagihan, Scan nota (CameraX → `/api/v1/receipts`) | `split.*`, unggah nota |
| I | Pengaturan lengkap, Sumber email, Gmail (Custom Tab), ekspor | `settings.update`, `source.*` |
| J | Khas native: widget "Hari ini", ubin QS, pintasan, notifikasi + aksi (FCM), bagikan | FCM (P-15) |

## Ide kreatif yang dipakai

- **Lintasan wisuda:** grafik runway jadi pusat Beranda; ketuk titik untuk melihat bulan itu.
- **Konfirmasi geser dengan haptik** di Inbox; jumlah besar diberi getar ringan saat disimpan.
- **"Tutup hari"** opsional malam hari: ringkasan pengeluaran hari ini vs aman per hari.
- **Mode privasi** di semua angka dan widget.

## Selesai bila

Semua 23 layar pemilik native; tidak ada tautan ke PWA kecuali sambung Gmail; offline: tambah/konfirmasi/ubah tersimpan dan terkirim tanpa duplikat; TalkBack dan huruf 200% lolos.


## Catatan 2026-11-13
Tema gelap + bilah bawah melayang; API Go; Scan nota dengan OCR di HP (ML Kit) + Tinjau nota; layar F, G (baca) dan Sumber email online; belum: tulis offline untuk anggaran/rencana/langganan/label, Bagi tagihan native, widget/FCM (J), uji di perangkat (tidak ada emulator di Mac ini).

Update 2026-11-13 (malam): semua layar sudah native, termasuk tulis online untuk Anggaran, Langganan, Rencana, Label, Akun, Aset (detail, harga, beli, cairkan), Bagi tagihan, Sumber email, Pengaturan, ekspor. Tulis tetap online (tidak lewat outbox); offline-first hanya Tambah/Inbox/Transaksi. Belum diuji di perangkat.
