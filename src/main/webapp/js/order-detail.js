// Order Detail Page JavaScript

document.addEventListener('DOMContentLoaded', function() {
    const urlParams = new URLSearchParams(window.location.search);
    const orderId = urlParams.get('id');
    
    if (orderId) {
        loadOrderDetail(orderId);
    } else {
        showError('注文IDが指定されていません');
    }
});

async function loadOrderDetail(orderId) {
    try {
        const contextPath = window.CONTEXT_PATH || '';
        const response = await fetch(`${contextPath}/OrderServlet?action=getOrderDetails&order_id=${orderId}`, {
            credentials: 'same-origin'
        });
        const text = await response.text();
        let data = {};
        try {
            data = JSON.parse(text);
        } catch (e) {
            showError('サーバーからの応答が不正です');
            return;
        }
        
        if (response.ok && data.order_id) {
            renderOrderDetail(data);
        } else {
            showError(data.error || data.message || '注文が見つかりません');
        }
    } catch (error) {
        console.error('Error loading order detail:', error);
        showError('注文の読み込みに失敗しました');
    }
}

function renderOrderDetail(order) {
    const container = document.getElementById('orderDetail');
    if (!container) return;
    
    const contextPath = window.CONTEXT_PATH || '';
    const items = order.items || [];
    
    const statusBadge = getOrderStatusBadge(order.order_status);
    const statusText = getOrderStatusText(order.order_status);
    
    container.innerHTML = `
        <div class="card-header card-header-light">
            <div class="d-flex justify-content-between align-items-center">
                <div>
                    <h5 class="mb-1">注文番号: ${escapeHtml(order.order_number || order.order_id)}</h5>
                    <small class="text-muted">注文日: ${formatDate(order.created_at)}</small>
                </div>
                <span class="badge bg-${statusBadge} fs-6">${statusText}</span>
            </div>
        </div>
        <div class="card-body p-4">
            <div class="row">
                <div class="col-lg-8">
                    <div class="mb-4">
                        <h6 class="text-primary mb-3">
                            <i class="bi bi-box-seam me-2"></i>注文商品
                        </h6>
                        <div class="table-responsive">
                            <table class="table table-light table-hover">
                                <thead>
                                    <tr>
                                        <th>商品</th>
                                        <th>数量</th>
                                        <th class="text-end">価格</th>
                                        <th class="text-end">小計</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    ${items.map(item => {
                                        const imageUrl = item.image_url ? 
                                            (item.image_url.startsWith('http') ? item.image_url : `${contextPath}/${item.image_url}`) :
                                            `${contextPath}/img/default-product.png`;
                                        return `
                                            <tr>
                                                <td>
                                                    <div class="d-flex align-items-center">
                                                        <img src="${imageUrl}" 
                                                             alt="${escapeHtml(item.product_name || '商品')}" 
                                                             class="me-3" 
                                                             style="width: 60px; height: 60px; object-fit: cover; border-radius: 8px;"
                                                             onerror="this.src='${contextPath}/img/default-product.png'">
                                                        <div>
                                                            <a href="${contextPath}/product-detail.jsp?id=${item.product_id}" 
                                                               class="text-decoration-none">
                                                                <strong>${escapeHtml(item.product_name || '商品')}</strong>
                                                            </a>
                                                        </div>
                                                    </div>
                                                </td>
                                                <td>${item.quantity || 1}</td>
                                                <td class="text-end">${formatPrice(item.price || 0)}</td>
                                                <td class="text-end fw-bold">${formatPrice(item.subtotal || (item.price || 0) * (item.quantity || 1))}</td>
                                            </tr>
                                        `;
                                    }).join('')}
                                </tbody>
                            </table>
                        </div>
                    </div>
                    
                    ${order.shipping_address ? `
                        <div class="mb-4">
                            <h6 class="text-primary mb-3">
                                <i class="bi bi-geo-alt me-2"></i>配送先住所
                            </h6>
                            <div class="card card-light p-3">
                                <p class="mb-1"><strong>${escapeHtml(order.shipping_address.recipient_name || '')}</strong></p>
                                <p class="mb-1">${escapeHtml(order.shipping_address.phone || '')}</p>
                                <p class="mb-0">
                                    〒${escapeHtml(order.shipping_address.postal_code || '')}<br>
                                    ${escapeHtml(order.shipping_address.prefecture || '')} ${escapeHtml(order.shipping_address.city || '')}<br>
                                    ${escapeHtml(order.shipping_address.address_line1 || '')} ${escapeHtml(order.shipping_address.address_line2 || '')}<br>
                                    ${escapeHtml(order.shipping_address.building_name || '')}
                                </p>
                            </div>
                        </div>
                    ` : ''}
                </div>
                
                <div class="col-lg-4">
                    <div class="card card-light">
                        <div class="card-header card-header-light">
                            <h6 class="mb-0">注文概要</h6>
                        </div>
                        <div class="card-body">
                            <div class="d-flex justify-content-between mb-2">
                                <span>小計:</span>
                                <span>${formatPrice(order.subtotal || 0)}</span>
                            </div>
                            <div class="d-flex justify-content-between mb-2">
                                <span>送料:</span>
                                <span>${formatPrice(order.shipping_cost || 0)}</span>
                            </div>
                            ${order.discount_amount > 0 ? `
                                <div class="d-flex justify-content-between mb-2 text-success">
                                    <span>割引:</span>
                                    <span>-${formatPrice(order.discount_amount || 0)}</span>
                                </div>
                            ` : ''}
                            ${order.tax_amount > 0 ? `
                                <div class="d-flex justify-content-between mb-2">
                                    <span>税金:</span>
                                    <span>${formatPrice(order.tax_amount || 0)}</span>
                                </div>
                            ` : ''}
                            <hr>
                            <div class="d-flex justify-content-between mb-3">
                                <strong>合計:</strong>
                                <strong class="text-primary fs-5">${formatPrice(order.total_amount || 0)}</strong>
                            </div>
                            
                            <div class="mb-3">
                                <small class="text-muted d-block mb-1">支払い方法:</small>
                                <strong>${escapeHtml(order.payment_method || '未指定')}</strong>
                            </div>
                            
                            <div class="mb-3">
                                <small class="text-muted d-block mb-1">支払い状態:</small>
                                <span class="badge bg-${getPaymentStatusBadge(order.payment_status)}">
                                    ${getPaymentStatusText(order.payment_status)}
                                </span>
                            </div>
                            
                            ${order.tracking_number ? `
                                <div class="mb-3">
                                    <small class="text-muted d-block mb-1">追跡番号:</small>
                                    <strong>${escapeHtml(order.tracking_number)}</strong>
                                </div>
                            ` : ''}
                            
                            ${order.courier ? `
                                <div class="mb-3">
                                    <small class="text-muted d-block mb-1">配送業者:</small>
                                    <strong>${escapeHtml(order.courier)}</strong>
                                </div>
                            ` : ''}
                            
                            ${order.shipped_at ? `
                                <div class="mb-3">
                                    <small class="text-muted d-block mb-1">発送日:</small>
                                    <strong>${formatDate(order.shipped_at)}</strong>
                                </div>
                            ` : ''}
                            
                            ${order.delivered_at ? `
                                <div class="mb-3">
                                    <small class="text-muted d-block mb-1">配送日:</small>
                                    <strong>${formatDate(order.delivered_at)}</strong>
                                </div>
                            ` : ''}
                            
                            <!-- Action Buttons (buyer only - hidden for seller) -->
                            <div id="buyerActions" class="mt-4 pt-3 border-top">
                                ${order.order_status !== 'cancelled' && order.order_status === 'pending' && order.payment_status === 'pending' ? `
                                    <button onclick="processPayment(${order.order_id})" class="btn btn-success w-100 mb-2">
                                        <i class="bi bi-credit-card me-2"></i>支払う
                                    </button>
                                    <button onclick="cancelOrder(${order.order_id})" class="btn btn-outline-danger w-100">
                                        <i class="bi bi-x-circle me-2"></i>注文をキャンセル
                                    </button>
                                ` : ''}
                                ${order.order_status === 'pending' && order.payment_status === 'paid' ? `
                                    <div class="alert alert-info mb-0">
                                        <i class="bi bi-info-circle me-2"></i>支払い済み - 売り手の確定待ちです
                                    </div>
                                ` : ''}
                                ${order.order_status === 'shipped' && order.tracking_number ? `
                                    <button onclick="trackOrder('${escapeHtml(order.tracking_number)}', '${escapeHtml(order.courier || '')}')" class="btn btn-info w-100 mb-2">
                                        <i class="bi bi-truck me-2"></i>配送を追跡
                                    </button>
                                ` : ''}
                                ${order.order_status === 'delivered' ? `
                                    <div class="alert alert-success mb-0">
                                        <i class="bi bi-check-circle me-2"></i>注文が完了しました！
                                    </div>
                                ` : ''}
                            </div>
                        </div>
                    </div>
                    
                    <!-- Seller Actions (if user is seller) -->
                    <div id="sellerActions" class="card card-light mt-3" style="display: none;">
                        <div class="card-header card-header-light">
                            <h6 class="mb-0">
                                <i class="bi bi-shop me-2"></i>売り手アクション
                            </h6>
                        </div>
                        <div class="card-body">
                            <div id="sellerActionButtons">
                                <!-- Seller action buttons will be loaded here -->
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    `;
    
    // Check if user is seller and load seller actions
    checkSellerStatus(order);
}

