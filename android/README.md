# Adrisya Invoice — Aplikasi Android

Cangkang Android untuk aplikasi web Adrisya Invoice
(https://konseptormalam.github.io/AdrisyaInvoice/) dengan jembatan native:

- **Print thermal Bluetooth** (printer 58mm / 80mm, ESC/POS, ada logo di struk)
- **Print A4** lewat dialog print Android (printer WiFi/USB/jaringan mana pun)
- **Kirim WhatsApp + PDF** lewat menu berbagi Android
- **Simpan backup** ke folder Download
- **Pilih berkas** untuk pulihkan backup

## Cara mengambil APK

1. Buka tab **Actions** di repo ini.
2. Pilih run **Build APK Adrisya Invoice** terbaru yang hijau (selesai).
3. Di bagian **Artifacts**, unduh **Adrisya-Invoice-APK** (file ZIP berisi `app-debug.apk`).
4. Ekstrak ZIP, pasang `app-debug.apk` di HP (izinkan "pasang dari sumber tidak dikenal" bila diminta).

APK otomatis dibangun ulang setiap ada perubahan di folder `android/`.
Update fitur aplikasi web TIDAK perlu build APK ulang — aplikasi membuka versi web terbaru.

## Cara pakai print thermal

1. Pasangkan printer Bluetooth di Pengaturan Bluetooth HP (pairing seperti biasa).
2. Di aplikasi: buka invoice → **Print** → pilih **Thermal 58mm / 80mm** →
   **Pilih Printer Bluetooth** → ketuk nama printer.
3. Ketuk **Cetak Sekarang**. Printer yang dipilih diingat untuk print berikutnya
   (bisa diganti kapan pun, atau diatur di Pengaturan aplikasi).

Build APK berjalan otomatis di GitHub Actions setiap ada perubahan pada folder ini.
