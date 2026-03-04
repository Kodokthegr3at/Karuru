// Order Confirmation page JavaScript

document.addEventListener('DOMContentLoaded', function() {
    loadOrderConfirmation();
    
    // Setup view order detail button
    const viewOrderDetailBtn = document.getElementById('viewOrderDetailBtn');
    if (viewOrderDetailBtn) {
        viewOrderDetailBtn.addEventListener('click', function() {
            if (window.orderId) {
                window.location.href = `${window.CONTEXT_PATH}/order-detail.jsp?id=${window.orderId}`;
            }
        });
    }
});

async function loadOrderConfirmation() {
    const orderId = window.orderId;
    
    console.log('Loading order confirmation for order ID:', orderId);
    
    if (!orderId) {
        // No order ID, show error message
        const content = document.getElementById('orderConfirmContent');
        if (content) {
            content.innerHTML = `
                <div class="text-center py-5 text-danger">
                    <i class="bi bi-exclamation-triangle" style="font-size: 3rem;"></i>
                    <p class="mt-3 mb-0">注文情報が見つかりませんでした</p>
                    <a href="${window.CONTEXT_PATH}/orders.jsp" class="btn btn-outline-primary mt-3">
                        注文履歴に戻る
                    </a>
                </div>
            `;
        }
        return;
    }
    
    try {
        const url = `${window.CONTEXT_PATH}/OrderServlet?action=getOrderDetails&order_id=${orderId}`;
        console.log('Fetching order details from:', url);
        
        const response = await fetch(url, {
            method: 'GET',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            },
            credentials: 'same-origin'
        });
        
        console.log('Response status:', response.status);
        
        if (!response.ok) {
            const errorText = await response.text();
            console.error('Error response:', errorText);
            let serverMessage = '';
            try {
                const errData = JSON.parse(errorText);
                serverMessage = errData.error || errData.message || '';
            } catch (_) {}
            const msg = serverMessage || `HTTP error! status: ${response.status}`;
            throw new Error(msg);
        }
        
        const data = await response.json();
        console.log('Order data received:', data);
        
        if (data && data.order_id) {
            // getOrderDetails returns order data directly, not wrapped in 'order' property
            renderOrderConfirmation(data);
        } else if (data && data.order) {
            // Sometimes wrapped in 'order' property
            renderOrderConfirmation(data.order);
        } else {
            console.error('Invalid order data:', data);
            showError('注文情報の読み込みに失敗しました');
        }
    } catch (error) {
        console.error('Error loading order confirmation:', error);
        console.error('Error stack:', error.stack);
        showError('注文情報の読み込みに失敗しました: ' + (error.message || ''));
    }
}