function showError(message) {
    const container = document.getElementById('orderDetail');
    if (container) {
        container.innerHTML = `
            <div class="card-body p-5">
                <div class="text-center">
                    <i class="bi bi-exclamation-triangle text-danger" style="font-size: 4rem; opacity: 0.5;"></i>
                    <p class="mt-3 mb-0 text-danger">${escapeHtml(message)}</p>
                    <a href="${window.CONTEXT_PATH || ''}/orders.jsp" class="btn btn-outline-primary mt-3">
                        <i class="bi bi-arrow-left me-2"></i>注文履歴に戻る
                    </a>
                </div>
            </div>
        `;
    }
}

function getOrderStatusBadge(status) {
    const statusMap = {
        'pending': 'warning',
        'confirmed': 'info',
        'processing': 'primary',
        'shipped': 'info',
        'delivered': 'success',
        'cancelled': 'danger'
    };
    return statusMap[status] || 'secondary';
}

function getOrderStatusText(status) {
    const statusMap = {
        'pending': '保留中',
        'confirmed': '確定済み',
        'processing': '処理中',
        'shipped': '発送済み',
        'delivered': '配送済み',
        'cancelled': 'キャンセル',
        'refunded': '返金済み'
    };
    return statusMap[status] || status;
}

function getPaymentStatusText(status) {
    const statusMap = {
        'pending': '未払い',
        'paid': '支払済み',
        'failed': '失敗',
        'refunded': '返金済み'
    };
    return statusMap[status] || status || '未払い';
}

