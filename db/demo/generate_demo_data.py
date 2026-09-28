#!/usr/bin/env python3
"""
Realistic demo data for a local Karuru database: 30 sellers and 100 listings per category (1,200 in all),
with prices, conditions, discounts, sold items, rentals, view/like counts and reviews.

    python3 db/demo/generate_demo_data.py > /tmp/demo.sql
    mysql -u root -p karuru_db < /tmp/demo.sql

The output is deterministic (fixed seed). Every demo seller's email ends in @demo.karuru.local and their
password can never match, so the accounts cannot be logged into. To remove all demo data (products,
images, reviews and favourites cascade from the sellers):

    DELETE FROM users WHERE email LIKE '%@demo.karuru.local';

Run against a development database only.
"""
import random
from datetime import datetime, timedelta

rng = random.Random(42)
NOW = datetime(2026, 9, 29, 12, 0, 0)
DOMAIN = "demo.karuru.local"

# (handle, full name) of the demo sellers
SELLERS = [
    ("haruka_closet", "佐藤 遥"), ("kenta_garage", "鈴木 健太"), ("yui.room", "高橋 結衣"),
    ("daiki_gear", "田中 大輝"), ("misaki_shop", "伊藤 美咲"), ("sho_camera", "渡辺 翔"),
    ("aoi_books", "山本 葵"), ("ryo_sports", "中村 涼"), ("nana_baby", "小林 菜々"),
    ("takumi_tech", "加藤 拓海"), ("mei_handmade", "吉田 芽衣"), ("yuto_outdoor", "山田 悠斗"),
    ("sakura_beauty", "佐々木 さくら"), ("kaito_hobby", "山口 海斗"), ("rin_interior", "松本 凛"),
    ("sota_bike", "井上 颯太"), ("hina_zakka", "木村 陽菜"), ("ren_audio", "林 蓮"),
    ("mio_fashion", "清水 美桜"), ("yamato_car", "山崎 大和"), ("koharu_kids", "森 心春"),
    ("hayato_game", "池田 隼人"), ("tsumugi_craft", "橋本 紬"), ("minato_kaden", "阿部 湊"),
    ("akari_cosme", "石川 明里"), ("itsuki_books", "前田 樹"), ("ema_vintage", "藤田 恵麻"),
    ("riku_fishing", "後藤 陸"), ("chihiro_home", "岡田 千尋"), ("sora_select", "長谷川 空"),
]

