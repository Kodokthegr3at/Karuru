**Bahasa:** [English](README.md) | Bahasa Indonesia | [日本語](README.ja.md)

# Karuru (カルル) — Aplikasi Web Pasar Loak

> Aplikasi web pasar loak / iklan baris yang dibangun di atas Java EE murni (`javax.*`): Servlet, JSP, JDBC, dan WebSocket langsung, tanpa framework, dengan build Maven.

---
<img width="1920" height="1080" alt="Blue and Beige Simple Project Proposal Presentation (2)" src="https://github.com/user-attachments/assets/aaf1e0da-385d-44ac-998f-27e603267763" />

### 1. Gambaran Proyek

**Karuru** adalah aplikasi pasar loak pribadi: listing barang, pembelian, penyewaan, negosiasi harga, chat real-time, notifikasi, dompet di dalam aplikasi, dan dashboard admin. Semuanya berjalan di Java EE murni (Servlet 4.0, JSP, JDBC, WebSocket). Tidak memakai Spring. Proyek ini berupa WAR Maven yang di-deploy ke Tomcat 9.

Semua bagian ditulis sendiri: siklus hidup servlet, rantai filter, kontrol akses berbasis session, dan pengelolaan koneksi JDBC. Tidak ada yang disembunyikan di balik framework. Artinya, ada beberapa hal yang perlu dibereskan sebelum aplikasi ini dipakai di production; lihat catatan keamanan di bawah.

### 2. Tech Stack

| Lapisan | Teknologi |
|---|---|
| Bahasa | Java 17 |
| Web layer | Java Servlet 4.0 (`javax.servlet`), JSP, JSTL |
| Real-time | Java WebSocket API (`MessageWebSocket`, `NotificationWebSocket`, dikoordinasikan oleh `WebSocketManager`) |
| Autentikasi / password | **BCrypt** (`org.mindrot.jbcrypt`, lewat `PasswordUtils`, cost factor = 12), autentikasi berbasis session yang dijaga `SessionFilter`, dan verifikasi email lewat Gmail SMTP |
| Akses data | **JDBC** (MySQL Connector/J melalui connection pool Tomcat JDBC, `PreparedStatement` untuk setiap query; query bersama ada di `dao/`) |
| Database | MySQL (`karuru_db`) |
| Frontend | JSP + **Bootstrap 5.3.2** (dimuat lewat CDN di `includes/header.jsp`, dipakai semua halaman) + Bootstrap Icons + JavaScript vanilla per halaman (`js/*.js`), Chart.js untuk analitik admin |
| Build / Deploy | Maven (`pom.xml`, wrapper `./mvnw`) → WAR di-deploy ke Tomcat 9; bisa di-import ke Eclipse lewat m2e. Test dengan JUnit 5. |

### 3. Arsitektur
<img width="1920" height="1080" alt="01" src="https://github.com/user-attachments/assets/f5e40046-e173-4e65-a605-8e322bcc203e" />

```
Browser
  │  HTTP / WebSocket
  ▼
Rantai Filter
  ├─ CsrfFilter      … menolak POST/PUT/DELETE lintas origin
  └─ SessionFilter   … menjaga path yang dilindungi (/dashboard.jsp, /admin/*, dll.)
  (encoding UTF-8 untuk request/response diatur di META-INF/context.xml)
  │
  ▼
Lapisan Servlet (34 servlet) ── satu fitur, satu servlet; API JSON mewarisi ApiServlet
  ├─ Auth: Login / Register / Logout / ForgotPassword / ResetPassword / Verify
  ├─ Listing: Product / ProductDetails / CreateListing / Category / Search / Banner
  ├─ Transaksi: Cart / Checkout / Order / Payment / Offer / Rental / Wallet
  ├─ Sosial: Review / Messages / Notifications / Favorite / SavedSearches / RecentlyViewed
  ├─ Pengguna: Profile / SellerProfile / Settings / Garage / Dashboard / Upload
  └─ Admin: Admin (pengguna, log aktivitas, CRUD) / Analytics / HealthCheck
  │
  ▼
dao/* + util.DatabaseConnection ── connection pool Tomcat JDBC yang dikonfigurasi dari db.properties
  │
  ▼
MySQL (karuru_db)

Secara paralel:
WebSocketManager mencatat sesi MessageWebSocket / NotificationWebSocket yang aktif
dan mengirim pesan chat serta notifikasi ke klien yang terhubung secara real-time.
```

Beberapa catatan tentang susunan ini:
- **Tidak ada front controller.** Setiap URL punya servlet sendiri. Servlet API JSON berbagi `ApiServlet`, yang memetakan `?action=` ke handler dan menangani pengecekan login/admin, koneksi DB, serta respons error di satu tempat.
- `db.properties` di-ignore oleh git; yang di-commit hanya `db.properties.example`. Ini pola yang umum untuk konfigurasi semacam ini.

### 4. Fitur Utama

