// Product page (product-detail.jsp?id=N). One request to ProductDetailsServlet returns the product,
// its images, specifications, reviews and similar products.

const productId = new URLSearchParams(window.location.search).get('id');
let product = null;

document.addEventListener('DOMContentLoaded', () => {
    const container = document.getElementById('productDetail');
    if (!productId) {
        container.innerHTML = KaruruUtils.emptyState('bi-question-circle', '商品が指定されていません', KaruruUtils.browseAction());
        return;
    }
    loadProduct(container);
    document.getElementById('offerForm').addEventListener('submit', submitOffer);
});

function requireLogin() {
    if (window.currentUserId) return true;
    window.location.href = KaruruUtils.loginUrl();
    return false;
}

async function loadProduct(container) {
    let data;
    try {
        data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/ProductDetailsServlet?id=${encodeURIComponent(productId)}`);
    } catch (error) {
        container.innerHTML = KaruruUtils.emptyState('bi-exclamation-circle', error.message, KaruruUtils.browseAction());
        return;
    }
    product = data.product;
    document.title = `${product.product_name} | カルル`;

    const images = data.images.length ? data.images.map(image => image.image_url) : [product.image_url];
    container.innerHTML = `
        <nav class="breadcrumbs" aria-label="パンくずリスト">
            <a href="${window.CONTEXT_PATH}/products.jsp">商品を探す</a>
            ${data.categories[0] ? `<a href="${window.CONTEXT_PATH}/products.jsp?category=${data.categories[0].category_id}">${escapeHtml(data.categories[0].category_name)}</a>` : ''}
        </nav>
        <div class="product-layout">
            ${renderGallery(images)}
            <div class="product-summary">
                <h1 class="product-name">${escapeHtml(product.product_name)}</h1>
                ${renderPrice()}
                <div class="product-tags">
                    ${product.condition ? `<span class="status">${KaruruUtils.CONDITIONS[product.condition] || escapeHtml(product.condition)}</span>` : ''}
                    ${product.is_negotiable ? '<span class="status">値下げ交渉可</span>' : ''}
                    ${product.status !== 'available' ? `<span class="status status-danger">${product.status === 'sold' ? '売り切れ' : '販売停止中'}</span>` : ''}
                </div>
                ${renderActions()}
                ${renderSeller(data.sellerStats)}
            </div>
        </div>
        <div class="product-body">
            ${product.description ? `
                <section class="section" aria-labelledby="descriptionTitle">
                    <h2 id="descriptionTitle">商品の説明</h2>
                    <p class="product-description">${escapeHtml(product.description)}</p>
                </section>` : ''}
            <section class="section" aria-labelledby="detailsTitle">
                <h2 id="detailsTitle">商品の情報</h2>
                ${renderDetails(data.categories, data.specifications)}
            </section>
        </div>`;

    bindActions();
    setupGallery();
    renderReviews(data.reviews, data.reviewStats);
    document.getElementById('relatedSection').hidden = data.relatedProducts.length === 0;
    KaruruUtils.renderProductGrid(document.getElementById('relatedProducts'), data.relatedProducts);
    if (window.currentUserId) {
        checkFavorite();
        setupReviewForm();
    }
}

function renderGallery(images) {
    return `
        <div class="gallery">
            <div class="gallery-main">
                <img id="galleryMain" src="${KaruruUtils.resolveProductImageUrl(images[0])}" alt="${escapeHtml(product.product_name)}"
                     onerror="KaruruUtils.imageFallback(this)">
            </div>
            ${images.length > 1 ? `
                <div class="gallery-thumbs">
                    ${images.map((image, index) => `
                        <button type="button" class="gallery-thumb" data-src="${KaruruUtils.resolveProductImageUrl(image)}"
                                aria-label="画像 ${index + 1}"${index === 0 ? ' aria-current="true"' : ''}>
                            <img src="${KaruruUtils.resolveProductImageUrl(image)}" alt="" onerror="KaruruUtils.imageFallback(this)">
                        </button>`).join('')}
                </div>` : ''}
        </div>`;
}

function setupGallery() {
    const main = document.getElementById('galleryMain');
    document.querySelectorAll('.gallery-thumb').forEach(thumb => {
        thumb.addEventListener('click', () => {
            main.src = thumb.dataset.src;
            document.querySelectorAll('.gallery-thumb').forEach(t => t.removeAttribute('aria-current'));
            thumb.setAttribute('aria-current', 'true');
        });
    });
}

function renderPrice() {
    const discounted = Number(product.original_price) > Number(product.price);
    const rates = [['rental_price_daily', '1日'], ['rental_price_weekly', '1週間'], ['rental_price_monthly', '1か月']]
        .filter(([key]) => Number(product[key]) > 0);
    return `
        <div class="product-price">
            <span class="price">${KaruruUtils.formatPrice(product.price)}</span>
            ${discounted ? `<del>${KaruruUtils.formatPrice(product.original_price)}</del>` : ''}
        </div>
        ${product.is_rental && rates.length ? `
            <dl class="rental-rates">
                ${rates.map(([key, label]) => `<div><dt>${label}</dt><dd class="price">${KaruruUtils.formatPrice(product[key])}</dd></div>`).join('')}
            </dl>` : ''}`;
}

function renderActions() {
    const own = Number(product.seller_id) === Number(window.currentUserId);
    if (own) {
        return `
            <div class="product-actions">
                <p class="text-muted mb-2">あなたが出品した商品です。</p>
                <a class="btn btn-outline-secondary btn-lg" href="${window.CONTEXT_PATH}/dashboard.jsp">出品した商品を管理</a>
            </div>`;
    }
    const available = product.status === 'available' && product.stock_quantity > 0;
    return `
        <div class="product-actions">
            ${available ? `
                <button class="btn btn-primary btn-lg" type="button" data-action="buy">購入手続きへ</button>
                ${product.is_rental ? '<button class="btn btn-outline-primary btn-lg" type="button" data-action="rent">レンタルする</button>' : ''}
                <button class="btn btn-outline-secondary btn-lg" type="button" data-action="cart">カートに入れる</button>`
                : '<button class="btn btn-secondary btn-lg" type="button" disabled>現在購入できません</button>'}
        </div>
        <div class="product-links">
            ${available && product.is_negotiable ? '<button type="button" class="btn btn-link" data-action="offer"><i class="bi bi-tag"></i>値下げを交渉</button>' : ''}
            <button type="button" class="btn btn-link" data-action="ask"><i class="bi bi-chat"></i>出品者に質問</button>
            <button type="button" class="btn btn-link" data-action="favorite" aria-pressed="false"><i class="bi bi-heart"></i><span>お気に入り</span></button>
            <button type="button" class="btn btn-link" data-action="share"><i class="bi bi-share"></i>共有</button>
        </div>`;
}

function renderSeller(stats) {
    const joined = product.seller_joined_at ? new Date(product.seller_joined_at).getFullYear() + '年から利用' : '';
    const rating = stats && stats.review_count > 0
        ? `<i class="bi bi-star-fill"></i> ${stats.avg_rating.toFixed(1)}（${stats.review_count}件）`
        : '評価はまだありません';
    return `
        <a class="seller-card" href="${window.CONTEXT_PATH}/seller.jsp?seller_id=${product.seller_id}">
            <img class="avatar" src="${KaruruUtils.resolveAvatarUrl(product.seller_avatar)}" alt=""
                 onerror="KaruruUtils.imageFallback(this, '/img/default-avatar.png')">
            <span class="seller-card-body">
                <span class="seller-card-name">${escapeHtml(product.seller_name || product.seller_username)}
                    ${product.seller_verified ? '<i class="bi bi-patch-check-fill" title="本人確認済み"></i>' : ''}</span>
                <span class="seller-card-meta">${rating}${joined ? ' ・ ' + joined : ''}</span>
            </span>
            <i class="bi bi-chevron-right"></i>
        </a>`;
}

function renderDetails(categories, specifications) {
    const rows = [
        ['カテゴリー', categories.map(c => escapeHtml(c.category_name)).join('、')],
        ['商品の状態', KaruruUtils.CONDITIONS[product.condition] || escapeHtml(product.condition || '')],
        ['在庫', `${product.stock_quantity}点`],
        ['重量', Number(product.weight) > 0 ? `${product.weight} kg` : ''],
        ...specifications.map(spec => [escapeHtml(spec.spec_name), escapeHtml(spec.spec_value)]),
        ['出品日', product.created_at ? new Date(product.created_at).toLocaleDateString('ja-JP') : '']
    ].filter(([, value]) => value);
    return `<dl class="detail-list">${rows.map(([label, value]) => `<div><dt>${label}</dt><dd>${value}</dd></div>`).join('')}</dl>`;
}

function bindActions() {
    const handlers = { buy: buyNow, rent: rentNow, cart: addToCart, offer: openOffer, ask: askSeller, favorite: toggleFavorite, share: share };
    document.querySelectorAll('[data-action]').forEach(button => {
        button.addEventListener('click', () => handlers[button.dataset.action](button));
    });
}

async function withBusy(button, task) {
    button.disabled = true;
    try {
        await task();
    } catch (error) {
        KaruruUtils.showNotification(error.message, 'danger');
    } finally {
        button.disabled = false;
    }
}

function postForm(url, params) {
    return KaruruUtils.apiFetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams(params)
    });
}

function addToCart(button) {
    if (!requireLogin()) return;
    withBusy(button, async () => {
        const data = await postForm(`${window.CONTEXT_PATH}/CartServlet`, { action: 'add', productId: product.product_id, quantity: 1 });
        KaruruUtils.showNotification(data.message, 'success');
        window.updateCartCount();
    });
}

/** Puts the product in the cart unless it is already there, then opens checkout. */
function buyNow(button) {
    if (!requireLogin()) return;
    withBusy(button, async () => {
        const cart = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/CartServlet?action=getCart`);
        if (!cart.items.some(item => Number(item.product_id) === Number(product.product_id))) {
            await postForm(`${window.CONTEXT_PATH}/CartServlet`, { action: 'add', productId: product.product_id, quantity: 1 });
        }
        window.location.href = `${window.CONTEXT_PATH}/checkout.jsp`;
    });
}

