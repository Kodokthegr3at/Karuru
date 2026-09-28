// Shared helpers loaded on every page (see includes/header.jsp).

window.KaruruUtils = window.KaruruUtils || {};

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text == null ? '' : String(text);
    return div.innerHTML;
}

KaruruUtils.CONDITIONS = { new: '新品', like_new: '新品同様', good: '良好', fair: '可' };

/** [label, tone] per status; the tone picks the .status-* colour. */
KaruruUtils.ORDER_STATUS = {
    pending: ['確認待ち', 'warning'],
    confirmed: ['確定済み', 'info'],
    processing: ['発送準備中', 'info'],
    shipped: ['発送済み', 'info'],
    delivered: ['お届け済み', 'success'],
    completed: ['取引完了', 'success'],
    cancelled: ['キャンセル', 'danger'],
    refunded: ['返金済み', '']
};

KaruruUtils.PAYMENT_STATUS = {
    pending: ['支払い待ち', 'warning'],
    paid: ['支払い済み', 'success'],
    failed: ['支払い失敗', 'danger'],
    refunded: ['返金済み', '']
};

KaruruUtils.OFFER_STATUS = {
    pending: ['返事待ち', 'warning'],
    accepted: ['承諾済み', 'success'],
    rejected: ['お断り', 'danger'],
    cancelled: ['取り消し', '']
};

KaruruUtils.PAYMENT_METHODS = {
    wallet: 'ウォレット残高',
    credit_card: 'クレジットカード',
    ewallet: '電子マネー',
    bank_transfer: '銀行振込',
    cod: '代金引換'
};

/** <span class="status status-…">label</span> for a value of one of the maps above. */
KaruruUtils.statusBadge = function(map, value) {
    const [label, tone] = map[value] || [value || '-', ''];
    return `<span class="status${tone ? ' status-' + tone : ''}">${escapeHtml(label)}</span>`;
};

KaruruUtils.formatDateTime = function(value) {
    const date = KaruruUtils.parseDate(value);
    return !date ? '' : date.toLocaleString('ja-JP', {
        year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit'
    });
};

KaruruUtils.formatPrice = function(amount) {
    const value = typeof amount === 'string' ? parseFloat(amount) : amount;
    const number = Number.isFinite(value) ? value : 0;
    return (number < 0 ? '-¥' : '¥') + Math.abs(number).toLocaleString('ja-JP');
};

/**
 * Date from an API value. Most endpoints send ISO-8601; a few still send java.sql.Timestamp#toString
 * ("2026-09-28 00:32:01.0"), which only some browsers parse, so that form is normalised first.
 */
KaruruUtils.parseDate = function(value) {
    if (!value) return null;
    const text = String(value).replace(/^(\d{4}-\d{2}-\d{2}) (\d{2}:\d{2}:\d{2})(\.\d+)?$/, '$1T$2');
    const date = new Date(text);
    return isNaN(date.getTime()) ? null : date;
};

/** Relative time for the last week, a plain date after that. */
KaruruUtils.formatDate = function(dateString) {
    const date = KaruruUtils.parseDate(dateString);
    if (!date) return dateString || '';

    const minutes = Math.floor((Date.now() - date) / 60000);
    if (minutes < 1) return 'たった今';
    if (minutes < 60) return `${minutes}分前`;
    if (minutes < 60 * 24) return `${Math.floor(minutes / 60)}時間前`;
    if (minutes < 60 * 24 * 7) return `${Math.floor(minutes / 60 / 24)}日前`;
    return date.toLocaleDateString('ja-JP', { year: 'numeric', month: 'long', day: 'numeric' });
};

KaruruUtils.showLoading = function(container) {
    if (!container) return;
    container.innerHTML = `
        <div class="col-12 text-center py-5">
            <div class="spinner-border text-primary" role="status">
                <span class="visually-hidden">読み込み中...</span>
            </div>
        </div>`;
};