- Registrasi, login, reset password, dan verifikasi email pengguna (lewat Gmail SMTP)
- Membuat listing (`CreateListingServlet`), menjelajah kategori, serta pencarian/filter (`Search`, `Category`)
- Alur keranjang → checkout → pesanan → pembayaran (`Cart` → `Checkout` → `Order` → `Payment`)
- Transaksi sewa (`Rental`) dan penawaran harga (`Offer`)
- Pengelolaan saldo dompet di dalam aplikasi (`Wallet`)
- Favorit, pencarian tersimpan, dan riwayat barang yang baru dilihat
- Ulasan dan rating (`Review`)
- Pesan dan notifikasi real-time lewat WebSocket (`MessagesServlet` + `MessageWebSocket`, `NotificationsServlet` + `NotificationWebSocket`)
- Profil penjual dan pembeli, pengaturan akun (`SellerProfile`, `Profile`, `Settings`)
- Dashboard admin: manajemen pengguna, analitik penjualan dengan Chart.js, manajemen banner
- Endpoint health check (`HealthCheckServlet`) untuk pemantauan dasar
- Tema terang dan gelap: mengikuti setelan OS sampai pengguna memilih lewat tombol di header (disimpan di browser)

### 5. Memulai

**Prasyarat**: JDK 17+, Tomcat 9, MySQL 8 / MariaDB 10.6+. Maven tidak perlu dipasang karena repo sudah menyertakan Maven Wrapper (`./mvnw`).

```bash
# 1. Clone
git clone https://github.com/Kodokthegr3at/Karuru.git
cd Karuru

# 2. Buat database (schema + kategori awal)
mysql -u root -p < db/schema.sql
mysql -u root -p karuru_db < db/seed.sql

# 3. Konfigurasi (kedua file di-ignore git; aplikasi tidak mau jalan tanpa keduanya)
cp src/main/resources/db.properties.example    src/main/resources/db.properties
cp src/main/resources/email.properties.example src/main/resources/email.properties
# isi kredensial DB dan Gmail app password

# 4. Build + jalankan test
./mvnw package          # → target/KaruruFleaMarket.war

# 5a. Deploy: salin WAR ke folder webapps/ milik Tomcat, atau
# 5b. Eclipse: File > Import > Existing Maven Projects, lalu Run on Server (Tomcat 9)

# 6. Buka http://localhost:8085/KaruruFleaMarket
#    Untuk membuat admin: daftar dulu, lalu  UPDATE users SET role='admin' WHERE username='kamu';
```

### 6. Catatan Keamanan

- **Kredensial** disimpan di `db.properties` / `email.properties` yang di-ignore git; kalau konfigurasinya tidak ada, deployment gagal saat startup (`util.AppConfig`). Gmail app password yang dulu pernah di-commit di `EmailConfig.java` masih ada di git history, jadi harus dicabut.
- **Password**: BCrypt (cost 12) lewat `PasswordUtils`. Session id diganti saat login (pertahanan terhadap session fixation).
- **CSRF**: `util.CsrfFilter` menolak POST/PUT/DELETE lintas origin (cek Origin/Referer), ditambah cookie session `SameSite=Lax` + `HttpOnly` (`META-INF/context.xml`, `web.xml`).
- **Autentikasi WebSocket**: pengguna diambil dari session HTTP yang sudah login saat handshake (`websocket.SessionUserConfigurator`), tidak pernah dari query string `?userId=`.
- **Otorisasi**: aksi khusus admin (manajemen banner, daftar pengguna, CRUD admin) dicek dengan `SessionFilter.isAdmin`; CRUD generik admin memakai whitelist nama tabel dan memvalidasi nama kolom karena keduanya digabung langsung ke SQL.
- **Dompet**: pembacaan saldo di dalam transaksi memakai `SELECT … FOR UPDATE`, sehingga penarikan/transfer yang bersamaan tidak bisa membuat saldo minus.
- **SQL injection**: semua nilai dari pengguna lewat `PreparedStatement`.
- **Respons error** tidak memuat detail exception/SQL; detail itu hanya masuk ke log server. Aksi yang mengubah data hanya lewat POST/DELETE (tidak pernah GET), jadi semuanya tercakup oleh filter CSRF.
- **Log** tidak pernah memuat link reset password atau verifikasi (link tersebut berisi token).

### 7. Struktur Proyek

```
Karuru/
├── src/main/java/
│   ├── servlet/      … 34 servlet per fitur + base class abstrak ApiServlet
│   ├── dao/          … query bersama (pesanan, dompet, notifikasi, …)
│   ├── websocket/     … endpoint WebSocket untuk pesan & notifikasi
│   └── util/           … koneksi DB, hashing password, filter, konfigurasi email
├── src/main/resources/
│   └── db.properties.example, email.properties.example
├── src/test/java/      … test JUnit 5
├── db/                 … schema.sql, seed.sql
├── src/main/webapp/
│   ├── *.jsp            … view JSP untuk tiap halaman
│   ├── admin/          … halaman admin
│   ├── error/           … halaman error 404 / 500
│   ├── img/ images/    … aset statis
│   └── WEB-INF/web.xml … konfigurasi filter dan halaman error
├── pom.xml, mvnw        … build Maven (+ wrapper)
├── .github/workflows/   … CI: build + test, load schema ke MySQL 8.4
├── .classpath / .project … metadata Eclipse (m2e + WTP)
└── README.md, README.id.md, README.ja.md
```

---

## Pembuat

- Maintainer: `kodoktheGr3at`
- GitHub: [github.com/Kodokthegr3at](https://github.com/Kodokthegr3at)
- Repository: [github.com/Kodokthegr3at/Karuru](https://github.com/Kodokthegr3at/Karuru)

## Lisensi

Repositori ini belum menyertakan file lisensi. Anggap kodenya all-rights-reserved sampai file `LICENSE` ditambahkan.
