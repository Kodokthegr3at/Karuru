-- Karuru Flea Market — database schema (MySQL 8 / MariaDB 10.6+)
-- Usage:
--   mysql -u root -p < db/schema.sql
-- Derived from the queries in src/main/java; every query in the code is validated against it.

CREATE DATABASE IF NOT EXISTS karuru_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE karuru_db;

CREATE TABLE users (
    user_id            INT AUTO_INCREMENT PRIMARY KEY,
    username           VARCHAR(50)  NOT NULL UNIQUE,
    email              VARCHAR(255) NOT NULL UNIQUE,
    password_hash      VARCHAR(255) NOT NULL,
    full_name          VARCHAR(100),
    phone              VARCHAR(20),
    avatar_url         VARCHAR(500),
    bio                TEXT,
    role               VARCHAR(20)  NOT NULL DEFAULT 'user',
    is_verified        BOOLEAN      NOT NULL DEFAULT FALSE,
    is_seller          BOOLEAN      NOT NULL DEFAULT FALSE,
    verification_token VARCHAR(255),
    verified_at        TIMESTAMP NULL,
    reset_token        VARCHAR(255),
    reset_token_expiry TIMESTAMP NULL,
    login_attempts     INT          NOT NULL DEFAULT 0,
    locked_until       TIMESTAMP NULL,
    last_login         TIMESTAMP NULL,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at         TIMESTAMP NULL,
    INDEX idx_users_verification_token (verification_token),
    INDEX idx_users_reset_token (reset_token)
) ENGINE=InnoDB;

