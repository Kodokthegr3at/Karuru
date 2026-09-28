// Favourite products (favorites.jsp).

document.addEventListener('DOMContentLoaded', () => {
    const grid = document.getElementById('favoritesGrid');
    loadFavorites(grid);
    grid.addEventListener('click', event => {
        const button = event.target.closest('button[data-unfavorite]');
        if (button) removeFavorite(grid, button);
    });
});

async function loadFavorites(grid) {
    try {
        const products = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/FavoriteServlet?action=getFavorites`);
        grid.innerHTML = products.length
            ? products.map(product => `
                <div class="product-card-wrap">
                    ${KaruruUtils.productCard(product)}
                    <button class="card-corner-btn" type="button" data-unfavorite="${product.product_id}"
                            aria-label="${escapeHtml(product.product_name)}をお気に入りから外す"><i class="bi bi-heart-fill"></i></button>
                </div>`).join('')
            : KaruruUtils.emptyState('bi-heart', 'お気に入りの商品はまだありません');
    } catch (error) {
        grid.innerHTML = KaruruUtils.emptyState('bi-exclamation-circle', error.message);
    }
}

async function removeFavorite(grid, button) {
    button.disabled = true;
    try {
        await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/FavoriteServlet`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams({ action: 'remove', productId: button.dataset.unfavorite })
        });
        button.closest('.product-card-wrap').remove();
        if (!grid.children.length) grid.innerHTML = KaruruUtils.emptyState('bi-heart', 'お気に入りの商品はまだありません');
        window.updateFavoriteIcon();
    } catch (error) {
        KaruruUtils.showNotification(error.message, 'danger');
        button.disabled = false;
    }
}
