**言語:** [English](README.md) | [Bahasa Indonesia](README.id.md) | 日本語

# Karuru (カルル) — フリマ Web アプリケーション

> 素の Java EE（`javax.*`）で作ったフリマ／クラシファイド Web アプリです。Servlet、JSP、JDBC、WebSocket を直接使い、フレームワークは使わず、Maven でビルドします。

---
<img width="1920" height="1080" alt="Blue and Beige Simple Project Proposal Presentation (2)" src="https://github.com/user-attachments/assets/aaf1e0da-385d-44ac-998f-27e603267763" />

### 1. プロジェクト概要

**Karuru** は個人開発のフリマアプリです。出品、購入、レンタル、値下げ交渉、リアルタイムチャット、通知、アプリ内ウォレット、管理者ダッシュボードを備え、すべて素の Java EE（Servlet 4.0、JSP、JDBC、WebSocket）で動きます。Spring は使っていません。Maven の WAR プロジェクトとして Tomcat 9 にデプロイします。

Servlet のライフサイクル、フィルタチェーン、セッションによるアクセス制御、JDBC 接続の管理まで、すべて自前で書いています。フレームワークに隠れている部分はありません。そのため本番環境で使う前に対応が必要な点もいくつかあります。下のセキュリティに関する注意を参照してください。

### 2. 技術スタック

| レイヤー | 技術 |
|---|---|
| 言語 | Java 17 |
| Web レイヤー | Java Servlet 4.0（`javax.servlet`）、JSP、JSTL |
| リアルタイム | Java WebSocket API（`MessageWebSocket`、`NotificationWebSocket`、`WebSocketManager` で管理） |
| 認証／パスワード | **BCrypt**（`org.mindrot.jbcrypt`、`PasswordUtils` 経由、コスト 12）、`SessionFilter` によるセッション認証、Gmail SMTP によるメール認証 |
| データアクセス | **JDBC**（Tomcat JDBC コネクションプール経由の MySQL Connector/J。すべてのクエリで `PreparedStatement` を使用し、共通クエリは `dao/` に配置） |
| データベース | MySQL（`karuru_db`） |
| フロントエンド | JSP + **Bootstrap 5.3.2**（`includes/header.jsp` で CDN から読み込み、全ページ共通）+ Bootstrap Icons + ページごとの素の JavaScript（`js/*.js`）、管理画面の分析に Chart.js |
| ビルド／デプロイ | Maven（`pom.xml`、ラッパー `./mvnw`）→ WAR を Tomcat 9 にデプロイ。m2e で Eclipse にインポート可能。テストは JUnit 5。 |

### 3. アーキテクチャ
<img width="1920" height="1080" alt="01" src="https://github.com/user-attachments/assets/f5e40046-e173-4e65-a605-8e322bcc203e" />

```
ブラウザ
  │  HTTP / WebSocket
  ▼
フィルタチェーン
  ├─ CsrfFilter      … クロスオリジンの POST/PUT/DELETE を拒否
  └─ SessionFilter   … 保護されたパスを制御（/dashboard.jsp、/admin/* など）
  （リクエスト／レスポンスの UTF-8 エンコーディングは META-INF/context.xml で設定）
  │
  ▼
Servlet レイヤー（34 個）── 1 機能 1 Servlet。JSON API は ApiServlet を継承
  ├─ 認証: Login / Register / Logout / ForgotPassword / ResetPassword / Verify
  ├─ 出品: Product / ProductDetails / CreateListing / Category / Search / Banner
  ├─ 取引: Cart / Checkout / Order / Payment / Offer / Rental / Wallet
  ├─ ソーシャル: Review / Messages / Notifications / Favorite / SavedSearches / RecentlyViewed
  ├─ ユーザー: Profile / SellerProfile / Settings / Garage / Dashboard / Upload
  └─ 管理者: Admin（ユーザー、操作ログ、CRUD）/ Analytics / HealthCheck
  │
  ▼
dao/* + util.DatabaseConnection ── db.properties から設定する Tomcat JDBC コネクションプール
  │
  ▼
MySQL（karuru_db）

並行して:
WebSocketManager が接続中の MessageWebSocket / NotificationWebSocket のセッションを管理し、
チャットメッセージや通知を接続中のクライアントへリアルタイムに送信します。
```

この構成についての補足:
- **フロントコントローラーはありません。** URL ごとに専用の Servlet があります。JSON API の Servlet は共通の `ApiServlet` を継承しており、`?action=` からハンドラーへの振り分け、ログイン／管理者チェック、DB 接続、エラーレスポンスを 1 か所で処理します。
- `db.properties` 自体は git の管理対象外で、コミットされているのは `db.properties.example` だけです。この種の設定ではよくあるやり方です。

### 4. 主な機能

- ユーザー登録、ログイン、パスワードリセット、メール認証（Gmail SMTP 経由）
- 出品（`CreateListingServlet`）、カテゴリ閲覧、検索・絞り込み（`Search`、`Category`）
- カート → チェックアウト → 注文 → 支払いの流れ（`Cart` → `Checkout` → `Order` → `Payment`）
- レンタル取引（`Rental`）と値下げ交渉のオファー（`Offer`）
- アプリ内ウォレットの残高管理（`Wallet`）
- お気に入り、保存した検索条件、最近見た商品の履歴
- レビューと評価（`Review`）
- WebSocket によるリアルタイムのメッセージと通知（`MessagesServlet` + `MessageWebSocket`、`NotificationsServlet` + `NotificationWebSocket`）
- 出品者・購入者のプロフィール、アカウント設定（`SellerProfile`、`Profile`、`Settings`）
- 管理者ダッシュボード: ユーザー管理、Chart.js による売上分析、バナー管理
- 簡単な死活監視用のヘルスチェックエンドポイント（`HealthCheckServlet`）