/** Toast message. Errors stay longer; at most three toasts are shown at once. */
KaruruUtils.showNotification = function(message, type = 'info') {
    const kinds = {
        success: { cls: 'alert-success', icon: 'bi-check-circle' },
        error: { cls: 'alert-danger', icon: 'bi-exclamation-triangle' },
        danger: { cls: 'alert-danger', icon: 'bi-exclamation-triangle' },
        warning: { cls: 'alert-warning', icon: 'bi-exclamation-circle' },
        info: { cls: 'alert-info', icon: 'bi-info-circle' }
    };
    const kind = kinds[type] || kinds.info;

    let container = document.getElementById('toastContainer');
    if (!container) {
        container = document.createElement('div');
        container.id = 'toastContainer';
        container.className = 'toast-stack';
        container.setAttribute('aria-live', 'polite');
        document.body.appendChild(container);
    }
    // A result replaces the "processing..." info toast that preceded it.
    if (type !== 'info' && type !== 'warning') {
        container.querySelectorAll('.alert-info').forEach(el => el.remove());
    }
    while (container.children.length >= 3) {
        container.firstElementChild.remove();
    }

    const toast = document.createElement('div');
    toast.className = `toast-item alert ${kind.cls}`;
    toast.setAttribute('role', type === 'danger' || type === 'error' ? 'alert' : 'status');
    toast.innerHTML = `
        <i class="bi ${kind.icon}" aria-hidden="true"></i>
        <span class="toast-item-text">${escapeHtml(message)}</span>
        <button type="button" class="btn-close" aria-label="閉じる"></button>`;
    toast.querySelector('.btn-close').addEventListener('click', () => toast.remove());
    container.appendChild(toast);

    setTimeout(() => toast.remove(), kind.cls === 'alert-danger' ? 8000 : 5000);
};

/** login.jsp with a redirect back to the current page (a path inside the app, as LoginServlet expects). */
KaruruUtils.loginUrl = function() {
    const path = location.pathname.slice((window.CONTEXT_PATH || '').length) + location.search;
    return `${window.CONTEXT_PATH}/login.jsp?redirect=${encodeURIComponent(path)}`;
};

KaruruUtils.confirmDialog = function(message) {
    return confirm(message);
};

/**
 * fetch() that returns the parsed JSON body and throws an Error carrying the
 * server's message ({error} or {message}) for non-2xx responses.
 */
KaruruUtils.apiFetch = async function(url, options = {}) {
    const response = await fetch(url, {
        credentials: 'same-origin',
        ...options,
        headers: { 'Content-Type': 'application/json', ...options.headers }
    });
    const text = await response.text();
    let data = null;
    try {
        data = text ? JSON.parse(text) : null;
    } catch (e) {
        // Not JSON (e.g. Tomcat's HTML error page); handled below.
    }
    if (!response.ok) {
        throw new Error(data?.error || data?.message || `通信エラーが発生しました (${response.status})`);
    }
    if (data === null && text) {
        throw new Error('サーバーから不正な応答がありました');
    }
    return data;
};

/** The list inside a response: the response itself if it is an array, else response[key] or response.data. */
KaruruUtils.extractData = function(response, key = null) {
    if (!response || response.success === false) return null;
    if (Array.isArray(response)) return response;
    if (key && response[key] !== undefined) return response[key];
    return response.data !== undefined ? response.data : response;
};

KaruruUtils.buildUrl = function(baseUrl, params = {}) {
    const url = new URL(baseUrl, window.location.origin);
    Object.entries(params).forEach(([key, value]) => {
        if (value !== null && value !== undefined && value !== '') {
            url.searchParams.append(key, value);
        }
    });
    return url.pathname + url.search;
};