function rentNow() {
    if (requireLogin()) window.location.href = `${window.CONTEXT_PATH}/rental.jsp?product_id=${product.product_id}`;
}

function askSeller() {
    if (requireLogin()) {
        window.location.href = `${window.CONTEXT_PATH}/messages.jsp?user_id=${product.seller_id}&product_id=${product.product_id}&type=product`;
    }
}

function share() {
    if (navigator.share) {
        navigator.share({ title: product.product_name, url: location.href }).catch(() => {});
    } else {
        navigator.clipboard.writeText(location.href)
            .then(() => KaruruUtils.showNotification('リンクをコピーしました', 'success'));
    }
}

function showFavorite(isFavorite) {
    const button = document.querySelector('[data-action="favorite"]');
    if (!button) return;
    button.setAttribute('aria-pressed', String(isFavorite));
    button.querySelector('.bi').className = isFavorite ? 'bi bi-heart-fill' : 'bi bi-heart';
    button.querySelector('span').textContent = isFavorite ? 'お気に入り済み' : 'お気に入り';
}

async function checkFavorite() {
    try {
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/FavoriteServlet?action=check&productId=${product.product_id}`);
        showFavorite(data.isFavorite);
    } catch (error) {
        // The button simply stays in its default state.
    }
}

function toggleFavorite(button) {
    if (!requireLogin()) return;
    withBusy(button, async () => {
        const data = await postForm(`${window.CONTEXT_PATH}/FavoriteServlet`, { action: 'toggle', productId: product.product_id });
        showFavorite(data.isFavorite);
        window.updateFavoriteIcon();
    });
}

function openOffer() {
    if (!requireLogin()) return;
    const form = document.getElementById('offerForm');
    form.reset();
    form.offer_price.max = Math.ceil(product.price) - 1;
    document.getElementById('offerCurrentPrice').textContent = KaruruUtils.formatPrice(product.price);
    bootstrap.Modal.getOrCreateInstance(document.getElementById('offerModal')).show();
}

async function submitOffer(event) {
    event.preventDefault();
    const form = event.target;
    const button = form.querySelector('[type="submit"]');
    await withBusy(button, async () => {
        await postForm(`${window.CONTEXT_PATH}/OfferServlet`, {
            action: 'create',
            product_id: product.product_id,
            offer_price: form.offer_price.value,
            message: form.message.value
        });
        bootstrap.Modal.getInstance(document.getElementById('offerModal')).hide();
        KaruruUtils.showNotification('オファーを送信しました。出品者からの返事は「オファー」で確認できます。', 'success');
    });
}

// Reviews -------------------------------------------------------------------------------------------

function stars(rating) {
    return `<span class="stars" aria-label="5点中${rating}点">${'<i class="bi bi-star-fill"></i>'.repeat(rating)}${'<i class="bi bi-star"></i>'.repeat(5 - rating)}</span>`;
}

function renderReviews(reviews, stats) {
    document.getElementById('reviewsSection').hidden = false;
    const container = document.getElementById('reviewsContainer');
    if (!reviews.length) {
        container.innerHTML = '<p class="text-muted">まだレビューはありません。</p>';
        return;
    }
    container.innerHTML = `
        <p class="review-summary">${stars(Math.round(stats.average))} <strong>${stats.average.toFixed(1)}</strong>
            <span class="text-muted">${stats.total}件のレビュー</span></p>
        <ul class="review-list">
            ${reviews.map(review => `
                <li class="review">
                    <div class="review-head">
                        <strong>${escapeHtml(review.reviewer.full_name || review.reviewer.username)}</strong>
                        ${review.is_verified_purchase ? '<span class="status status-success">購入者</span>' : ''}
                        <time class="text-muted" datetime="${review.created_at}">${KaruruUtils.formatDate(review.created_at)}</time>
                    </div>
                    ${stars(review.rating)}
                    <p>${escapeHtml(review.review_text)}</p>
                    ${review.comments.map(comment => `
                        <div class="review-reply">
                            <strong>${comment.is_seller_reply ? '出品者からの返信' : escapeHtml(comment.commenter.username)}</strong>
                            <p>${escapeHtml(comment.comment_text)}</p>
                        </div>`).join('')}
                </li>`).join('')}
        </ul>`;
}

/** The review form is offered only to buyers of the product; the server checks this again on submit. */
async function setupReviewForm() {
    const form = document.getElementById('reviewForm');
    try {
        const status = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/ReviewServlet?action=checkPurchaseStatus&product_id=${product.product_id}`);
        form.hidden = !status.has_purchased;
    } catch (error) {
        return;
    }
    form.addEventListener('submit', event => {
        event.preventDefault();
        withBusy(form.querySelector('[type="submit"]'), async () => {
            await postForm(`${window.CONTEXT_PATH}/ReviewServlet`, {
                action: 'submitReview',
                product_id: product.product_id,
                rating: form.rating.value,
                review_text: form.review_text.value
            });
            KaruruUtils.showNotification('レビューを投稿しました', 'success');
            form.hidden = true;
            const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/ProductDetailsServlet?action=getReviews&productId=${product.product_id}`);
            renderReviews(data.reviews, { average: data.reviews.reduce((sum, r) => sum + r.rating, 0) / data.reviews.length, total: data.reviews.length });
        });
    });
}
