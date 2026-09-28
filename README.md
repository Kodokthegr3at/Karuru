**Language:** [English](README.md) | [Bahasa Indonesia](README.id.md) | [日本語](README.ja.md)

# Karuru (カルル) — Flea Market Web Application

> A flea-market / classifieds web app built on plain Jakarta EE — raw Servlets, JSP, JDBC and WebSockets, no framework, built with Maven.

---
<img width="1920" height="1080" alt="Blue and Beige Simple Project Proposal Presentation (2)" src="https://github.com/user-attachments/assets/aaf1e0da-385d-44ac-998f-27e603267763" />

### 1. Project Overview

**Karuru** is a personal flea-market app — listings, purchases, rentals, price-offer negotiation, real-time chat, notifications, an in-app wallet, and an admin dashboard, all running on plain Jakarta EE (Servlet 4.0, JSP, JDBC, WebSocket). No Spring — a Maven WAR project deployed to Tomcat 9.

Everything here is hand-rolled: the servlet lifecycle, filter chains, session-based access control, JDBC connection handling — none of it hides behind a framework. That also means a few things need fixing before this touches production; see the security notes below.

### 2. Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Web layer | Jakarta Servlet 4.0, JSP, JSTL |
| Real-time | Java WebSocket API (`MessageWebSocket`, `NotificationWebSocket`, coordinated via `WebSocketManager`) |
| Auth / password handling | **BCrypt** (`org.mindrot.jbcrypt`, via `PasswordUtils`, cost factor = 12), session-based authentication enforced by `SessionFilter`, and email verification over Gmail SMTP |
| Data access | **JDBC** (MySQL Connector/J via the Tomcat JDBC connection pool, `PreparedStatement` used consistently across all 34 servlets) |
| Database | MySQL (`karuru_db`) |
| Frontend | JSP + **Bootstrap 5.3.2** (loaded via CDN in `includes/header.jsp`, shared across all pages) + Bootstrap Icons + page-specific vanilla JavaScript (`js/*.js`), Chart.js for admin analytics |
| Build / Deploy | Maven (`pom.xml`, wrapper `./mvnw`) → WAR deployed to Tomcat 9; imports into Eclipse via m2e. JUnit 5 tests. |

### 3. Architecture
<img width="1920" height="1080" alt="01" src="https://github.com/user-attachments/assets/f5e40046-e173-4e65-a605-8e322bcc203e" />

```
Browser
  │  HTTP / WebSocket
  ▼
Filter Chain
  ├─ FilterEncodingUTF8  … normalizes request encoding to UTF-8 across the board
  └─ SessionFilter       … gatekeeps protected paths (/dashboard.jsp, /admin/*, etc.)
  │
  ▼
Servlet Layer (34 servlets) ── one feature, one servlet — a deliberately flat mapping
  ├─ Auth: Login / Register / Logout / ForgotPassword / ResetPassword / Verify
  ├─ Listings: Product / ProductDetails / CreateListing / Category / Search / Banner
  ├─ Commerce: Cart / Checkout / Order / Payment / Offer / Rental / Wallet
  ├─ Social: Review / Messages / Notifications / Favorite / SavedSearches / SavedSellers / RecentlyViewed
  ├─ User: Profile / SellerProfile / Settings / Garage / Activity
  └─ Admin: Admin / Analytics / Users / HealthCheck
  │
  ▼
util.DatabaseConnection ── Tomcat JDBC connection pool configured from db.properties
  │
  ▼
MySQL (karuru_db)

In parallel:
WebSocketManager tracks live MessageWebSocket / NotificationWebSocket sessions
and pushes chat messages and notifications to connected clients in real time.
```

A couple of notes on this setup:
- **No front controller** — every URL gets its own servlet. Easy to follow, but things like auth checks and logging end up repeated across servlets instead of living in one place.
- `db.properties` itself is git-ignored; only `db.properties.example` is committed, which is the usual pattern for this kind of config.

### 4. Key Features