### 5. はじめかた

**前提条件**: JDK 17 以上、Tomcat 9、MySQL 8 / MariaDB 10.6 以上。リポジトリに Maven Wrapper（`./mvnw`）が含まれているので、Maven のインストールは不要です。

```bash
# 1. クローン
git clone https://github.com/Kodokthegr3at/Karuru.git
cd Karuru

# 2. データベースを作成（スキーマ + 初期カテゴリ）
mysql -u root -p < db/schema.sql
mysql -u root -p karuru_db < db/seed.sql

# 3. 設定（どちらも git 管理外。無いとアプリは起動しません）
cp src/main/resources/db.properties.example    src/main/resources/db.properties
cp src/main/resources/email.properties.example src/main/resources/email.properties
# DB の認証情報と Gmail のアプリパスワードを記入

# 4. ビルド + テスト実行
./mvnw package          # → target/KaruruFleaMarket.war

# 5a. デプロイ: WAR を Tomcat の webapps/ にコピー、または
# 5b. Eclipse: ファイル > インポート > 既存 Maven プロジェクト、その後 サーバーで実行（Tomcat 9）

# 6. http://localhost:8085/KaruruFleaMarket を開く
#    管理者にするには: 登録後に  UPDATE users SET role='admin' WHERE username='あなたのユーザー名';
```

### 6. セキュリティに関する注意

- **認証情報**は git 管理外の `db.properties` / `email.properties` に置きます。設定が無い場合は起動時にデプロイが失敗します（`util.AppConfig`）。以前 `EmailConfig.java` にコミットされていた Gmail のアプリパスワードは git 履歴に残っているため、必ず無効化してください。
- **パスワード**: `PasswordUtils` 経由の BCrypt（コスト 12）。ログイン時にセッション ID を再発行します（セッション固定攻撃への対策）。
- **CSRF**: `util.CsrfFilter` がクロスオリジンの POST/PUT/DELETE を拒否し（Origin/Referer をチェック）、さらにセッション Cookie に `SameSite=Lax` と `HttpOnly` を設定しています（`META-INF/context.xml`、`web.xml`）。
- **WebSocket の認証**: ユーザーはハンドシェイク時にログイン済みの HTTP セッションから取得します（`websocket.SessionUserConfigurator`）。クエリ文字列の `?userId=` は使いません。
- **認可**: 管理者専用の操作（バナー管理、ユーザー一覧、管理者 CRUD）は `SessionFilter.isAdmin` でチェックします。管理者用の汎用 CRUD はテーブル名をホワイトリストで制限し、カラム名も検証します。どちらも SQL に直接連結されるためです。
- **ウォレット**: トランザクション内の残高読み取りには `SELECT … FOR UPDATE` を使うため、同時に出金や送金が行われても残高を超えて引き出されることはありません。
- **SQL インジェクション**: ユーザーからの値はすべて `PreparedStatement` を通します。
- **エラーレスポンス**には例外や SQL の詳細を含めず、それらはサーバーログにだけ出力します。データを変更する操作は POST/DELETE のみで GET では行わないため、すべて CSRF フィルタの対象になります。
- **ログ**にはパスワードリセットやメール認証のリンクを出力しません（リンクにトークンが含まれるため）。

### 7. プロジェクト構成

```
Karuru/
├── src/main/java/
│   ├── servlet/      … 機能ごとの Servlet 34 個 + 抽象基底クラス ApiServlet
│   ├── dao/          … 共通クエリ（注文、ウォレット、通知など）
│   ├── websocket/     … メッセージと通知用の WebSocket エンドポイント
│   └── util/           … DB 接続、パスワードのハッシュ化、フィルタ、メール設定
├── src/main/resources/
│   └── db.properties.example, email.properties.example
├── src/test/java/      … JUnit 5 テスト
├── db/                 … schema.sql, seed.sql
├── src/main/webapp/
│   ├── *.jsp            … 各ページの JSP ビュー
│   ├── admin/          … 管理画面
│   ├── error/           … 404 / 500 エラーページ
│   ├── img/ images/    … 静的ファイル
│   └── WEB-INF/web.xml … フィルタとエラーページの設定
├── pom.xml, mvnw        … Maven ビルド（+ ラッパー）
├── .github/workflows/   … CI: ビルド + テスト、MySQL 8.4 へのスキーマ読み込み
├── .classpath / .project … Eclipse（m2e + WTP）のメタデータ
└── README.md, README.id.md, README.ja.md
```

---

## 作者

- メンテナー: `kodoktheGr3at`
- GitHub: [github.com/Kodokthegr3at](https://github.com/Kodokthegr3at)
- リポジトリ: [github.com/Kodokthegr3at/Karuru](https://github.com/Kodokthegr3at/Karuru)

## ライセンス

このリポジトリにはライセンスファイルが含まれていません。`LICENSE` ファイルが追加されるまでは、コードは all-rights-reserved として扱ってください。
