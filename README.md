# Karuru (カルル) — Flea Market Web Application

> A Java EE (Jakarta Servlet) flea-market / classifieds platform built with raw Servlets, JSP, JDBC and WebSockets.
> Servlet・JSP・JDBC・WebSocketで構築されたフリマアプリケーションです。

---

## 🇯🇵 日本語

### 1. プロジェクト概要

**Karuru** は個人開発のフリマ（フリーマーケット）プラットフォームです。出品・購入・レンタル・オファー（値引き交渉）・メッセージング・通知・ウォレット・管理者ダッシュボードまで、ECサイトとして必要な一通りの機能を **生の Jakarta EE スタック**（Servlet 4.0 / JSP / JDBC / WebSocket）だけで実装しているのが特徴です。Spring や Maven のようなフレームワーク／ビルドツールは使われておらず、Eclipse の Dynamic Web Project としてそのまま Tomcat にデプロイする構成になっています。

シニアエンジニアの視点から見ると、これは「フレームワークの抽象化に頼らずに Web の基礎（Servlet ライフサイクル、フィルタチェーン、セッション管理、JDBC コネクション管理）を理解しているか」を試す、教育的価値の高い実装と言えます。同時に、本番運用を見据えた場合に改善すべき点（後述）もいくつか見受けられます。

### 2. 技術スタック

| レイヤー | 技術 |
|---|---|
| 言語 | Java 17 |
| Web層 | Jakarta Servlet 4.0, JSP, JSTL |
| リアルタイム通信 | Java WebSocket (`javax.websocket` ベースの `MessageWebSocket` / `NotificationWebSocket`) |
| 認証/パスワード管理 | **BCrypt**（`org.mindrot.jbcrypt`、`PasswordUtils` 経由、cost factor = 12）+ セッションベース認証 + `SessionFilter` によるアクセス制御 + メール認証（Gmail SMTP） |
| データアクセス | **JDBC**（MySQL Connector/J、`java.sql.DriverManager` ベースの自前コネクション管理、全34Servletで `PreparedStatement` を徹底使用） |
| データベース | MySQL（`karuru_db`） |
| フロントエンド | JSP + **Bootstrap 5.3.2**（CDN経由、`includes/header.jsp` で全画面共通読み込み）+ Bootstrap Icons + バニラ JavaScript（画面ごとの `*.js`）、Chart.js（管理画面の分析グラフ） |
| ビルド／デプロイ | Eclipse WTP（`.project` / `.classpath`）→ Tomcat 9 へ WAR デプロイ。Maven/Gradle は未使用 |


### 3. アーキテクチャ

```
ブラウザ
  │  HTTP / WebSocket
  ▼
FilterChain
  ├─ FilterEncodingUTF8   … 全リクエストの文字コードをUTF-8に統一
  └─ SessionFilter        … 保護対象パス（/dashboard.jsp, /admin/* 等）の未ログインアクセスを遮断
  │
  ▼
Servlet 層（34本） ── 1機能 = 1Servletの素直なマッピング
  ├─ 認証系: Login / Register / Logout / ForgotPassword / ResetPassword / Verify
  ├─ 出品/商品系: Product / ProductDetails / CreateListing / Category / Search / Banner
  ├─ 取引系: Cart / Checkout / Order / Payment / Offer / Rental / Wallet
  ├─ ソーシャル系: Review / Messages / Notifications / Favorite / SavedSearches / SavedSellers / RecentlyViewed
  ├─ ユーザー系: Profile / SellerProfile / Settings / Garage / Activity
  └─ 管理系: Admin / Analytics / Users / HealthCheck
  │
  ▼
util.DatabaseConnection ── classpath上の db.properties を読み込み、MySQLへ直接JDBC接続
  │
  ▼
MySQL (karuru_db)

並行して:
WebSocketManager が MessageWebSocket / NotificationWebSocket のセッションを管理し、
チャットや通知をリアルタイムでブラウザにpush
```

設計上の特徴：
- **Front Controller パターンは採用せず**、URLごとに専用Servletを割り当てるシンプルな構成。学習コストは低いが、横断的関心事（認可、ロギング等）はServlet単位での重複が発生しやすい。
- 認証情報はDBプロパティファイル（`db.properties`）を `.gitignore` 対象にしつつ、サンプル（`db.properties.example`）をコミットする一般的なパターンを踏襲。

### 4. 主な機能