function getPaymentStatusBadge(status) {
    const badgeMap = {
        'pending': 'warning',
        'paid': 'success',
        'failed': 'danger',
        'refunded': 'info'
    };
    return badgeMap[status] || 'warning';
}

function formatPrice(price) {
    if (typeof KaruruUtils !== 'undefined' && KaruruUtils.formatPrice) {
        return KaruruUtils.formatPrice(price);
    }
    return '¥' + parseInt(price || 0).toLocaleString('ja-JP');
}

function formatDate(dateString) {
    if (!dateString) return '';
    if (typeof KaruruUtils !== 'undefined' && KaruruUtils.formatDate) {
        return KaruruUtils.formatDate(dateString);
    }
    const date = new Date(dateString);
    return date.toLocaleDateString('ja-JP', { 
        year: 'numeric', 
        month: 'long', 
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
    });
}

function checkBuyerSellerStatus(order) {
    const currentUserId = parseInt(window.currentUserId || sessionStorage.getItem('userId') || '0');
    if (!currentUserId) return { isBuyer: false, isSeller: false };
    
    const buyerId = parseInt(order.user_id || 0);
    const items = order.items || [];
    const sellerIds = [...new Set(items.map(item => parseInt(item.seller_id) || 0).filter(id => id > 0))];
    
    const isBuyer = (buyerId === currentUserId);
    const isSeller = sellerIds.includes(currentUserId);
    
    return { isBuyer, isSeller };
}

