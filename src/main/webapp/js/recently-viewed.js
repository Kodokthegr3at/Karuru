// Products the user viewed recently (recently-viewed.jsp).

const EMPTY_MESSAGE = '最近見た商品はありません';

document.addEventListener('DOMContentLoaded', () => {
    loadHistory();
    document.getElementById('clearHistory').addEventListener('click', clearHistory);
});

async function loadHistory() {
    const grid = document.getElementById('recentlyViewedProducts');
    try {
        const { products } = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/RecentlyViewedServlet?action=getRecentlyViewed`);
        products.forEach(product => { product.seller_name = product.seller_username; });
        KaruruUtils.renderProductGrid(grid, products, EMPTY_MESSAGE);
        document.getElementById('clearHistory').hidden = products.length === 0;
    } catch (error) {
        grid.innerHTML = KaruruUtils.emptyState('bi-exclamation-circle', error.message);
    }
}

async function clearHistory() {
    if (!KaruruUtils.confirmDialog('閲覧履歴をすべて消去しますか？')) return;
    try {
        await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/RecentlyViewedServlet`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams({ action: 'clearHistory' })
        });
        loadHistory();
    } catch (error) {
        KaruruUtils.showNotification(error.message, 'danger');
    }
}