- ユーザー登録・ログイン・パスワードリセット・メール認証（Gmail SMTP経由）
- 商品出品（`CreateListingServlet`）、カテゴリ別検索・絞り込み（`Search`, `Category`）
- カート → チェックアウト → 注文 → 決済（`Cart` → `Checkout` → `Order` → `Payment`）
- レンタル取引（`Rental`）、価格交渉オファー（`Offer`）
- ウォレット（`Wallet`）による残高管理
- お気に入り、保存した検索条件、フォロー中の出品者、閲覧履歴（`Favorite` / `SavedSearches` / `SavedSellers` / `RecentlyViewed`）
- レビュー・評価（`Review`）
- WebSocketによるリアルタイムメッセージング・通知（`MessagesServlet` + `MessageWebSocket`, `NotificationsServlet` + `NotificationWebSocket`）
- 出品者プロフィール／一般プロフィール／設定（`SellerProfile`, `Profile`, `Settings`）
- 管理者ダッシュボード：ユーザー管理・売上分析（Chart.js）・バナー管理（`Admin`, `Analytics`, `Users`, `Banner`）
- ヘルスチェックエンドポイント（`HealthCheckServlet`）

### 5. セットアップ手順

**前提条件**: JDK 17、Tomcat 9、MySQL、Eclipse（または任意のIDE + Tomcat手動デプロイ）、MySQL Connector/J（クラスパスに追加）

```bash
# 1. クローン
git clone https://github.com/Kodokthegr3at/Karuru.git
cd Karuru

# 2. MySQLにDBを作成
mysql -u root -p -e "CREATE DATABASE karuru_db CHARACTER SET utf8mb4;"

# 3. DB接続情報を設定
cp src/main/resources/db.properties.example src/main/resources/db.properties
# db.properties を編集し、db.url / db.user / db.password を環境に合わせる

# 4. Eclipseでプロジェクトをインポート
#    File > Import > Existing Projects into Workspace
#    Tomcat 9 サーバーを追加し、プロジェクトをそこにデプロイ

# 5. ブラウザでアクセス
#    http://localhost:8085/KaruruFleaMarket
```

> ⚠️ Maven/Gradle のビルドファイルが存在しないため、MySQL JDBCドライバ等の依存ライブラリは手動でクラスパス（`WEB-INF/lib`）に配置する必要があります。

### 6. セキュリティに関する所見（シニアエンジニア視点での指摘）

公開リポジトリのコードを読んだ上で、本番運用前に必ず対応すべき点を率直に共有します：

1. **`EmailConfig.java` にGmailのアプリパスワードがハードコードされてリポジトリにコミットされています。** これは重大なセキュリティリスクです。直ちに当該パスワードを無効化・再発行し、環境変数または `db.properties` と同様に `.gitignore` 対象の設定ファイルに移すべきです。
2. パスワードは `PasswordUtils`（`org.mindrot.jbcrypt`、cost factor = 12）で **BCryptハッシュ化**されており、平文保存やMD5/SHA等の高速ハッシュは使われていません。この点は適切に実装されています。
3. SQLインジェクション対策として、確認できた範囲（全34 Servlet）では `PreparedStatement` が一貫して使用されており、生の `Statement`／文字列連結クエリは見つかりませんでした。良好な実装です。
4. `db.properties` のデフォルト値（`root` ユーザー・パスワード空欄）は開発用としては妥当ですが、本番デプロイ時に明示的な設定が強制されるような fail-fast 設計（環境変数必須化など）が望ましいです。

### 7. プロジェクト構成

```
Karuru/
├── src/main/java/
│   ├── servlet/      … 34本の機能別Servlet
│   ├── websocket/     … メッセージ・通知用WebSocketエンドポイント
│   └── util/           … DB接続、パスワードユーティリティ、フィルタ、メール設定
├── src/main/resources/
│   └── db.properties.example
├── src/main/webapp/
│   ├── *.jsp            … 各画面のJSPビュー
│   ├── admin/          … 管理画面
│   ├── error/           … 404 / 500 エラーページ
│   ├── img/ images/    … 静的アセット
│   └── WEB-INF/web.xml … フィルタ・エラーページ定義
├── .classpath / .project … Eclipse Dynamic Web Project設定
└── README.md
```

---

## 🇬🇧 English

### 1. Project Overview

**Karuru** is a personal-project flea-market (classifieds/marketplace) web application. It covers the full surface area of a typical e-commerce platform — listings, purchases, rentals, price-offer negotiation, real-time messaging, notifications, an in-app wallet, and an admin dashboard — built entirely on the **raw Jakarta EE stack** (Servlet 4.0, JSP, JDBC, WebSocket). There is no framework (no Spring) and no build tool (no Maven/Gradle); it's structured as an Eclipse Dynamic Web Project deployed directly to Tomcat.

From a senior engineer's perspective, this is a solid demonstration of understanding web fundamentals without leaning on framework abstractions — the servlet lifecycle, filter chains, session-scoped access control, and manual JDBC connection management are all hand-rolled. That said, there are a few things worth flagging before this goes anywhere near production (see Section 6).

