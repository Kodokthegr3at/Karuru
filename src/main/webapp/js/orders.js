// The buyer's order history (orders.jsp).

document.addEventListener('DOMContentLoaded', () => {
    const filter = document.getElementById('statusFilter');
    filter.insertAdjacentHTML('beforeend', Object.entries(KaruruUtils.ORDER_STATUS)
        .map(([value, [label]]) => `<option value="${value}">${label}</option>`).join(''));
    filter.addEventListener('change', () => loadOrders(filter.value));
    loadOrders('');
});

async function loadOrders(status) {
    const list = document.getElementById('ordersList');
    try {
        const data = await KaruruUtils.apiFetch(
            KaruruUtils.buildUrl(`${window.CONTEXT_PATH}/OrderServlet`, { action: 'getUserOrders', status }));
        list.innerHTML = data.orders.length
            ? data.orders.map(renderOrder).join('')
            : `<li>${KaruruUtils.emptyState('bi-receipt', status ? 'この状態の注文はありません' : 'まだ注文はありません')}</li>`;
    } catch (error) {
        list.innerHTML = `<li>${KaruruUtils.emptyState('bi-exclamation-circle', error.message)}</li>`;
    }
}

function renderOrder(order) {
    const first = order.items[0];
    const title = first
        ? escapeHtml(first.product_name) + (order.items.length > 1 ? ` ほか${order.items.length - 1}点` : '')
        : escapeHtml(order.order_number);
    return `
        <li>
            <a class="record" href="${window.CONTEXT_PATH}/order-detail.jsp?id=${order.order_id}">
                <span class="record-thumb">
                    <img src="${KaruruUtils.resolveProductImageUrl(first && first.image_url)}" alt="" onerror="KaruruUtils.imageFallback(this)">
                </span>
                <span class="record-body">
                    <span class="record-title">${title}</span>
                    <span class="record-meta">${KaruruUtils.formatDateTime(order.created_at)} ・ ${escapeHtml(order.order_number)}</span>
                </span>
                <span class="record-side">
                    ${KaruruUtils.statusBadge(KaruruUtils.ORDER_STATUS, order.order_status)}
                    <span class="price">${KaruruUtils.formatPrice(order.total_amount)}</span>
                </span>
            </a>
        </li>`;
}
