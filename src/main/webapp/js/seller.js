// A seller's public profile (seller.jsp?seller_id=N).

document.addEventListener('DOMContentLoaded', async () => {
    const sellerId = new URLSearchParams(location.search).get('seller_id');
    const profile = document.getElementById('sellerProfile');
    let data;
    try {
        data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/SellerProfileServlet?seller_id=${encodeURIComponent(sellerId)}`);
    } catch (error) {
        profile.innerHTML = KaruruUtils.emptyState('bi-exclamation-circle', error.message);
        return;
    }
    const seller = data.seller;
    const name = seller.full_name || seller.username;
    document.title = `${name} | カルル`;
    profile.innerHTML = `
        <header class="profile-head">
            <img class="avatar avatar-lg" src="${KaruruUtils.resolveAvatarUrl(seller.avatar_url)}" alt=""
                 onerror="KaruruUtils.imageFallback(this, '/img/default-avatar.png')">
            <div class="profile-head-body">
                <h1>${escapeHtml(name)} ${seller.is_verified ? '<i class="bi bi-patch-check-fill verified" title="本人確認済み"></i>' : ''}</h1>
                <p class="text-muted mb-1">@${escapeHtml(seller.username)} ・ ${new Date(seller.created_at).getFullYear()}年から利用</p>
                <p class="mb-0">${seller.total_reviews > 0
                    ? `<span class="stars-inline"><i class="bi bi-star-fill"></i> ${seller.avg_rating.toFixed(1)}</span>（${seller.total_reviews}件の評価）`
                    : '評価はまだありません'} ・ 出品 ${seller.total_products}件</p>
            </div>
            ${Number(seller.user_id) !== Number(window.currentUserId) ? `
                <a class="btn btn-outline-secondary" href="${window.CONTEXT_PATH}/messages.jsp?user_id=${seller.user_id}">メッセージを送る</a>` : ''}
        </header>
        ${seller.bio ? `<p class="profile-bio">${escapeHtml(seller.bio)}</p>` : ''}`;
    data.products.forEach(product => { product.seller_name = null; }); // the seller is this page
    KaruruUtils.renderProductGrid(document.getElementById('sellerProducts'), data.products, '出品中の商品はありません');
});
