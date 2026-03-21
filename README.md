# Karuru 🛍️

Karuruは、「購入」と「レンタル」の両方に対応したフリーマーケット型ECプラットフォームです。  
ユーザーは商品を購入するだけでなく、レンタルという選択肢を通じて、用途や予算に応じた柔軟な取引を行うことができます。

---

## 📌 特徴（Features）

- 購入・レンタル両対応のECシステム
- 価格交渉（オファー）機能
- リアルタイムチャット（WebSocket）
- 通知機能（リアルタイム）
- MVC構成による設計

---

## 🚀 主な機能（Main Functions）

- 商品出品・編集・削除
- 商品購入機能
- レンタル申請・管理機能
- 価格交渉（オファー）機能
- リアルタイムチャット機能
- ユーザー登録・ログイン認証
- マイページ管理
- 通知機能

---

## 🏗️ システム構成（Architecture）

本プロジェクトはMVCモデルを採用しています。

- **Model**：データ管理（DB連携）
- **View**：JSPによる画面表示
- **Controller**：Servletによる処理制御

また、WebSocketを使用してリアルタイム通信を実現しています。

---

## 🛠️ 使用技術（Tech Stack）

- Java (Servlet / JSP)
- HTML / CSS / JavaScript
- WebSocket
- MySQL（または使用DB）
- Apache Tomcat

---

## 💡 コンセプト（Concept）

「買う」だけでなく「借りる」という選択肢を提供し、  
より自由で実用的なEC体験を実現する。

---

## 🎯 セールスポイント（Selling Points）

- 購入×レンタル対応の柔軟な取引設計
- ユーザー主導の価格交渉機能
- リアルタイム通信による快適なUX
- 実運用を意識したMVC設計

---

## 📷 画面イメージ（Screenshots）
<img width="1920" height="1080" alt="Blue and Beige Simple Project Proposal Presentation (1)" src="https://github.com/user-attachments/assets/e1342611-4e5c-4125-a9d1-e827afdbd9ec" />
<img width="1919" height="872" alt="スクリーンショット 2026-02-03 172738" src="https://github.com/user-attachments/assets/87a57186-87a5-4378-9edb-586c29c664f9" /><img width="1914" height="1904" alt="image" src="https://github.com/user-attachments/assets/dc224730-ca01-465c-bd62-5ceeb21d5da8" />
<img width="2160" height="1333" alt="image (1)" src="https://github.com/user-attachments/assets/8cd9e13e-d09c-44fd-a8c6-581d87e10e7b" />
<img width="1920" height="1080" alt="Blue and Beige Simple Project Proposal Presentation" src="https://github.com/user-attachments/assets/b1f59dd7-bf68-4744-b1ff-d7689a196d06" />

---



## ⚙️ セットアップ方法（Setup）

```bash
# リポジトリをクローン
git clone https://github.com/your-username/karuru.git
```

-Eclipseでインポート
```bash
「File」→「Import」

「Existing Projects into Workspace」を選択

クローンしたフォルダを指定
```
-Tomcat 9の設定
```bash
「Window」→「Preferences」→「Server」→「Runtime Environments」

「Add」→「Apache Tomcat v9.0」を選択

Tomcatのインストール先を指定

サーバーにプロジェクトを追加

「Servers」タブで右クリック → 「New」→「Server」
```
-Tomcat 9を選択
```bash
「Karuru」を追加

実行

サーバーを右クリック → 「Start」

またはプロジェクトを右クリック → 「Run on Server」
```
## 🌐 アクセス

ブラウザで以下にアクセス：
```bash
http://localhost:8085/KaruruFleaMarket
```
## 🔧 工夫した点

1. プロジェクト構成を整理し、見やすく設計

2. エラー発生時に自ら調査し、解決力を向上

3. フロントエンドとバックエンドの連携を意識した開発

---

## 🚧 開発中の課題

1. UI/UXのさらなる改善

2. 出品・購入機能の拡張

3. パフォーマンス最適化

---

## 📈 今後の展望

1. リアルタイム通知機能の追加

2. より多くのユーザーが使いやすいUI設計

3. AI技術を活用した便利機能の実装

---

## 📚 学んだこと

1. Webアプリケーション開発の全体の流れ

2. トラブルシューティング能力

3. 自ら調べて問題を解決する力

4. 継続的な改善の重要性

---

## 👤 作成者

GitHub: https://github.com/Kodokthegr3at

Karuruプロジェクトを通して、エンジニアとしての基礎力と問題解決力を身につけました。
今後も継続して改善・開発を進めていきます。