CREATE TABLE categories (
    category_id   INT AUTO_INCREMENT PRIMARY KEY,
    parent_id     INT NULL,
    category_name VARCHAR(100) NOT NULL,
    slug          VARCHAR(120) UNIQUE,
    description   TEXT,
    icon_url      VARCHAR(500),
    image_url     VARCHAR(500),
    display_order INT     NOT NULL DEFAULT 0,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (parent_id) REFERENCES categories(category_id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE products (
    product_id           INT AUTO_INCREMENT PRIMARY KEY,
    user_id              INT            NOT NULL,
    product_name         VARCHAR(200)   NOT NULL,
    slug                 VARCHAR(250),
    description          TEXT,
    price                DECIMAL(12,2)  NOT NULL DEFAULT 0,
    original_price       DECIMAL(12,2),
    discount_percentage  INT            NOT NULL DEFAULT 0,
    `condition`          VARCHAR(20),
    stock_quantity       INT            NOT NULL DEFAULT 1,
    min_order            INT            NOT NULL DEFAULT 1,
    weight               DECIMAL(10,2),
    image_url            VARCHAR(500),
    status               VARCHAR(20)    NOT NULL DEFAULT 'available',
    is_negotiable        BOOLEAN        NOT NULL DEFAULT FALSE,
    is_rental            BOOLEAN        NOT NULL DEFAULT FALSE,
    rental_price_daily   DECIMAL(12,2),
    rental_price_weekly  DECIMAL(12,2),
    rental_price_monthly DECIMAL(12,2),
    rental_deposit       DECIMAL(12,2),
    featured             BOOLEAN        NOT NULL DEFAULT FALSE,
    featured_until       TIMESTAMP NULL,
    views_count          INT            NOT NULL DEFAULT 0,
    likes_count          INT            NOT NULL DEFAULT 0,
    sold_count           INT            NOT NULL DEFAULT 0,
    rating_avg           DECIMAL(3,2)   NOT NULL DEFAULT 0,
    rating_count         INT            NOT NULL DEFAULT 0,
    created_at           TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_products_status_created (status, created_at),
    FULLTEXT INDEX ft_products_search (product_name, description)
) ENGINE=InnoDB;

CREATE TABLE product_categories (
    product_id  INT NOT NULL,
    category_id INT NOT NULL,
    PRIMARY KEY (product_id, category_id),
    FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE,
    FOREIGN KEY (category_id) REFERENCES categories(category_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE product_images (
    image_id    INT AUTO_INCREMENT PRIMARY KEY,
    product_id  INT          NOT NULL,
    image_url   VARCHAR(500) NOT NULL,
    image_order INT          NOT NULL DEFAULT 0,
    is_primary  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE product_specifications (
    spec_id       INT AUTO_INCREMENT PRIMARY KEY,
    product_id    INT          NOT NULL,
    spec_name     VARCHAR(100) NOT NULL,
    spec_value    VARCHAR(500),
    display_order INT          NOT NULL DEFAULT 0,
    FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE user_addresses (
    address_id     INT AUTO_INCREMENT PRIMARY KEY,
    user_id        INT          NOT NULL,
    address_label  VARCHAR(50),
    recipient_name VARCHAR(100) NOT NULL,
    phone          VARCHAR(20),
    postal_code    VARCHAR(10),
    country        VARCHAR(50)  NOT NULL DEFAULT 'Japan',
    prefecture     VARCHAR(50),
    city           VARCHAR(100),
    address_line1  VARCHAR(255),
    address_line2  VARCHAR(255),
    building_name  VARCHAR(255),
    is_default     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE carts (
    cart_id        INT AUTO_INCREMENT PRIMARY KEY,
    user_id        INT NOT NULL,
    product_id     INT NOT NULL,
    quantity       INT NOT NULL DEFAULT 1,
    price_snapshot DECIMAL(12,2),
    added_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_carts_user_product (user_id, product_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE orders (
    order_id            INT AUTO_INCREMENT PRIMARY KEY,
    order_number        VARCHAR(50)   NOT NULL UNIQUE,
    user_id             INT           NOT NULL,
    shipping_address_id INT NULL,
    subtotal            DECIMAL(12,2) NOT NULL DEFAULT 0,
    shipping_cost       DECIMAL(12,2) NOT NULL DEFAULT 0,
    tax_amount          DECIMAL(12,2) NOT NULL DEFAULT 0,
    discount_amount     DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_amount        DECIMAL(12,2) NOT NULL DEFAULT 0,
    payment_method      VARCHAR(30),
    payment_status      VARCHAR(20)   NOT NULL DEFAULT 'pending',
    order_status        VARCHAR(20)   NOT NULL DEFAULT 'pending',
    tracking_number     VARCHAR(100),
    courier             VARCHAR(100),
    notes               TEXT,
    paid_at             TIMESTAMP NULL,
    shipped_at          TIMESTAMP NULL,
    delivered_at        TIMESTAMP NULL,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    FOREIGN KEY (shipping_address_id) REFERENCES user_addresses(address_id) ON DELETE SET NULL,
    INDEX idx_orders_user_created (user_id, created_at)
) ENGINE=InnoDB;

CREATE TABLE order_items (
    item_id      INT AUTO_INCREMENT PRIMARY KEY,
    order_id     INT           NOT NULL,
    product_id   INT           NOT NULL,
    seller_id    INT           NOT NULL,
    product_name VARCHAR(200),
    quantity     INT           NOT NULL DEFAULT 1,
    price        DECIMAL(12,2) NOT NULL,
    subtotal     DECIMAL(12,2) NOT NULL,
    status       VARCHAR(20)   NOT NULL DEFAULT 'pending',
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(product_id),
    FOREIGN KEY (seller_id) REFERENCES users(user_id),
    INDEX idx_order_items_seller (seller_id)
) ENGINE=InnoDB;

CREATE TABLE offers (
    offer_id    INT AUTO_INCREMENT PRIMARY KEY,
    product_id  INT           NOT NULL,
    buyer_id    INT           NOT NULL,
    seller_id   INT           NOT NULL,
    offer_price DECIMAL(12,2) NOT NULL,
    message     TEXT,
    status      VARCHAR(20)   NOT NULL DEFAULT 'pending',
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE,
    FOREIGN KEY (buyer_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (seller_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE rentals (
    rental_id      INT AUTO_INCREMENT PRIMARY KEY,
    rental_number  VARCHAR(50)   NOT NULL UNIQUE,
    renter_id      INT           NOT NULL,
    owner_id       INT           NOT NULL,
    product_id     INT           NOT NULL,
    start_date     DATE          NOT NULL,
    end_date       DATE          NOT NULL,
    rental_price   DECIMAL(12,2) NOT NULL,
    total_amount   DECIMAL(12,2) NOT NULL,
    quantity       INT           NOT NULL DEFAULT 1,
    status         VARCHAR(20)   NOT NULL DEFAULT 'pending',
    payment_status VARCHAR(20)   NOT NULL DEFAULT 'pending',
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (renter_id) REFERENCES users(user_id),
    FOREIGN KEY (owner_id) REFERENCES users(user_id),
    FOREIGN KEY (product_id) REFERENCES products(product_id)
) ENGINE=InnoDB;

CREATE TABLE rental_deposits (
    deposit_id     INT AUTO_INCREMENT PRIMARY KEY,
    rental_id      INT           NOT NULL,
    deposit_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    deposit_status VARCHAR(20)   NOT NULL DEFAULT 'held',
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (rental_id) REFERENCES rentals(rental_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE product_reviews (
    review_id            INT AUTO_INCREMENT PRIMARY KEY,
    product_id           INT         NOT NULL,
    user_id              INT         NOT NULL,
    order_id             INT NULL,
    rating               TINYINT     NOT NULL CHECK (rating BETWEEN 1 AND 5),
    review_text          TEXT,
    status               VARCHAR(20) NOT NULL DEFAULT 'approved',
    is_verified_purchase BOOLEAN     NOT NULL DEFAULT FALSE,
    helpful_count        INT         NOT NULL DEFAULT 0,
    comment_count        INT         NOT NULL DEFAULT 0,
    seller_reply         TEXT,
    seller_replied_at    TIMESTAMP NULL,
    created_at           TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE review_comments (
    comment_id        INT AUTO_INCREMENT PRIMARY KEY,
    review_id         INT         NOT NULL,
    user_id           INT         NOT NULL,
    parent_comment_id INT NULL,
    comment_text      TEXT        NOT NULL,
    is_seller_reply   BOOLEAN     NOT NULL DEFAULT FALSE,
    status            VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (review_id) REFERENCES product_reviews(review_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (parent_comment_id) REFERENCES review_comments(comment_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE user_favorites (
    favorite_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id     INT NOT NULL,
    product_id  INT NOT NULL,
    added_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_favorites_user_product (user_id, product_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE messages (
    message_id     INT AUTO_INCREMENT PRIMARY KEY,
    sender_id      INT     NOT NULL,
    receiver_id    INT     NOT NULL,
    product_id     INT NULL,
    message_text   TEXT    NOT NULL,
    attachment_url VARCHAR(500),
    is_read        BOOLEAN NOT NULL DEFAULT FALSE,
    read_at        TIMESTAMP NULL,
    sent_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (sender_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (receiver_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE SET NULL,
    INDEX idx_messages_conversation (sender_id, receiver_id, sent_at)
) ENGINE=InnoDB;

CREATE TABLE notifications (
    notification_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id         INT          NOT NULL,
    type            VARCHAR(50)  NOT NULL,
    title           VARCHAR(200),
    message         TEXT,
    action_url      VARCHAR(500),
    reference_type  VARCHAR(50),
    reference_id    INT NULL,
    is_read         BOOLEAN      NOT NULL DEFAULT FALSE,
    read_at         TIMESTAMP NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_notifications_user_read (user_id, is_read)
) ENGINE=InnoDB;

CREATE TABLE user_wallets (
    wallet_id           INT AUTO_INCREMENT PRIMARY KEY,
    user_id             INT           NOT NULL UNIQUE,
    balance             DECIMAL(12,2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    frozen_balance      DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_earned        DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_spent         DECIMAL(12,2) NOT NULL DEFAULT 0,
    last_transaction_at TIMESTAMP NULL,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE wallet_transactions (
    transaction_id INT AUTO_INCREMENT PRIMARY KEY,
    wallet_id      INT           NOT NULL,
    type           VARCHAR(30)   NOT NULL,
    amount         DECIMAL(12,2) NOT NULL,
    balance_before DECIMAL(12,2),
    balance_after  DECIMAL(12,2),
    reference_type VARCHAR(50),
    reference_id   INT NULL,
    description    VARCHAR(500),
    status         VARCHAR(20)   NOT NULL DEFAULT 'completed',
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (wallet_id) REFERENCES user_wallets(wallet_id) ON DELETE CASCADE,
    INDEX idx_wallet_transactions_wallet_created (wallet_id, created_at)
) ENGINE=InnoDB;

CREATE TABLE vouchers (
    voucher_id     INT AUTO_INCREMENT PRIMARY KEY,
    voucher_code   VARCHAR(50)   NOT NULL UNIQUE,
    voucher_name   VARCHAR(100),
    discount_type  VARCHAR(20)   NOT NULL,
    discount_value DECIMAL(12,2) NOT NULL,
    min_purchase   DECIMAL(12,2) NOT NULL DEFAULT 0,
    max_discount   DECIMAL(12,2),
    usage_limit    INT NULL,
    used_count     INT           NOT NULL DEFAULT 0,
    valid_from     TIMESTAMP NULL,
    valid_until    TIMESTAMP NULL,
    is_active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE banners (
    banner_id     INT AUTO_INCREMENT PRIMARY KEY,
    title         VARCHAR(200),
    image_url     VARCHAR(500) NOT NULL,
    link_url      VARCHAR(500),
    position      VARCHAR(50)  NOT NULL DEFAULT 'home',
    display_order INT          NOT NULL DEFAULT 0,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    start_date    TIMESTAMP NULL,
    end_date      TIMESTAMP NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE activity_logs (
    log_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     INT NULL,
    action      VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50),
    entity_id   INT NULL,
    ip_address  VARCHAR(45),
    details     TEXT,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE SET NULL,
    INDEX idx_activity_user_action (user_id, action, created_at)
) ENGINE=InnoDB;
