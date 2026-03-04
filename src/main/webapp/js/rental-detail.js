// Rental Detail Page JavaScript

document.addEventListener('DOMContentLoaded', function() {
    const urlParams = new URLSearchParams(window.location.search);
    const rentalId = urlParams.get('id');
    
    if (rentalId) {
        loadRentalDetail(rentalId);
    } else {
        showError('レンタルIDが指定されていません');
    }
});

async function loadRentalDetail(rentalId) {
    try {
        const data = await KaruruUtils.apiFetch(
            `${window.CONTEXT_PATH}/RentalServlet?action=getRentalDetail&rental_id=${rentalId}`
        );
        
        if (data && data.success !== false && data.rental) {
            renderRentalDetail(data.rental);
        } else {
            showError(data?.error || 'レンタルが見つかりません');
        }
    } catch (error) {
        console.error('Error loading rental detail:', error);
        showError(error.message || 'レンタル詳細の読み込みに失敗しました');
    }
}

function renderRentalDetail(rental) {
    const container = document.getElementById('rentalDetail');
    if (!container) return;
    
    const contextPath = window.CONTEXT_PATH || '';
    const imageUrl = rental.product_image 
        ? (rental.product_image.startsWith('http') ? rental.product_image : `${contextPath}/${rental.product_image}`)
        : `${contextPath}/img/default-product.png`;
    
    const statusLabels = {
        'pending': '保留中',
        'confirmed': '確定済み',
        'active': '利用中',
        'completed': '完了',
        'cancelled': 'キャンセル'
    };
    const paymentLabels = {
        'pending': '未払い',
        'paid': '支払済み',
        'refunded': '返金済み'
    };
    
    const startDate = rental.start_date ? new Date(rental.start_date).toLocaleDateString('ja-JP') : '-';
    const endDate = rental.end_date ? new Date(rental.end_date).toLocaleDateString('ja-JP') : '-';
    const createdAt = rental.created_at ? new Date(rental.created_at).toLocaleDateString('ja-JP') : '-';
    
    const isRenter = rental.is_renter === true;
    const isOwner = rental.is_owner === true;
    
    let actionButtons = '';
    if (isRenter) {
        if (rental.status !== 'cancelled' && rental.payment_status === 'paid' && rental.owner_id) {
            actionButtons += `
                <a href="${contextPath}/messages.jsp?user_id=${rental.owner_id}&product_id=${rental.product_id}&type=product" 
                   class="btn btn-primary">
                    <i class="bi bi-chat-dots me-2"></i>出品者に連絡
                </a>
            `;
        }
        if (rental.status === 'confirmed' || rental.status === 'active') {
            actionButtons += `
                <button onclick="completeRental(${rental.rental_id})" class="btn btn-success">
                    <i class="bi bi-check2-circle me-2"></i>返却完了
                </button>
            `;
        }
    }
    if (isOwner) {
        if (rental.status === 'pending' && rental.payment_status === 'paid') {
            actionButtons += `
                <button onclick="confirmRental(${rental.rental_id})" class="btn btn-primary">
                    <i class="bi bi-check-circle me-2"></i>レンタルを確定
                </button>
            `;
        }
        if (rental.status !== 'cancelled' && rental.renter_id) {
            actionButtons += `
                <a href="${contextPath}/messages.jsp?user_id=${rental.renter_id}&product_id=${rental.product_id}&type=product" 
                   class="btn btn-outline-primary">
                    <i class="bi bi-chat-dots me-2"></i>借り手に連絡
                </a>
            `;
        }
    }
    
    const roleLabel = isRenter ? '借り手' : '貸し手';
    
    container.innerHTML = `
        <div class="card-body p-4 p-md-5">
            <div class="row g-4">
                <div class="col-lg-5">
                    <img src="${imageUrl}" class="rental-detail-image w-100" alt="${escapeHtml(rental.product_name || '')}"
                         onerror="this.src='${contextPath}/img/default-product.png'">
                    <div class="d-flex gap-2 mt-2 flex-wrap">
                        <span class="badge bg-${getStatusBadge(rental.status)} px-3 py-2">${statusLabels[rental.status] || rental.status}</span>
                        <span class="badge bg-${getPaymentBadge(rental.payment_status)} px-3 py-2">${paymentLabels[rental.payment_status] || rental.payment_status}</span>
                    </div>
                </div>
                <div class="col-lg-7">
                    <div class="mb-3">
                        <span class="text-muted small">レンタル番号</span>
                        <p class="mb-0 fw-bold">${escapeHtml(rental.rental_number || '')}</p>
                    </div>
                    <h4 class="mb-4 fw-bold text-primary">${escapeHtml(rental.product_name || '商品名不明')}</h4>
                    
                    <div class="rental-detail-info-card mb-4">
                        <div class="rental-detail-info-row">
                            <span class="text-muted"><i class="bi bi-calendar-range me-2"></i>レンタル期間</span>
                            <span class="fw-semibold">${startDate} ～ ${endDate}</span>
                        </div>
                        <div class="rental-detail-info-row">
                            <span class="text-muted"><i class="bi bi-clock me-2"></i>予約日</span>
                            <span class="fw-semibold">${createdAt}</span>
                        </div>
                        <div class="rental-detail-info-row">
                            <span class="text-muted"><i class="bi bi-box me-2"></i>数量</span>
                            <span class="fw-semibold">${rental.quantity || 1}</span>
                        </div>
                        <div class="rental-detail-info-row">
                            <span class="text-muted"><i class="bi bi-person me-2"></i>あなたの役割</span>
                            <span class="badge bg-primary">${roleLabel}</span>
                        </div>
                    </div>
                    
                    <div class="d-flex align-items-center justify-content-between flex-wrap gap-2 mb-4">
                        <div>
                            <span class="text-muted d-block small">レンタル料金</span>
                            <span class="rental-detail-price">${KaruruUtils.formatPrice ? KaruruUtils.formatPrice(rental.total_amount || 0) : '¥' + (rental.total_amount || 0).toLocaleString()}</span>
                        </div>
                        <a href="${contextPath}/product-detail.jsp?id=${rental.product_id}" class="btn btn-outline-info">
                            <i class="bi bi-box-arrow-up-right me-2"></i>商品を見る
                        </a>
                    </div>
                    
                    <div class="rental-detail-actions">
                        <h6 class="mb-3 text-muted"><i class="bi bi-lightning me-2"></i>アクション</h6>
                        <div class="d-flex gap-2 flex-wrap">
                            ${actionButtons || '<span class="text-muted">現在実行可能なアクションはありません</span>'}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    `;
}