async function checkSellerStatus(order) {
    try {
        const { isBuyer, isSeller } = checkBuyerSellerStatus(order);
        
        // Show seller actions only when user is seller
        if (isSeller) {
            const sellerActionsDiv = document.getElementById('sellerActions');
            const sellerActionButtons = document.getElementById('sellerActionButtons');
            
            if (sellerActionsDiv && sellerActionButtons) {
                sellerActionsDiv.style.display = 'block';
                renderSellerActions(order, sellerActionButtons);
            }
        }
        
        // Hide buyer actions when user is seller (seller should not see pay/wait buttons)
        const buyerActionsDiv = document.getElementById('buyerActions');
        if (buyerActionsDiv) {
            buyerActionsDiv.style.display = isBuyer ? 'block' : 'none';
        }
    } catch (error) {
        console.error('Error checking seller status:', error);
    }
}

function renderSellerActions(order, container) {
    const orderStatus = order.order_status;
    const paymentStatus = order.payment_status;
    
    let actions = '';
    
    // If payment is paid and order is pending, seller can confirm
    if (paymentStatus === 'paid' && orderStatus === 'pending') {
        actions += `
            <button onclick="confirmOrder(${order.order_id})" class="btn btn-success w-100 mb-2">
                <i class="bi bi-check-circle me-2"></i>注文を確定
            </button>
        `;
    }
    
    // If order is confirmed, seller can start processing
    if (orderStatus === 'confirmed') {
        actions += `
            <button onclick="updateOrderStatus(${order.order_id}, 'processing')" class="btn btn-primary w-100 mb-2">
                <i class="bi bi-gear me-2"></i>処理を開始
            </button>
        `;
    }
    
    // If order is processing, seller can ship
    if (orderStatus === 'processing') {
        actions += `
            <button onclick="showShippingModal(${order.order_id})" class="btn btn-info w-100 mb-2">
                <i class="bi bi-truck me-2"></i>発送する
            </button>
        `;
    }
    
    // If order is shipped, seller can mark as delivered
    if (orderStatus === 'shipped') {
        actions += `
            <button onclick="updateOrderStatus(${order.order_id}, 'delivered')" class="btn btn-success w-100 mb-2">
                <i class="bi bi-check-circle me-2"></i>配送完了を確認
            </button>
        `;
    }
    
    // Seller can cancel order if not delivered
    if (orderStatus !== 'delivered' && orderStatus !== 'cancelled') {
        actions += `
            <button onclick="cancelOrder(${order.order_id})" class="btn btn-outline-danger w-100">
                <i class="bi bi-x-circle me-2"></i>注文をキャンセル
            </button>
        `;
    }
    
    if (!actions) {
        actions = '<p class="text-muted mb-0 small">現在利用可能なアクションがありません</p>';
    }
    
    container.innerHTML = actions;
}