- User registration, login, password reset, and email verification (via Gmail SMTP)
- Listing creation (`CreateListingServlet`), category browsing and search/filtering (`Search`, `Category`)
- Cart → checkout → order → payment pipeline (`Cart` → `Checkout` → `Order` → `Payment`)
- Rental transactions (`Rental`) and price-negotiation offers (`Offer`)
- In-app wallet balance management (`Wallet`)
- Favorites, saved searches, followed sellers, and recently-viewed history
- Reviews and ratings (`Review`)
- Real-time messaging and notifications over WebSocket (`MessagesServlet` + `MessageWebSocket`, `NotificationsServlet` + `NotificationWebSocket`)
- Seller and buyer profiles, account settings (`SellerProfile`, `Profile`, `Settings`)
- Admin dashboard: user management, sales analytics via Chart.js, banner management
- A health-check endpoint (`HealthCheckServlet`) for basic liveness monitoring

### 5. Getting Started

**Prerequisites**: JDK 17+, Tomcat 9, MySQL 8 / MariaDB 10.6+. Maven is not required — the repo ships the Maven Wrapper (`./mvnw`).

```bash
# 1. Clone
git clone https://github.com/Kodokthegr3at/Karuru.git
cd Karuru

# 2. Create the database (schema + initial categories)
mysql -u root -p < db/schema.sql
mysql -u root -p karuru_db < db/seed.sql

# 3. Configure (both files are git-ignored; the app refuses to start without them)
cp src/main/resources/db.properties.example    src/main/resources/db.properties
cp src/main/resources/email.properties.example src/main/resources/email.properties
# fill in DB credentials and a Gmail app password

# 4. Build + run tests
./mvnw package          # → target/KaruruFleaMarket.war

# 5a. Deploy: copy the WAR into Tomcat's webapps/, or
# 5b. Eclipse: File > Import > Existing Maven Projects, then Run on Server (Tomcat 9)

# 6. Open http://localhost:8085/KaruruFleaMarket
#    To get an admin: register, then  UPDATE users SET role='admin' WHERE username='you';
```

### 6. Security Notes

- **Credentials** live in git-ignored `db.properties` / `email.properties`; missing config fails deployment at startup (`util.AppConfig`). The Gmail app password that was once committed in `EmailConfig.java` is still in git history — it must be revoked.
- **Passwords**: BCrypt (cost 12) via `PasswordUtils`. Session id is rotated on login (session-fixation defense).
- **CSRF**: `util.CsrfFilter` rejects cross-origin POST/PUT/DELETE (Origin/Referer check), plus `SameSite=Lax` + `HttpOnly` session cookie (`META-INF/context.xml`, `web.xml`).
- **WebSocket auth**: the user is taken from the logged-in HTTP session during the handshake (`websocket.SessionUserConfigurator`), never from the `?userId=` query string.
- **Authorization**: admin-only actions (banner management, user listing, admin CRUD) check `SessionFilter.isAdmin`; the admin generic CRUD whitelists table names and validates column names because they are concatenated into SQL.
- **Wallet**: balance reads inside transactions use `SELECT … FOR UPDATE`, so concurrent withdrawals/transfers cannot overspend.
- **SQL injection**: all user values go through `PreparedStatement`.
- **Error responses** don't include exception/SQL details; those go to the server log only. State-changing actions are POST/DELETE only (never GET), so the CSRF filter covers them.
- **Logs** never contain password-reset or verification links (they carry tokens).

### 7. Project Structure

```
Karuru/
├── src/main/java/
│   ├── servlet/      … 34 feature-specific servlets
│   ├── websocket/     … WebSocket endpoints for messaging & notifications
│   └── util/           … DB connection, password hashing, filters, email config
├── src/main/resources/
│   └── db.properties.example, email.properties.example
├── src/test/java/      … JUnit 5 tests
├── db/                 … schema.sql, seed.sql
├── src/main/webapp/
│   ├── *.jsp            … JSP views for each page
│   ├── admin/          … admin screens
│   ├── error/           … 404 / 500 error pages
│   ├── img/ images/    … static assets
│   └── WEB-INF/web.xml … filter and error-page configuration
├── pom.xml, mvnw        … Maven build (+ wrapper)
├── .github/workflows/   … CI: build + tests, schema load on MySQL 8.4
├── .classpath / .project … Eclipse (m2e + WTP) metadata
└── README.md
```

---

## Author

- Maintainer: `kodoktheGr3at`
- GitHub: [github.com/Kodokthegr3at](https://github.com/Kodokthegr3at)
- Repository: [github.com/Kodokthegr3at/Karuru](https://github.com/Kodokthegr3at/Karuru)

## License

No license file is included in this repository — treat the code as all-rights-reserved until a `LICENSE` file is added.
