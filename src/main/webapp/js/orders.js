// Orders page JavaScript

document.addEventListener('DOMContentLoaded', function() {
    loadOrders();
    
    document.getElementById('statusFilter').addEventListener('change', function() {
        loadOrders(this.value);
    });
});

async function loadOrders(status = '') {
    try {
        const container = document.getElementById('ordersList');
        if (!container) return;
        
        // Show loading state
        container.innerHTML = `
            <div class="text-center py-5">
                <div class="spinner-border text-primary" role="status">
                    <span class="visually-hidden">読み込み中...</span>
                </div>
                <p class="text-muted mt-3">注文履歴を読み込んでいます...</p>
            </div>
        `;
        
        const params = new URLSearchParams({
            action: 'getUserOrders'
        });
        if (status) {
            params.append('status', status);
        }
        
        const response = await fetch(`${window.CONTEXT_PATH}/OrderServlet?${params}`);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        
        if (data.orders && data.orders.length > 0) {
            container.innerHTML = data.orders.map(order => `
                <div class="card card-light mb-3">
                    <div class="card-header card-header-light d-flex justify-content-between align-items-center">
                        <h5 class="mb-0">
                            <i class="bi bi-receipt me-2"></i>注文番号: ${escapeHtml(order.order_number || `ORD-${order.order_id}`)}
                        </h5>
                        <span class="badge bg-${getOrderStatusBadge(order.order_status)}">${getOrderStatusText(order.order_status)}</span>
                    </div>
                    <div class="card-body">
                        <div class="row mb-3">
                            <div class="col-md-6">
                                <p class="mb-2">
                                    <i class="bi bi-box-seam me-2 text-muted"></i>
                                    <span class="text-muted">商品数:</span> 
                                    <strong>${order.items_count || order.item_count || 0}</strong>
                                </p>
                                <p class="mb-2">
                                    <i class="bi bi-currency-yen me-2 text-muted"></i>
                                    <span class="text-muted">合計:</span> 
                                    <strong class="text-primary fs-5">${KaruruUtils.formatPrice(order.total_amount || 0)}</strong>
                                </p>
                            </div>
                            <div class="col-md-6">
                                <p class="mb-2">
                                    <i class="bi bi-calendar me-2 text-muted"></i>
                                    <span class="text-muted">注文日:</span> 
                                    <span>${KaruruUtils.formatDate(order.created_at)}</span>
                                </p>
                                ${order.shipped_at ? `
                                    <p class="mb-2">
                                        <i class="bi bi-truck me-2 text-muted"></i>
                                        <span class="text-muted">発送日:</span> 
                                        <span>${KaruruUtils.formatDate(order.shipped_at)}</span>
                                    </p>
                                ` : ''}
                                ${order.delivered_at ? `
                                    <p class="mb-2">
                                        <i class="bi bi-check-circle me-2 text-muted"></i>
                                        <span class="text-muted">配送日:</span> 
                                        <span>${KaruruUtils.formatDate(order.delivered_at)}</span>
                                    </p>
                                ` : ''}
                            </div>
                        </div>
                        <div class="d-flex gap-2 flex-wrap">
                            <a href="${window.CONTEXT_PATH}/order-detail.jsp?id=${order.order_id}" class="btn btn-primary btn-sm">
                                <i class="bi bi-eye me-1"></i>詳細を見る
                            </a>
                            ${order.order_status !== 'cancelled' && order.order_status === 'pending' && order.payment_status === 'pending' ? `
                                <button onclick="processPayment(${order.order_id})" class="btn btn-success btn-sm">
                                    <i class="bi bi-credit-card me-1"></i>支払う
                                </button>
                                <button onclick="cancelOrder(${order.order_id})" class="btn btn-outline-danger btn-sm">
                                    <i class="bi bi-x-circle me-1"></i>キャンセル
                                </button>
                            ` : ''}
                            ${order.order_status === 'pending' && order.payment_status === 'paid' ? `
                                <span class="badge bg-info">支払い済み - 確定待ち</span>
                            ` : ''}
                            ${order.order_status === 'shipped' && order.tracking_number ? `
                                <a href="#" onclick="trackOrder('${escapeHtml(order.tracking_number)}', '${escapeHtml(order.courier || '')}'); return false;" class="btn btn-outline-info btn-sm">
                                    <i class="bi bi-truck me-1"></i>配送を追跡
                                </a>
                            ` : ''}
                        </div>
                    </div>
                </div>
            `).join('');
        } else {
            container.innerHTML = `
                <div class="text-center py-5 text-muted">
                    <i class="bi bi-receipt" style="font-size: 4rem; opacity: 0.5;"></i>
                    <p class="mt-3 mb-0">注文履歴がありません</p>
                </div>
            `;
        }
    } catch (error) {
        console.error('Error loading orders:', error);
        const container = document.getElementById('ordersList');
        if (container) {
            container.innerHTML = `
                <div class="text-center py-5 text-danger">
                    <i class="bi bi-exclamation-triangle" style="font-size: 3rem;"></i>
                    <p class="mt-3 mb-0">注文履歴の読み込みに失敗しました</p>
                    <p class="text-muted small mt-2">${error.message || ''}</p>
                </div>
            `;
        }
    }
}

