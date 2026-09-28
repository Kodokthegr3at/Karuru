// Seller dashboard (dashboard.jsp): key figures, received orders, sales chart and own listings.

document.addEventListener('DOMContentLoaded', () => {
    loadStats();
    loadOrders();
    loadSales();
    loadListings();
    document.getElementById('userProducts').addEventListener('click', event => {
        const button = event.target.closest('button[data-delete]');
        if (button) deleteListing(button.dataset.delete, button.dataset.name);
    });
});

const api = path => KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/${path}`);

async function loadStats() {
    try {
        const stats = await api('DashboardServlet?action=getStats');
        document.getElementById('activeListings').textContent = stats.activeListings;
        document.getElementById('salesThisMonth').textContent = KaruruUtils.formatPrice(stats.salesThisMonth);
        document.getElementById('salesLastMonth').textContent = `先月 ${KaruruUtils.formatPrice(stats.salesLastMonth)}`;
        document.getElementById('ordersToHandle').textContent = stats.ordersToHandle;
        document.getElementById('unreadMessages').textContent = stats.unreadMessages;
    } catch (error) {
        KaruruUtils.showNotification(error.message, 'danger');
    }
}

async function loadOrders() {
    const list = document.getElementById('sellerOrders');
    try {
        const { orders } = await api('DashboardServlet?action=getSellerOrders');
        list.innerHTML = orders.length ? orders.map(order => `
            <li>
                <a class="record" href="${window.CONTEXT_PATH}/order-detail.jsp?id=${order.order_id}">
                    <span class="record-body">
                        <span class="record-title">${escapeHtml(order.first_item)}${order.item_count > 1 ? ` ほか${order.item_count - 1}点` : ''}</span>
                        <span class="record-meta">${escapeHtml(order.buyer_name || '')}さん ・ ${KaruruUtils.formatDateTime(order.created_at)}</span>
                    </span>
                    <span class="record-side">
                        ${KaruruUtils.statusBadge(KaruruUtils.ORDER_STATUS, order.order_status)}
                        <span class="price">${KaruruUtils.formatPrice(order.seller_subtotal)}</span>
                    </span>
                </a>
            </li>`).join('')
            : `<li>${KaruruUtils.emptyState('bi-receipt', 'まだ注文はありません')}</li>`;
    } catch (error) {
        list.innerHTML = `<li>${KaruruUtils.emptyState('bi-exclamation-circle', error.message)}</li>`;
    }
}

async function loadSales() {
    let sales;
    try {
        ({ sales } = await api('DashboardServlet?action=getSales'));
    } catch (error) {
        return;
    }
    const canvas = document.getElementById('salesChart');
    if (!sales.length || !window.Chart) {
        canvas.hidden = true;
        document.getElementById('salesEmpty').hidden = false;
        return;
    }
    const days = [...sales].reverse(); // the API returns newest first
    const brand = getComputedStyle(document.documentElement).getPropertyValue('--brand').trim();
    new Chart(canvas, {
        type: 'bar',
        data: {
            labels: days.map(day => new Date(day.date).toLocaleDateString('ja-JP', { month: 'numeric', day: 'numeric' })),
            datasets: [{ label: '売上', data: days.map(day => day.revenue), backgroundColor: brand, borderRadius: 4, maxBarThickness: 32 }]
        },
        options: {
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false },
                tooltip: { callbacks: { label: item => KaruruUtils.formatPrice(item.parsed.y) } }
            },
            scales: {
                x: { grid: { display: false } },
                y: { beginAtZero: true, ticks: { callback: value => KaruruUtils.formatPrice(value) } }
            }
        }
    });
}

async function loadListings() {
    const list = document.getElementById('userProducts');
    try {
        const { products } = await api('ProductServlet?action=getUserProducts');
        list.innerHTML = products.length ? products.map(product => `
            <li class="record">
                <a class="record-thumb" href="${window.CONTEXT_PATH}/product-detail.jsp?id=${product.product_id}">
                    <img src="${KaruruUtils.resolveProductImageUrl(product.images[0] || product.image_url)}" alt="" onerror="KaruruUtils.imageFallback(this)">
                </a>
                <span class="record-body">
                    <a class="record-title" href="${window.CONTEXT_PATH}/product-detail.jsp?id=${product.product_id}">${escapeHtml(product.product_name)}</a>
                    <span class="record-meta">在庫 ${product.stock_quantity} ・ 閲覧 ${product.views_count} ・ いいね ${product.likes_count}</span>
                </span>
                <span class="record-side">
                    <span class="status status-${product.status_color === 'secondary' ? '' : product.status_color}">${escapeHtml(product.status_text)}</span>
                    <span class="price">${KaruruUtils.formatPrice(product.price)}</span>
                </span>
                <button class="btn btn-link btn-sm text-danger" type="button" data-delete="${product.product_id}"
                        data-name="${escapeHtml(product.product_name)}" aria-label="${escapeHtml(product.product_name)}を削除">削除</button>
            </li>`).join('')
            : `<li>${KaruruUtils.emptyState('bi-box-seam', 'まだ出品した商品はありません')}</li>`;
    } catch (error) {
        list.innerHTML = `<li>${KaruruUtils.emptyState('bi-exclamation-circle', error.message)}</li>`;
    }
}

async function deleteListing(productId, name) {
    if (!KaruruUtils.confirmDialog(`「${name}」の出品を取り消しますか？`)) return;
    try {
        await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/ProductServlet?action=delete&id=${productId}`, { method: 'DELETE' });
        KaruruUtils.showNotification('出品を取り消しました', 'success');
        loadListings();
        loadStats();
    } catch (error) {
        KaruruUtils.showNotification(error.message, 'danger');
    }
}