# Per category slug: (base item, low price, high price, local photo or None, rentable)
CATALOG = {
    "fashion": [
        ("ユニクロ ウルトラライトダウンジャケット", 1800, 4500, None, False),
        ("ノースフェイス ヌプシジャケット", 18000, 32000, None, False),
        ("チャンピオン リバースウィーブ パーカー", 3500, 8000, "/img/products/hoodie.jpg", False),
        ("ナイキ エアマックス90", 6000, 14000, None, False),
        ("アディダス スタンスミス", 4500, 9000, None, False),
        ("リーバイス 501 デニム", 3000, 9500, None, False),
        ("コーチ ショルダーバッグ", 7000, 18000, None, False),
        ("無印良品 オーガニックコットン シャツ", 900, 2200, None, False),
        ("ビームス ウールコート", 8000, 20000, None, False),
        ("ニューバランス 996", 6500, 13000, None, False),
        ("GU ワイドパンツ", 600, 1800, None, False),
        ("ラルフローレン ポロシャツ", 2500, 6000, None, False),
    ],
    "electronics": [
        ("iPhone 14 128GB SIMフリー", 62000, 85000, "/img/products/iphone14.jpg", False),
        ("MacBook Air M1 8GB/256GB", 68000, 88000, "/img/products/macbook.jpg", False),
        ("ソニー WH-1000XM4 ワイヤレスヘッドホン", 17000, 26000, "/images/headphones_1.jpg", True),
        ("Canon EOS R6 ボディ", 150000, 210000, "/img/products/camera.jpg", True),
        ("iPad 第9世代 64GB Wi-Fi", 28000, 38000, None, False),
        ("AirPods Pro 第2世代", 18000, 27000, None, False),
        ("Anker モバイルバッテリー 20000mAh", 2200, 4500, None, False),
        ("Kindle Paperwhite 第11世代", 9000, 14000, None, False),
        ("ソニー α6400 レンズキット", 72000, 98000, "/img/products/sony.jpg", True),
        ("Google Pixel 7a", 32000, 45000, None, False),
        ("Apple Watch Series 8 GPS 41mm", 26000, 38000, None, False),
        ("ロジクール MX Master 3S マウス", 8000, 12000, None, False),
    ],
    "home-appliances": [
        ("ダイソン V8 コードレス掃除機", 18000, 30000, None, False),
        ("バルミューダ ザ・トースター", 14000, 22000, None, False),
        ("シャープ プラズマクラスター 空気清浄機", 9000, 20000, None, True),
        ("パナソニック ナノケア ドライヤー", 12000, 22000, None, False),
        ("象印 圧力IH炊飯器 5.5合", 15000, 30000, None, False),
        ("アイリスオーヤマ 電子レンジ", 5000, 11000, None, False),
        ("デロンギ 全自動コーヒーマシン", 38000, 65000, None, False),
        ("ルンバ i3", 25000, 40000, None, True),
        ("山善 サーキュレーター", 2000, 4500, None, False),
        ("日立 ドラム式洗濯機", 60000, 110000, None, False),
    ],
    "furniture": [
        ("IKEA POÄNG アームチェア", 5000, 11000, "/img/products/chair.jpg", False),
        ("無印良品 スタッキングシェルフ", 6000, 14000, None, False),
        ("ニトリ ソファベッド", 12000, 26000, None, False),
        ("カリモク60 Kチェア 1シーター", 38000, 62000, None, False),
        ("ゲーミングチェア リクライニング", 8000, 18000, "/images/gaming-chair_1.jpg", False),
        ("無垢材 ダイニングテーブル 4人用", 18000, 45000, None, False),
        ("フランフラン フロアランプ", 4000, 9000, None, False),
        ("ウォールナット ローテーブル", 7000, 16000, None, False),
        ("電動昇降デスク 120cm", 18000, 32000, None, False),
        ("北欧風 ラグ 200x140", 3000, 8000, None, False),
    ],
    "books": [
        ("ONE PIECE 1〜105巻 全巻セット", 18000, 30000, None, False),
        ("呪術廻戦 1〜26巻 セット", 6000, 11000, None, False),
        ("村上春樹 ノルウェイの森 上下 文庫", 500, 900, None, False),
        ("TOEIC公式問題集 10 セット", 1800, 3000, None, False),
        ("リーダブルコード", 1200, 1900, None, False),
        ("嫌われる勇気", 700, 1200, None, False),
        ("週刊少年ジャンプ 2024年 合本", 1500, 4000, None, False),
        ("ハリー・ポッター 全7巻 ハードカバー", 5000, 9000, None, False),
        ("料理本 きょうの料理 まとめ売り", 800, 2000, None, False),
        ("宅建士 テキスト＆問題集 2026年版", 1500, 2800, None, False),
    ],
    "hobby": [
        ("Nintendo Switch 有機ELモデル", 28000, 36000, "/img/products/switch.jpg", True),
        ("PlayStation 5 ディスクドライブ版", 52000, 68000, "/img/products/ps5.jpg", True),
        ("DJI Mini 3 ドローン", 55000, 78000, "/img/products/drone.jpg", True),
        ("ポケモンカード 未開封 BOX", 5000, 12000, None, False),
        ("ガンプラ MG ガンダム 未組立", 3500, 9000, None, False),
        ("レゴ クリエイター エキスパート", 9000, 22000, None, False),
        ("ヤマハ アコースティックギター FG830", 22000, 34000, None, True),
        ("ワンピース フィギュア 一番くじ", 2000, 6000, None, False),
        ("スプラトゥーン3 Switchソフト", 3800, 5200, "/images/switch_1.jpg", False),
        ("キャンプ用 焚き火台", 4000, 9000, None, True),
    ],
    "sports": [
        ("ゴルフクラブ キャロウェイ アイアンセット", 25000, 55000, None, True),
        ("テーラーメイド ドライバー", 12000, 28000, None, True),
        ("ヨネックス バドミントンラケット", 6000, 14000, None, False),
        ("ナイキ サッカースパイク", 5000, 11000, None, False),
        ("ヨガマット 6mm", 1200, 3000, None, False),
        ("可変式ダンベル 24kg ペア", 12000, 22000, None, True),
        ("スノーボード 3点セット", 15000, 38000, None, True),
        ("アシックス ランニングシューズ", 5000, 11000, None, False),
        ("テニスラケット ウィルソン", 7000, 16000, None, False),
        ("ロードバイク用 ヘルメット", 3500, 9000, None, False),
    ],
    "beauty": [
        ("SK-II フェイシャルトリートメントエッセンス 230ml", 12000, 18000, None, False),
        ("ランコム ジェニフィック 50ml", 7000, 11000, None, False),
        ("シャネル N°5 オードゥパルファム 50ml", 9000, 15000, None, False),
        ("パナソニック スチーマー ナノケア", 12000, 22000, None, False),
        ("リファ カラット", 8000, 16000, None, False),
        ("デパコス アイシャドウパレット セット", 3000, 7000, None, False),
        ("ドライヤー ReFa ビューテック", 18000, 26000, None, False),
        ("ヘアアイロン ストレート 32mm", 3000, 7000, None, False),
        ("イニスフリー スキンケア 詰め合わせ", 1500, 4000, None, False),
        ("ディオール リップ マキシマイザー", 2500, 4200, None, False),
    ],
    "baby": [
        ("サイベックス ベビーカー メリオ", 25000, 42000, None, True),
        ("コンビ チャイルドシート", 12000, 28000, None, True),
        ("エルゴベビー 抱っこ紐 OMNI", 9000, 17000, None, False),
        ("ストッケ トリップトラップ", 15000, 26000, None, False),
        ("ベビーベッド 折りたたみ", 5000, 12000, None, True),
        ("子供服 80cm まとめ売り", 1500, 4000, None, False),
        ("アンパンマン 知育おもちゃ", 1500, 4500, None, False),
        ("バウンサー ベビービョルン", 9000, 16000, None, True),
        ("レゴ デュプロ 大きなバケツ", 2500, 5000, None, False),
        ("ランドセル 天使のはね", 12000, 28000, None, False),
    ],
    "handcraft": [
        ("ハンドメイド レザー 長財布", 4500, 12000, None, False),
        ("手編み ニット帽", 1500, 3800, None, False),
        ("天然石 ブレスレット", 1800, 5000, None, False),
        ("陶器 マグカップ 手作り", 1800, 4500, None, False),
        ("刺繍 トートバッグ", 2500, 6000, None, False),
        ("レジン アクセサリー ピアス", 900, 2500, None, False),
        ("木製 カッティングボード", 2500, 6500, None, False),
        ("キャンドル アロマ 3個セット", 1500, 3500, None, False),
        ("布マスク ガーゼ 5枚", 800, 2000, None, False),
        ("押し花 スマホケース", 2000, 4500, None, False),
    ],
    "daily": [
        ("ル・クルーゼ ココット・ロンド 20cm", 15000, 26000, None, False),
        ("ティファール 取っ手のとれる 鍋セット", 4500, 9000, None, False),
        ("象印 ステンレスマグ 480ml", 1500, 3000, None, False),
        ("無印良品 収納ケース 3段", 2000, 5000, None, False),
        ("ストウブ ピコ・ココット 22cm", 14000, 22000, None, False),
        ("今治タオル バスタオル 3枚セット", 2500, 6000, None, False),
        ("折りたたみ傘 軽量", 1200, 3000, None, False),
        ("防災リュック 1人用セット", 5000, 10000, None, False),
        ("布団乾燥機 アイリスオーヤマ", 5000, 9000, None, True),
        ("スーツケース Mサイズ", 6000, 18000, None, True),
    ],
    "car": [
        ("クロスバイク GIANT ESCAPE R3", 28000, 48000, "/img/products/bike.jpg", True),
        ("電動アシスト自転車 パナソニック", 55000, 95000, None, True),
        ("ルーフボックス THULE", 30000, 58000, None, True),
        ("ドライブレコーダー 前後カメラ", 8000, 18000, None, False),
        ("スタッドレスタイヤ 195/65R15 4本", 22000, 48000, None, False),
        ("バイク用 フルフェイスヘルメット SHOEI", 22000, 42000, None, False),
        ("ETC車載器", 3000, 7000, None, False),
        ("カーナビ パナソニック ストラーダ", 18000, 38000, None, False),
        ("ジュニアシート 車用", 3000, 8000, None, True),
        ("キャンプ用 カーサイドタープ", 8000, 18000, None, True),
    ],
}