### 2. Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Web layer | Jakarta Servlet 4.0, JSP, JSTL |
| Real-time | Java WebSocket API (`MessageWebSocket`, `NotificationWebSocket`, coordinated via `WebSocketManager`) |
| Auth / password handling | **BCrypt** (`org.mindrot.jbcrypt`, via `PasswordUtils`, cost factor = 12), session-based authentication enforced by `SessionFilter`, and email verification over Gmail SMTP |
| Data access | **JDBC** (MySQL Connector/J, manual `java.sql.DriverManager`-based connection handling, `PreparedStatement` used consistently across all 34 servlets) |
| Database | MySQL (`karuru_db`) |
| Frontend | JSP + **Bootstrap 5.3.2** (loaded via CDN in `includes/header.jsp`, shared across all pages) + Bootstrap Icons + page-specific vanilla JavaScript (`js/*.js`), Chart.js for admin analytics |
| Build / Deploy | Eclipse WTP project (`.project` / `.classpath`) → deployed as a WAR to Tomcat 9. No Maven/Gradle. |


### 3. Architecture

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
util.DatabaseConnection ── reads db.properties from the classpath, opens a direct JDBC connection
  │
  ▼
MySQL (karuru_db)

In parallel:
WebSocketManager tracks live MessageWebSocket / NotificationWebSocket sessions
and pushes chat messages and notifications to connected clients in real time.
```

Notable design choices:
- **No front-controller pattern** — every URL maps to its own dedicated servlet. This keeps the learning curve low, but cross-cutting concerns (authorization checks, logging) tend to get duplicated across servlets rather than centralized.
- Credentials follow the conventional pattern of committing an `db.properties.example` template while keeping the real `db.properties` git-ignored.

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

**Prerequisites**: JDK 17, Tomcat 9, MySQL, an IDE with WTP support (e.g. Eclipse) or manual Tomcat deployment, and the MySQL Connector/J JAR on the classpath.

```bash
# 1. Clone
git clone https://github.com/Kodokthegr3at/Karuru.git
cd Karuru

# 2. Create the database
mysql -u root -p -e "CREATE DATABASE karuru_db CHARACTER SET utf8mb4;"

# 3. Configure the DB connection
cp src/main/resources/db.properties.example src/main/resources/db.properties
# edit db.url / db.user / db.password to match your environment

# 4. Import into Eclipse
#    File > Import > Existing Projects into Workspace
#    Add a Tomcat 9 server and deploy the project to it

# 5. Open in your browser
#    http://localhost:8085/KaruruFleaMarket
```

> ⚠️ There's no Maven/Gradle build descriptor, so dependencies like the MySQL JDBC driver must be manually placed on the classpath (`WEB-INF/lib`).

### 6. Security Notes (a candid senior-engineer review)

Having read through the public source, a few things should be addressed before any production deployment:

1. **`EmailConfig.java` has a Gmail app password hardcoded and committed to the repository.** This is a real, exploitable secret leak. It should be revoked/rotated immediately and moved out to an environment variable or a git-ignored config file, consistent with how `db.properties` is already handled.
2. Passwords are hashed with **BCrypt** via `PasswordUtils` (`org.mindrot.jbcrypt`, cost factor 12) — no plaintext storage, no fast general-purpose hashes like MD5/SHA. This is implemented correctly.
3. SQL injection defenses look solid: across all 34 servlets, `PreparedStatement` is used consistently — no raw `Statement` or string-concatenated queries were found.
4. The default `db.properties` fallback (root user, empty password) is reasonable for local dev, but production deploys would benefit from a fail-fast design that requires explicit configuration (e.g. via required environment variables) rather than silently falling back to insecure defaults.

### 7. Project Structure

```
Karuru/
├── src/main/java/
│   ├── servlet/      … 34 feature-specific servlets
│   ├── websocket/     … WebSocket endpoints for messaging & notifications
│   └── util/           … DB connection, password hashing, filters, email config
├── src/main/resources/
│   └── db.properties.example
├── src/main/webapp/
│   ├── *.jsp            … JSP views for each page
│   ├── admin/          … admin screens
│   ├── error/           … 404 / 500 error pages
│   ├── img/ images/    … static assets
│   └── WEB-INF/web.xml … filter and error-page configuration
├── .classpath / .project … Eclipse Dynamic Web Project metadata
└── README.md
```

---

## License / ライセンス

No license file is present in the repository — treat the source as "all rights reserved" by the author unless/until a `LICENSE` file is added.
リポジトリにライセンスファイルは含まれていません。`LICENSE` が追加されるまでは著作者にすべての権利が留保されているものとして扱ってください。
