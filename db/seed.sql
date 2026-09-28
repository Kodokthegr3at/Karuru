-- Initial data. Run after schema.sql:
--   mysql -u root -p karuru_db < db/seed.sql
-- Slugs match the images in src/main/webapp/img/categories/.

INSERT INTO categories (category_name, slug, display_order) VALUES
    ('ファッション',   'fashion',         1),
    ('家電・スマホ',   'electronics',     2),
    ('生活家電',       'home-appliances', 3),
    ('家具・インテリア', 'furniture',     4),
    ('本・雑誌',       'books',           5),
    ('ホビー・おもちゃ', 'hobby',         6),
    ('スポーツ',       'sports',          7),
    ('コスメ・美容',   'beauty',          8),
    ('ベビー・キッズ', 'baby',            9),
    ('ハンドメイド',   'handcraft',      10),
    ('日用品',         'daily',          11),
    ('車・バイク',     'car',            12);

-- To make an admin: register normally in the app, then
--   UPDATE users SET role = 'admin' WHERE username = 'your_username';