async function confirmOrder(orderId) {
    if (!KaruruUtils.confirmDialog('この注文を確定しますか？確定後、在庫が減算されます。')) {
        return;
    }
    
    try {
        KaruruUtils.showNotification('注文を確定しています...', 'info');
        
        const response = await fetch(`${window.CONTEXT_PATH}/OrderServlet?action=confirmOrder&order_id=${orderId}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: new URLSearchParams({
                action: 'confirmOrder',
                order_id: orderId.toString()
            }),
            credentials: 'same-origin'
        });
        
        const text = await response.text();
        let result = {};
        try { result = JSON.parse(text); } catch (e) {}
        
        if (response.ok && result.success !== false) {
            KaruruUtils.showNotification('注文を確定しました', 'success');
            setTimeout(() => loadOrderDetail(orderId), 1000);
        } else {
            KaruruUtils.showNotification(result.error || result.message || '確定に失敗しました', 'danger');
        }
    } catch (error) {
        console.error('Error confirming order:', error);
        KaruruUtils.showNotification('確定に失敗しました', 'danger');
    }
}

async function updateOrderStatus(orderId, newStatus) {
    const statusMessages = {
        'processing': '処理を開始しますか？',
        'shipped': '発送済みに更新しますか？',
        'delivered': '配送完了を確認しますか？',
        'cancelled': 'この注文をキャンセルしますか？'
    };
    
    if (!KaruruUtils.confirmDialog(statusMessages[newStatus] || 'ステータスを更新しますか？')) {
        return;
    }
    
    try {
        KaruruUtils.showNotification('ステータスを更新しています...', 'info');
        
        const params = new URLSearchParams({
            action: 'updateOrderStatus',
            order_id: orderId.toString(),
            status: newStatus
        });
        
        const response = await fetch(`${window.CONTEXT_PATH}/OrderServlet?action=updateOrderStatus&order_id=${orderId}&status=${encodeURIComponent(newStatus)}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: params,
            credentials: 'same-origin'
        });
        
        const text = await response.text();
        let result = {};
        try { result = JSON.parse(text); } catch (e) {}
        
        if (response.ok && result.success !== false) {
            const statusTexts = {
                'processing': '処理を開始しました',
                'shipped': '発送済みに更新しました',
                'delivered': '配送完了を確認しました',
                'cancelled': '注文をキャンセルしました'
            };
            KaruruUtils.showNotification(statusTexts[newStatus] || '更新しました', 'success');
            setTimeout(() => loadOrderDetail(orderId), 1000);
        } else {
            const errMsg = result.error || result.message || '更新に失敗しました';
            console.warn('updateOrderStatus failed:', response.status, errMsg, result);
            KaruruUtils.showNotification(errMsg, 'danger');
        }
    } catch (error) {
        console.error('Error updating order status:', error);
        KaruruUtils.showNotification('更新に失敗しました', 'danger');
    }
}

function showShippingModal(orderId) {
    // Create and show shipping modal
    const modalHtml = `
        <div class="modal fade" id="shippingModal" tabindex="-1">
            <div class="modal-dialog">
                <div class="modal-content card-light">
                    <div class="modal-header card-header-light">
                        <h5 class="modal-title">発送情報を入力</h5>
                        <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                    </div>
                    <div class="modal-body">
                        <div class="mb-3">
                            <label for="trackingNumber" class="form-label">追跡番号 <span class="text-danger">*</span></label>
                            <input type="text" class="form-control form-control-light" id="trackingNumber" 
                                   placeholder="追跡番号を入力" required>
                        </div>
                        <div class="mb-3">
                            <label for="courier" class="form-label">配送業者 <span class="text-danger">*</span></label>
                            <select class="form-select form-control-light" id="courier" required>
                                <option value="">選択してください</option>
                                <option value="ヤマト運輸">ヤマト運輸</option>
                                <option value="佐川急便">佐川急便</option>
                                <option value="日本郵便">日本郵便</option>
                                <option value="その他">その他</option>
                            </select>
                        </div>
                    </div>
                    <div class="modal-footer card-header-light">
                        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">キャンセル</button>
                        <button type="button" class="btn btn-primary" onclick="submitShipping(${orderId})">
                            <i class="bi bi-check-circle me-2"></i>発送する
                        </button>
                    </div>
                </div>
            </div>
        </div>
    `;
    
    // Remove existing modal if any
    const existingModal = document.getElementById('shippingModal');
    if (existingModal) {
        existingModal.remove();
    }
    
    // Add modal to body
    document.body.insertAdjacentHTML('beforeend', modalHtml);
    
    // Show modal
    const modal = new bootstrap.Modal(document.getElementById('shippingModal'));
    modal.show();
    
    // Remove modal from DOM when hidden
    document.getElementById('shippingModal').addEventListener('hidden.bs.modal', function() {
        this.remove();
    });
}