function renderOrderConfirmation(order) {
    const content = document.getElementById('orderConfirmContent');
    if (!content) return;
    
    const orderNumber = order.order_number || `ORD-${order.order_id}`;
    const orderDate = order.created_at ? formatDate(order.created_at) : '';
    const paymentMethod = getPaymentMethodLabel(order.payment_method);
    const orderStatus = getOrderStatusLabel(order.order_status);
    const paymentStatus = getPaymentStatusLabel(order.payment_status);
    
    // Format prices
    const subtotal = formatPrice(order.subtotal || 0);
    const shipping = formatPrice(order.shipping_cost || 0);
    const tax = formatPrice(order.tax_amount || 0);
    const discount = formatPrice(order.discount_amount || 0);
    const total = formatPrice(order.total_amount || 0);
    
    // Render order items
    const itemsHtml = order.items && order.items.length > 0 ? order.items.map(item => `
        <div class="d-flex align-items-center border-bottom pb-3 mb-3" style="border-color: var(--border-color);">
            <div class="flex-grow-1">
                <h6 class="mb-1">${escapeHtml(item.product_name || '商品')}</h6>
                <small class="text-muted">
                    数量: ${item.quantity || 1} × ${formatPrice(item.price || 0)}
                </small>
            </div>
            <div class="text-end">
                <strong>${formatPrice((item.subtotal || item.price || 0) * (item.quantity || 1))}</strong>
            </div>
        </div>
    `).join('') : '<p class="text-muted">商品情報が見つかりませんでした</p>';
    
    content.innerHTML = `
        <!-- Order Info -->
        <div class="row mb-4">
            <div class="col-md-6 mb-3">
                <h6 class="text-muted mb-2">注文番号</h6>
                <p class="fw-bold mb-0">${escapeHtml(orderNumber)}</p>
            </div>
            <div class="col-md-6 mb-3">
                <h6 class="text-muted mb-2">注文日時</h6>
                <p class="mb-0">${orderDate}</p>
            </div>
            <div class="col-md-6 mb-3">
                <h6 class="text-muted mb-2">支払い方法</h6>
                <p class="mb-0">${paymentMethod}</p>
            </div>
            <div class="col-md-6 mb-3">
                <h6 class="text-muted mb-2">注文ステータス</h6>
                <span class="badge bg-primary">${orderStatus}</span>
            </div>
            <div class="col-md-6 mb-3">
                <h6 class="text-muted mb-2">支払いステータス</h6>
                <span class="badge ${order.payment_status === 'paid' ? 'bg-success' : 'bg-warning'}">${paymentStatus}</span>
            </div>
        </div>
        
        <!-- Order Items -->
        <div class="mb-4">
            <h6 class="fw-bold mb-3">注文商品</h6>
            ${itemsHtml}
        </div>
        
        <!-- Order Summary -->
        <div class="border-top pt-3" style="border-color: var(--border-color);">
            <div class="d-flex justify-content-between mb-2">
                <span class="text-muted">小計</span>
                <span>${subtotal}</span>
            </div>
            ${discount !== '¥0' ? `
            <div class="d-flex justify-content-between mb-2">
                <span class="text-muted">割引</span>
                <span class="text-success">-${discount}</span>
            </div>
            ` : ''}
            <div class="d-flex justify-content-between mb-2">
                <span class="text-muted">送料</span>
                <span>${shipping}</span>
            </div>
            <div class="d-flex justify-content-between mb-2">
                <span class="text-muted">税金</span>
                <span>${tax}</span>
            </div>
            <div class="d-flex justify-content-between border-top pt-3 mt-3" style="border-color: var(--border-color);">
                <span class="fw-bold fs-5">合計</span>
                <span class="fw-bold fs-5 text-primary">${total}</span>
            </div>
        </div>
    `;
    
    // Show view order detail button
    const viewOrderDetailBtn = document.getElementById('viewOrderDetailBtn');
    if (viewOrderDetailBtn) {
        viewOrderDetailBtn.style.display = 'block';
    }
}

function showError(message) {
    const content = document.getElementById('orderConfirmContent');
    if (content) {
        content.innerHTML = `
            <div class="text-center py-5 text-danger">
                <i class="bi bi-exclamation-triangle" style="font-size: 3rem;"></i>
                <p class="mt-3 mb-0">${escapeHtml(message)}</p>
                <a href="${window.CONTEXT_PATH}/orders.jsp" class="btn btn-outline-primary mt-3">
                    注文履歴に戻る
                </a>
            </div>
        `;
    }
}

function formatPrice(amount) {
    if (amount == null || amount === undefined) return '¥0';
    if (typeof amount === 'string') {
        amount = parseFloat(amount);
    }
    if (isNaN(amount)) return '¥0';
    return '¥' + amount.toLocaleString('ja-JP');
}

function formatDate(dateString) {
    if (!dateString) return '';
    try {
        const date = new Date(dateString);
        if (isNaN(date.getTime())) return dateString;
        return date.toLocaleString('ja-JP', {
            year: 'numeric',
            month: 'long',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    } catch (e) {
        return dateString;
    }
}

function getPaymentMethodLabel(method) {
    const labels = {
        'wallet': 'ウォレット',
        'bank_transfer': '銀行振込',
        'cod': '代金引換',
        'credit_card': 'クレジットカード',
        'ewallet': '電子ウォレット'
    };
    return labels[method] || method || '未設定';
}

function getOrderStatusLabel(status) {
    const labels = {
        'pending': '保留中',
        'confirmed': '確定済み',
        'processing': '処理中',
        'shipped': '発送済み',
        'delivered': '配送済み',
        'cancelled': 'キャンセル',
        'refunded': '返金済み'
    };
    return labels[status] || status || '不明';
}

function getPaymentStatusLabel(status) {
    const labels = {
        'pending': '保留中',
        'paid': '支払い済み',
        'failed': '支払い失敗',
        'refunded': '返金済み'
    };
    return labels[status] || status || '不明';
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