function getOrderStatusText(status) {
    const statuses = {
        'pending': '保留中',
        'confirmed': '確定済み',
        'processing': '処理中',
        'shipped': '発送済み',
        'delivered': '配送済み',
        'cancelled': 'キャンセル',
        'refunded': '返金済み'
    };
    return statuses[status] || status;
}

function getOrderStatusBadge(status) {
    const badges = {
        'pending': 'warning',
        'confirmed': 'info',
        'processing': 'primary',
        'shipped': 'info',
        'delivered': 'success',
        'cancelled': 'danger',
        'refunded': 'secondary'
    };
    return badges[status] || 'secondary';
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

async function processPayment(orderId) {
    try {
        // Get payment method from order or prompt user
        const paymentMethod = prompt('支払い方法を選択してください:\n1. wallet (ウォレット)\n2. credit_card (クレジットカード)\n3. bank_transfer (銀行振込)\n4. cod (代金引換)', 'wallet');
        
        if (!paymentMethod) {
            return;
        }
        
        KaruruUtils.showNotification('支払いを処理しています...', 'info');
        
        const response = await fetch(`${window.CONTEXT_PATH}/Payment`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: new URLSearchParams({
                action: 'processPayment',
                order_id: orderId.toString(),
                payment_method: paymentMethod
            })
        });
        
        const result = await response.json();
        if (result.success !== false) {
            const paymentStatus = result.payment_status || 'pending';
            if (paymentStatus === 'paid') {
                KaruruUtils.showNotification('支払いが完了しました', 'success');
            } else {
                KaruruUtils.showNotification('支払い処理が完了しました（確認待ち）', 'info');
            }
            loadOrders();
        } else {
            KaruruUtils.showNotification(result.error || result.message || '支払い処理に失敗しました', 'danger');
        }
    } catch (error) {
        console.error('Error processing payment:', error);
        KaruruUtils.showNotification('支払い処理中にエラーが発生しました', 'danger');
    }
}

async function cancelOrder(orderId) {
    if (!KaruruUtils.confirmDialog('この注文をキャンセルしますか？')) {
        return;
    }
    
    try {
        KaruruUtils.showNotification('注文をキャンセルしています...', 'info');
        
        const response = await fetch(`${window.CONTEXT_PATH}/OrderServlet`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: new URLSearchParams({
                action: 'updateOrderStatus',
                order_id: orderId.toString(),
                status: 'cancelled'
            })
        });
        
        const result = await response.json();
        if (result.success !== false) {
            KaruruUtils.showNotification('注文をキャンセルしました', 'success');
            const statusFilter = document.getElementById('statusFilter');
            loadOrders(statusFilter ? statusFilter.value : '');
        } else {
            KaruruUtils.showNotification(result.error || result.message || 'キャンセルできませんでした', 'danger');
        }
    } catch (error) {
        console.error('Error cancelling order:', error);
    }
}

function trackOrder(trackingNumber, courier) {
    // Open tracking in new window or show modal
    const courierUrls = {
        'ヤマト運輸': 'https://toi.kuronekoyamato.co.jp/cgi-bin/tneko/tneko0010.do',
        '佐川急便': 'https://k2k.sagawa-exp.co.jp/p/web/okurijoinput',
        '日本郵便': 'https://trackings.post.japanpost.jp/services/srv/search'
    };
    
    const url = courierUrls[courier] || `https://www.google.com/search?q=${encodeURIComponent(trackingNumber + ' ' + courier)}`;
    window.open(url, '_blank');
}