async function submitShipping(orderId) {
    const trackingNumber = document.getElementById('trackingNumber')?.value;
    const courier = document.getElementById('courier')?.value;
    
    if (!trackingNumber || !courier) {
        return;
    }
    
    try {
        KaruruUtils.showNotification('発送情報を更新しています...', 'info');
        
        const params = new URLSearchParams({
            action: 'updateOrderStatus',
            order_id: orderId.toString(),
            status: 'shipped',
            tracking_number: trackingNumber,
            courier: courier
        });
        
        const response = await fetch(`${window.CONTEXT_PATH}/OrderServlet?action=updateOrderStatus&order_id=${orderId}&status=shipped`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: params,
            credentials: 'same-origin'
        });
        
        const text = await response.text();
        let result = {};
        try { result = JSON.parse(text); } catch (e) {}
        
        if (response.ok && result.success !== false) {
            const modal = bootstrap.Modal.getInstance(document.getElementById('shippingModal'));
            if (modal) modal.hide();
            KaruruUtils.showNotification('発送情報を更新しました', 'success');
            setTimeout(() => loadOrderDetail(orderId), 1000);
        } else {
            KaruruUtils.showNotification(result.error || result.message || '発送情報の更新に失敗しました', 'danger');
        }
    } catch (error) {
        console.error('Error submitting shipping:', error);
        KaruruUtils.showNotification('発送情報の更新に失敗しました', 'danger');
    }
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
            setTimeout(() => {
                loadOrderDetail(orderId);
            }, 1000);
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
        
        const params = new URLSearchParams({
            action: 'updateOrderStatus',
            order_id: orderId.toString(),
            status: 'cancelled'
        });
        
        const response = await fetch(`${window.CONTEXT_PATH}/OrderServlet?action=updateOrderStatus&order_id=${orderId}&status=cancelled`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: params,
            credentials: 'same-origin'
        });
        
        const text = await response.text();
        let result = {};
        try { result = JSON.parse(text); } catch (e) {}
        
        if (response.ok && result.success !== false) {
            KaruruUtils.showNotification('注文をキャンセルしました', 'success');
            setTimeout(() => loadOrderDetail(orderId), 1000);
        } else {
            KaruruUtils.showNotification(result.error || result.message || 'キャンセルに失敗しました', 'danger');
        }
    } catch (error) {
        console.error('Error cancelling order:', error);
        KaruruUtils.showNotification('キャンセルに失敗しました', 'danger');
    }
}

function trackOrder(trackingNumber, courier) {
    const courierUrls = {
        'ヤマト運輸': 'https://toi.kuronekoyamato.co.jp/cgi-bin/tneko/tneko0010.do',
        '佐川急便': 'https://k2k.sagawa-exp.co.jp/p/web/okurijoinput',
        '日本郵便': 'https://trackings.post.japanpost.jp/services/srv/search'
    };
    
    const url = courierUrls[courier] || `https://www.google.com/search?q=${encodeURIComponent(trackingNumber + ' ' + courier)}`;
    window.open(url, '_blank');
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