VARIANTS = {
    "fashion": ["Sサイズ", "Mサイズ", "Lサイズ", "ブラック", "ネイビー", "ベージュ", "グレー", "ホワイト", "27.0cm", "26.5cm"],
    "electronics": ["ブラック", "シルバー", "ホワイト", "本体のみ", "箱付き", "ケース付き", "2023年購入", "バッテリー良好"],
    "home-appliances": ["ホワイト", "ブラック", "2022年製", "2023年製", "2024年製", "取説付き"],
    "furniture": ["ナチュラル", "ブラウン", "ホワイト", "組立済み", "引き取り限定", "分解発送可"],
    "books": ["初版", "帯付き", "美本", "まとめ売り", "書き込みなし"],
    "hobby": ["箱付き", "付属品完備", "美品", "未開封", "動作確認済み"],
    "sports": ["メンズ", "レディース", "ケース付き", "数回使用", "サイズ26cm"],
    "beauty": ["未開封", "残量9割", "残量8割", "箱付き", "限定色"],
    "baby": ["美品", "洗濯済み", "取説付き", "2023年購入", "男女兼用"],
    "handcraft": ["一点物", "オーダー可", "ギフト包装可", "ネイビー", "ナチュラル"],
    "daily": ["新品未使用", "ブルー", "レッド", "グレー", "箱なし"],
    "car": ["2022年モデル", "2023年モデル", "取付説明書付き", "室内保管", "引き取り歓迎"],
}