/** Absolute URLs pass through; stored paths ("uploads/..", "/img/..") get the context path. */
KaruruUtils.resolveImageUrl = function(imageUrl, fallback = '/img/placeholder-product.svg') {
    const path = imageUrl && imageUrl !== 'null' && imageUrl !== 'undefined' ? imageUrl.trim() : '';
    if (/^https?:\/\//.test(path)) return path;
    const target = path || fallback;
    return (window.CONTEXT_PATH || '') + (target.startsWith('/') ? target : '/' + target);
};

KaruruUtils.resolveAvatarUrl = url => KaruruUtils.resolveImageUrl(url, '/img/default-avatar.png');
KaruruUtils.resolveProductImageUrl = url => KaruruUtils.resolveImageUrl(url, '/img/placeholder-product.svg');

/** Replaces a broken <img> with the fallback once (onerror="KaruruUtils.imageFallback(this)"). */
KaruruUtils.imageFallback = function(img, fallback = '/img/placeholder-product.svg') {
    img.onerror = null;
    img.src = KaruruUtils.resolveImageUrl('', fallback);
};

/** The lowest rental rate as "¥1,500 / 日", or '' when the product has none. */
KaruruUtils.rentalRate = function(product) {
    const rates = [['rental_price_daily', '日'], ['rental_price_weekly', '週'], ['rental_price_monthly', '月']];
    const rate = rates.find(([key]) => Number(product[key]) > 0);
    return rate ? `${KaruruUtils.formatPrice(product[rate[0]])}<small> / ${rate[1]}</small>` : '';
};

/** Product tile for grids; the data comes from ProductCards on the server. */
KaruruUtils.productCard = function(product) {
    const image = (product.images && product.images[0]) || product.image_url;
    const rental = product.is_rental ? KaruruUtils.rentalRate(product) : '';
    const unavailable = product.is_available === false || (product.status && product.status !== 'available');
    const tag = product.is_rental ? 'レンタル可' : (product.is_negotiable ? '値下げ交渉可' : '');
    const discount = !product.is_rental && Number(product.discount_percentage) > 0 ? Number(product.discount_percentage) : 0;
    return `
        <a class="product-card" href="${window.CONTEXT_PATH}/product-detail.jsp?id=${product.product_id}">
            <div class="product-card-media">
                <img src="${KaruruUtils.resolveProductImageUrl(image)}" alt="" loading="lazy"
                     onerror="KaruruUtils.imageFallback(this)">
                ${tag ? `<span class="product-card-tag${product.is_rental ? ' product-card-tag-rental' : ''}">${tag}</span>` : ''}
                ${discount ? `<span class="product-card-discount">${discount}%OFF</span>` : ''}
                ${unavailable ? `<span class="product-card-status">${escapeHtml(product.status_text || '販売停止')}</span>` : ''}
            </div>
            <div class="product-card-body">
                <h3 class="product-card-title">${escapeHtml(product.product_name)}</h3>
                <div class="product-card-price price">${rental || KaruruUtils.formatPrice(product.price)}</div>
                ${product.seller_name ? `<div class="product-card-meta">${escapeHtml(product.seller_name)}</div>` : ''}
            </div>
        </a>`;
};

/** Fills a .product-grid, or shows an empty-state message. */
KaruruUtils.renderProductGrid = function(container, products, emptyMessage = '商品がありません') {
    if (!container) return;
    container.innerHTML = products && products.length
        ? products.map(KaruruUtils.productCard).join('')
        : KaruruUtils.emptyState('bi-box-seam', emptyMessage);
};

KaruruUtils.categoryTile = function(category) {
    const image = category.image_url || `/img/categories/${category.slug}.png`;
    return `
        <a class="category-tile" href="${window.CONTEXT_PATH}/products.jsp?category=${category.category_id}">
            <img src="${KaruruUtils.resolveImageUrl(image)}" alt="" loading="lazy"
                 onerror="KaruruUtils.imageFallback(this, '/img/default-category.png')">
            <span>${escapeHtml(category.category_name)}</span>
        </a>`;
};

/** Items and totals of an order (OrderServlet getOrderDetails), shared by the confirmation and detail pages. */
KaruruUtils.orderSummary = function(order) {
    const rows = [
        ['商品の小計', order.subtotal],
        ['送料', order.shipping_cost],
        ['手数料', order.tax_amount],
        ...(Number(order.discount_amount) > 0 ? [['割引', -order.discount_amount]] : [])
    ];
    return `
        <section class="panel">
            <dl class="summary-list">
                <div><dt>注文番号</dt><dd>${escapeHtml(order.order_number)}</dd></div>
                <div><dt>注文日時</dt><dd>${KaruruUtils.formatDateTime(order.created_at)}</dd></div>
                <div><dt>支払い方法</dt><dd>${escapeHtml(KaruruUtils.PAYMENT_METHODS[order.payment_method] || order.payment_method || '-')}</dd></div>
            </dl>
            <ul class="line-items line-items-compact">
                ${order.items.map(item => `
                    <li class="line-item">
                        <a class="line-item-image" href="${window.CONTEXT_PATH}/product-detail.jsp?id=${item.product_id}">
                            <img src="${KaruruUtils.resolveProductImageUrl(item.image_url)}" alt="" onerror="KaruruUtils.imageFallback(this)">
                        </a>
                        <span class="line-item-body">
                            <a class="line-item-name" href="${window.CONTEXT_PATH}/product-detail.jsp?id=${item.product_id}">${escapeHtml(item.product_name)}</a>
                            <span class="line-item-meta">${KaruruUtils.formatPrice(item.price)} × ${item.quantity}</span>
                        </span>
                        <span class="line-item-price price">${KaruruUtils.formatPrice(item.subtotal)}</span>
                    </li>`).join('')}
            </ul>
            <dl class="summary-list">
                ${rows.map(([label, value]) => `<div><dt>${label}</dt><dd class="price">${KaruruUtils.formatPrice(value)}</dd></div>`).join('')}
                <div class="summary-total"><dt>合計</dt><dd class="price">${KaruruUtils.formatPrice(order.total_amount)}</dd></div>
            </dl>
        </section>`;
};

KaruruUtils.emptyState = function(icon, message) {
    return `<div class="empty-state"><i class="bi ${icon}"></i><p>${escapeHtml(message)}</p></div>`;
};

// ---------------------------------------------------------------------------
// Header badges
// ---------------------------------------------------------------------------

function setBadge(id, count) {
    const badge = document.getElementById(id);
    if (!badge) return;
    badge.textContent = count > 99 ? '99+' : String(count);
    badge.hidden = !(count > 0);
}

function setBadges(ids, count) {
    ids.forEach(id => setBadge(id, count));
}

async function fetchCount(url) {
    try {
        const data = await KaruruUtils.apiFetch(url);
        return parseInt(data?.count ?? data?.unread_count ?? 0, 10) || 0;
    } catch (e) {
        return null;
    }
}

window.updateCartCount = async function() {
    const count = await fetchCount(`${window.CONTEXT_PATH}/CartServlet?action=getCartCount`);
    if (count !== null) setBadges(['cartBadge', 'mobileCartBadge'], count);
};

/** Sets the unread-message badges; fetches the count when none is given. */
window.updateMessageBadge = async function(count) {
    const value = Number.isInteger(count) ? count
        : await fetchCount(`${window.CONTEXT_PATH}/MessagesServlet?action=getUnreadCount`);
    if (value !== null) setBadge('messageBadge', value);
};

window.updateNotificationBadge = function(count) {
    setBadge('notificationBadge', count || 0);
};

window.updateNotificationBadgeCount = async function() {
    const count = await fetchCount(`${window.CONTEXT_PATH}/NotificationsServlet?action=getUnreadCount`);
    if (count !== null) window.updateNotificationBadge(count);
};

/** Filled heart in the navigation when the user has any favourites. */
window.updateFavoriteIcon = async function() {
    const count = await fetchCount(`${window.CONTEXT_PATH}/FavoriteServlet?action=getCount`);
    if (count === null) return;
    document.querySelectorAll('[data-favorites-icon]').forEach(icon => {
        icon.classList.toggle('bi-heart', count === 0);
        icon.classList.toggle('bi-heart-fill', count > 0);
    });
};

window.updateAllBadges = function() {
    return Promise.all([
        window.updateCartCount(),
        window.updateMessageBadge(),
        window.updateNotificationBadgeCount(),
        window.updateFavoriteIcon()
    ]);
};

window.initWebSockets = function() {
    if (!window.currentUserId || !window.webSocketManager) return;
    if (localStorage.getItem('websocket_enabled') === 'false') {
        window.webSocketManager.userId = window.currentUserId;
        window.webSocketManager.startMessagePolling();
        window.webSocketManager.startNotificationPolling();
    } else {
        window.webSocketManager.initialize(window.currentUserId);
    }
};

/** Marks the current page in the mobile tab bar and the desktop nav. */
function markActiveTab() {
    const page = window.location.pathname.split('/').pop() || 'index.jsp';
    document.querySelectorAll('.tab-bar-item[data-page]').forEach(item => {
        const active = item.dataset.page.split(' ').includes(page);
        item.classList.toggle('active', active);
        if (active) item.setAttribute('aria-current', 'page');
    });
    document.querySelectorAll('.site-nav a').forEach(link => {
        if (link.getAttribute('href').split('/').pop() === page) link.setAttribute('aria-current', 'page');
    });
}

/*
 * Light/dark theme. The inline script in includes/header.jsp applies the theme before first paint; this
 * keeps the toggle buttons in sync and follows the OS setting until the user picks a theme themselves.
 * Pages that draw their own colours (charts) can listen for the "karuru:themechange" event.
 */
window.KaruruTheme = {
    storageKey: 'karuru-theme',

    current() {
        return document.documentElement.getAttribute('data-bs-theme') === 'dark' ? 'dark' : 'light';
    },

    saved() {
        try { return localStorage.getItem(this.storageKey); } catch (e) { return null; }
    },

    apply(theme, save) {
        const root = document.documentElement;
        root.classList.add('theme-switching');
        root.setAttribute('data-bs-theme', theme);
        setTimeout(() => root.classList.remove('theme-switching'), 250);
        if (save) {
            try { localStorage.setItem(this.storageKey, theme); } catch (e) { /* private mode: theme still applies */ }
        }
        this.syncButtons();
        document.dispatchEvent(new CustomEvent('karuru:themechange', { detail: { theme } }));
    },

    toggle() {
        this.apply(this.current() === 'dark' ? 'light' : 'dark', true);
    },

    syncButtons() {
        const dark = this.current() === 'dark';
        const label = dark ? 'ライトモードに切り替え' : 'ダークモードに切り替え';
        document.querySelectorAll('[data-theme-toggle]').forEach(button => {
            button.setAttribute('aria-label', label);
            button.title = label;
            const icon = button.querySelector('.bi');
            if (icon) icon.className = 'bi ' + (dark ? 'bi-sun' : 'bi-moon-stars');
        });
    },

    init() {
        this.syncButtons();
        document.querySelectorAll('[data-theme-toggle]').forEach(button => {
            button.addEventListener('click', () => this.toggle());
        });
        const media = window.matchMedia('(prefers-color-scheme: dark)');
        media.addEventListener('change', event => {
            const saved = this.saved();
            if (saved !== 'light' && saved !== 'dark') this.apply(event.matches ? 'dark' : 'light', false);
        });
    }
};

/*
 * Mobile tab bar: hidden by default so the page gets the whole screen. It only appears on a deliberate
 * scroll: a clear upward scroll (UP_DISTANCE px in one go), or pulling past either end of the page, which
 * also works on short pages that cannot scroll at all. Any downward scroll hides it again.
 */
window.KaruruTabBar = {
    UP_DISTANCE: 80,
    DOWN_DISTANCE: 12,
    PULL_DISTANCE: 150,

    init() {
        this.bar = document.querySelector('.tab-bar');
        if (!this.bar) return;
        this.lastY = window.scrollY;
        this.up = 0;
        this.down = 0;

        window.addEventListener('scroll', () => this.onScroll(), { passive: true });
        window.addEventListener('wheel', event => this.onPull(event.deltaY), { passive: true });
        let touchY = null;
        window.addEventListener('touchstart', event => { touchY = event.touches[0].clientY; }, { passive: true });
        window.addEventListener('touchmove', event => {
            if (touchY === null) return;
            const y = event.touches[0].clientY;
            this.onPull(touchY - y);   // finger moving up = pulling the page down
            touchY = y;
        }, { passive: true });
        // Keyboard users tabbing into the bar must be able to see it.
        this.bar.addEventListener('focusin', () => this.show());
    },

    atBottom() {
        return window.scrollY + window.innerHeight >= document.documentElement.scrollHeight - 2;
    },

    onScroll() {
        const y = window.scrollY;
        const dy = y - this.lastY;
        this.lastY = y;
        if (dy > 0) {
            this.up = 0;
            this.down += dy;
            if (this.down > this.DOWN_DISTANCE && !this.atBottom()) this.hide();
        } else if (dy < 0) {
            this.down = 0;
            this.up -= dy;
            if (this.up >= this.UP_DISTANCE) this.show();
        }
    },

    /** A wheel or swipe at either end of the page, where the page itself can no longer move. */
    onPull(deltaY) {
        if ((deltaY > 0 && this.atBottom()) || (deltaY < 0 && window.scrollY <= 0)) {
            this.pulled = (this.pulled || 0) + Math.abs(deltaY);
            if (this.pulled >= this.PULL_DISTANCE) this.show();
        } else {
            this.pulled = 0;
        }
    },

    show() {
        this.bar.classList.add('is-visible');
        this.pulled = 0;
    },

    hide() {
        if (this.bar.contains(document.activeElement)) return;
        this.bar.classList.remove('is-visible');
    }
};

document.addEventListener('DOMContentLoaded', () => {
    window.KaruruTheme.init();
    window.KaruruTabBar.init();
    markActiveTab();
    if (window.currentUserId) {
        window.updateAllBadges();
        setInterval(window.updateAllBadges, 30000);
        window.initWebSockets();
    }
});