function getStatusBadge(status) {
    const map = { pending: 'warning', confirmed: 'info', active: 'success', completed: 'secondary', cancelled: 'danger' };
    return map[status] || 'secondary';
}

function getPaymentBadge(status) {
    const map = { pending: 'warning', paid: 'success', refunded: 'info' };
    return map[status] || 'secondary';
}

async function confirmRental(rentalId) {
    try {
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/RentalServlet`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams({ action: 'confirmRental', rental_id: String(rentalId) })
        });
        if (data && data.success !== false) {
            KaruruUtils.showNotification(data.message || 'レンタルを確定しました', 'success');
            loadRentalDetail(rentalId);
        } else {
            throw new Error(data?.error || data?.message || '確定に失敗しました');
        }
    } catch (error) {
        KaruruUtils.showNotification(error.message || '確定に失敗しました', 'danger');
    }
}

async function completeRental(rentalId) {
    if (!confirm('レンタル商品を返却しましたか？')) return;
    try {
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/RentalServlet`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams({ action: 'completeRental', rental_id: String(rentalId) })
        });
        if (data && data.success !== false) {
            KaruruUtils.showNotification(data.message || '返却が完了しました', 'success');
            loadRentalDetail(rentalId);
        } else {
            throw new Error(data?.error || data?.message || '返却の登録に失敗しました');
        }
    } catch (error) {
        KaruruUtils.showNotification(error.message || '返却の登録に失敗しました', 'danger');
    }
}

function showError(message) {
    const container = document.getElementById('rentalDetail');
    if (container) {
        container.innerHTML = `
            <div class="card-body p-5 text-center">
                <div class="py-4">
                    <i class="bi bi-exclamation-triangle text-danger" style="font-size: 4rem; opacity: 0.6;"></i>
                </div>
                <h5 class="text-danger mb-3">${escapeHtml(message)}</h5>
                <a href="${window.CONTEXT_PATH || ''}/rentals.jsp" class="btn btn-primary">
                    <i class="bi bi-arrow-left me-2"></i>レンタル履歴に戻る
                </a>
            </div>
        `;
    }
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