CONDITIONS = [("new", 12), ("like_new", 30), ("good", 40), ("fair", 18)]
CONDITION_TEXT = {
    "new": "新品・未使用です。購入後そのまま保管していました。",
    "like_new": "数回使用しただけで、目立つ傷や汚れはありません。",
    "good": "使用に伴う小さな傷はありますが、動作・使用に問題はありません。",
    "fair": "使用感や傷があります。写真をよくご確認のうえご購入ください。",
}
SHIPPING = ["送料込みです。", "匿名配送で発送します。", "2〜3日以内に発送します。", "丁寧に梱包して発送します。", "近隣の方は手渡しも可能です。"]
REASONS = ["引っ越しのため出品します。", "買い替えのため出品します。", "使う機会が減ったため出品します。", "サイズが合わなかったため出品します。", "", ""]
REVIEWS = [
    (5, "丁寧な梱包で、説明どおりの綺麗な状態でした。ありがとうございました。"),
    (5, "迅速な発送でした。また機会があればよろしくお願いします。"),
    (5, "写真より綺麗で満足しています。"),
    (4, "少し使用感はありましたが、問題なく使えています。"),
    (4, "やり取りがスムーズで安心して取引できました。"),
    (3, "発送まで少し時間がかかりましたが、商品は問題ありませんでした。"),
]


def q(text):
    """SQL string literal."""
    return "'" + str(text).replace("\\", "\\\\").replace("'", "''") + "'"


def ts(dt):
    return q(dt.strftime("%Y-%m-%d %H:%M:%S"))


def yen(value):
    step = 100 if value >= 1000 else 10
    return int(round(value / step) * step)


def weighted(choices):
    return rng.choices([c for c, _ in choices], weights=[w for _, w in choices])[0]


out = ["-- Generated by db/demo/generate_demo_data.py. Remove with:",
       f"-- DELETE FROM users WHERE email LIKE '%@{DOMAIN}';",
       "SET NAMES utf8mb4;", "START TRANSACTION;"]

# Sellers. "!" is not a bcrypt hash, so no password can ever match.
for i, (handle, name) in enumerate(SELLERS):
    joined = NOW - timedelta(days=rng.randint(120, 540), hours=rng.randint(0, 23))
    out.append(
        "INSERT INTO users (username, email, password_hash, full_name, role, is_verified, verified_at, is_seller, created_at) "
        f"VALUES ({q(handle)}, {q(handle.replace('.', '_') + '@' + DOMAIN)}, '!', {q(name)}, 'user', 1, {ts(joined)}, 1, {ts(joined)});")
    out.append(f"SET @s{i} = LAST_INSERT_ID();")

