// Everything the user has bought (garage.jsp).

document.addEventListener('DOMContentLoaded', async () => {
    const list = document.getElementById('garageProducts');
    try {
        const { products } = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/GarageServlet`);
        list.innerHTML = products.length ? products.map(item => `
            <li class="record">
                <a class="record-thumb" href="${window.CONTEXT_PATH}/product-detail.jsp?id=${item.product_id}">
                    <img src="${KaruruUtils.resolveProductImageUrl((item.images && item.images[0]) || item.image_url)}" alt=""
                         onerror="KaruruUtils.imageFallback(this)">
                </a>
                <span class="record-body">
                    <a class="record-title" href="${window.CONTEXT_PATH}/product-detail.jsp?id=${item.product_id}">${escapeHtml(item.product_name)}</a>
                    <span class="record-meta">${KaruruUtils.formatDateTime(item.purchase_date)}に購入 ・ ${escapeHtml(item.seller_username || '')}</span>
                </span>
                <span class="record-side">
                    ${KaruruUtils.statusBadge(KaruruUtils.ORDER_STATUS, item.order_status)}
                    <a class="small" href="${window.CONTEXT_PATH}/order-detail.jsp?id=${item.order_id}">注文を見る</a>
                </span>
            </li>`).join('')
            : `<li>${KaruruUtils.emptyState('bi-box-seam', 'まだ購入した商品はありません')}</li>`;
    } catch (error) {
        list.innerHTML = `<li>${KaruruUtils.emptyState('bi-exclamation-circle', error.message)}</li>`;
    }
});