for slug, items in CATALOG.items():
    out.append(f"SELECT category_id INTO @cat FROM categories WHERE slug = {q(slug)};")
    seen = set()
    for n in range(100):
        base, low, high, photo, rentable = items[n % len(items)]
        title = base
        while title in seen:
            title = f"{base} {rng.choice(VARIANTS[slug])}"
            if title in seen:
                title = f"{base} {rng.choice(VARIANTS[slug])} {rng.choice(['美品', '送料込み', '即購入OK', '値下げしました'])}"
        seen.add(title)

        condition = weighted(CONDITIONS)
        # Newer-looking items sit at the top of the range
        spread = {"new": 0.9, "like_new": 0.7, "good": 0.45, "fair": 0.2}[condition]
        price = yen(low + (high - low) * min(1, max(0, rng.gauss(spread, 0.15))))
        original = yen(price / rng.uniform(0.55, 0.85)) if rng.random() < 0.4 else None
        discount = round((1 - price / original) * 100) if original else 0
        rental = rentable and rng.random() < 0.3
        daily = yen(max(500, price * rng.uniform(0.02, 0.04))) if rental else None
        created = NOW - timedelta(days=rng.randint(0, 120), hours=rng.randint(0, 23), minutes=rng.randint(0, 59))
        views = int(rng.paretovariate(1.3) * 25)
        likes = int(views * rng.uniform(0.02, 0.12))
        sold = not rental and rng.random() < 0.12
        seller = rng.randrange(len(SELLERS))
        description = " ".join(filter(None, [
            f"{base}です。", rng.choice(REASONS), CONDITION_TEXT[condition],
            "値下げ交渉にも対応します。" if not rental and rng.random() < 0.4 else "",
            "レンタルは1日単位でご利用いただけます。デポジットは返却確認後に返金します。" if rental else rng.choice(SHIPPING),
        ]))

        out.append(
            "INSERT INTO products (user_id, product_name, description, price, original_price, discount_percentage, `condition`, "
            "stock_quantity, image_url, status, is_negotiable, is_rental, rental_price_daily, rental_price_weekly, rental_deposit, "
            "featured, views_count, likes_count, sold_count, created_at, updated_at) VALUES ("
            f"@s{seller}, {q(title)}, {q(description)}, {0 if rental else price}, {original or 'NULL'}, {discount}, {q(condition)}, "
            f"1, {q(photo or f'/img/categories/{slug}.png')}, {q('sold' if sold else 'available')}, "
            f"{1 if (not rental and '値下げ交渉' in description) else 0}, {1 if rental else 0}, "
            f"{daily or 'NULL'}, {yen(daily * 5.5) if rental else 'NULL'}, {yen(price * 0.3) if rental else 'NULL'}, "
            f"{1 if rng.random() < 0.04 else 0}, {views}, {likes}, {1 if sold else 0}, {ts(created)}, {ts(created)});")
        out.append("SET @p = LAST_INSERT_ID();")
        out.append("INSERT INTO product_categories (product_id, category_id) VALUES (@p, @cat);")
        out.append(f"INSERT INTO product_images (product_id, image_url, image_order, is_primary) VALUES (@p, {q(photo or f'/img/categories/{slug}.png')}, 0, 1);")

        if rng.random() < 0.3:
            reviewers = rng.sample([i for i in range(len(SELLERS)) if i != seller], rng.randint(1, 4))
            for r in reviewers:
                rating, text = rng.choice(REVIEWS)
                when = created + timedelta(days=rng.randint(3, 20))
                out.append(
                    "INSERT INTO product_reviews (product_id, user_id, rating, review_text, is_verified_purchase, created_at, updated_at) "
                    f"VALUES (@p, @s{r}, {rating}, {q(text)}, 1, {ts(min(when, NOW))}, {ts(min(when, NOW))});")
            out.append("UPDATE products SET rating_avg = (SELECT AVG(rating) FROM product_reviews WHERE product_id = @p), "
                       "rating_count = (SELECT COUNT(*) FROM product_reviews WHERE product_id = @p) WHERE product_id = @p;")

out.append("COMMIT;")
print("\n".join(out))
