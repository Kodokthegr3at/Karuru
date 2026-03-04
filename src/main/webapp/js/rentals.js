// Rentals list page JavaScript

let currentRentals = [];
let cancelRentalId = null;
let payRentalId = null;
let payRentalAmount = 0;
let completeRentalId = null;

document.addEventListener('DOMContentLoaded', function() {
    loadRentals();
    
    // Setup status filter
    const statusFilter = document.getElementById('statusFilter');
    if (statusFilter) {
        const urlParams = new URLSearchParams(window.location.search);
        const statusParam = urlParams.get('status');
        if (statusParam) {
            statusFilter.value = statusParam;
        }
        statusFilter.addEventListener('change', function() {
            loadRentals();
        });
    }
    
    // Setup role toggle (renter/owner)
    document.querySelectorAll('input[name="rentalRole"]').forEach(function(radio) {
        radio.addEventListener('change', function() {
            loadRentals();
        });
    });
    
    // Setup cancel modal
    const cancelModal = document.getElementById('cancelRentalModal');
    if (cancelModal) {
        cancelModal.addEventListener('show.bs.modal', function(event) {
            const button = event.relatedTarget;
            cancelRentalId = button.getAttribute('data-rental-id');
        });
        
        const confirmCancelBtn = document.getElementById('confirmCancelBtn');
        if (confirmCancelBtn) {
            confirmCancelBtn.addEventListener('click', function() {
                if (cancelRentalId) {
                    cancelRental(cancelRentalId);
                }
            });
        }
    }
    
    // Setup payment modal
    const paymentModal = document.getElementById('paymentRentalModal');
    if (paymentModal) {
        paymentModal.addEventListener('show.bs.modal', function(event) {
            const button = event.relatedTarget;
            if (button) {
                payRentalId = button.getAttribute('data-rental-id');
                payRentalAmount = parseFloat(button.getAttribute('data-rental-amount') || 0);
                const amountEl = document.getElementById('paymentRentalAmount');
                if (amountEl) {
                    amountEl.textContent = KaruruUtils.formatPrice ? KaruruUtils.formatPrice(payRentalAmount) : '¥' + payRentalAmount.toLocaleString();
                }
            }
        });
        
        const confirmPaymentBtn = document.getElementById('confirmPaymentBtn');
        if (confirmPaymentBtn) {
            confirmPaymentBtn.addEventListener('click', function() {
                if (payRentalId) {
                    payRental(payRentalId);
                }
            });
        }
    }
    
    // Setup complete rental modal
    const completeModal = document.getElementById('completeRentalModal');
    if (completeModal) {
        completeModal.addEventListener('show.bs.modal', function(event) {
            const button = event.relatedTarget;
            completeRentalId = button ? button.getAttribute('data-rental-id') : null;
        });
        const confirmCompleteBtn = document.getElementById('confirmCompleteBtn');
        if (confirmCompleteBtn) {
            confirmCompleteBtn.addEventListener('click', function() {
                if (completeRentalId) {
                    completeRental(completeRentalId);
                }
            });
        }
    }
});

async function loadRentals() {
    try {
        const statusFilter = document.getElementById('statusFilter');
        const status = statusFilter ? statusFilter.value : '';
        const roleRadio = document.querySelector('input[name="rentalRole"]:checked');
        const role = roleRadio ? roleRadio.value : 'renter';
        
        let url = `${window.CONTEXT_PATH}/RentalServlet?action=getRentals`;
        if (role === 'owner') {
            url += '&role=owner';
        }
        if (status) {
            url += `&status=${encodeURIComponent(status)}`;
        }
        
        const data = await KaruruUtils.apiFetch(url);
        let rentals = KaruruUtils.extractData(data, 'rentals') || [];
        
        currentRentals = rentals;
        renderRentals(rentals, role);
        
    } catch (error) {
        console.error('Error loading rentals:', error);
        const container = document.getElementById('rentalsList');
        if (container) {
            container.innerHTML = `
                <div class="alert alert-danger">
                    <i class="bi bi-exclamation-triangle me-2"></i>レンタル履歴の読み込みに失敗しました
                </div>
            `;
        }
    }
}

function renderRentals(rentals, role) {
    const container = document.getElementById('rentalsList');
    if (!container) return;
    role = role || 'renter';
    const isOwnerView = role === 'owner';
    
    const statusFilter = document.getElementById('statusFilter');
    const hasFilter = statusFilter && statusFilter.value;
    const filterLabels = {
        'pending': '保留中',
        'confirmed': '確定済み',
        'active': '利用中',
        'completed': '完了',
        'cancelled': 'キャンセル'
    };
    const filterLabel = hasFilter ? (filterLabels[statusFilter.value] || statusFilter.value) : '';
    
    if (!rentals || rentals.length === 0) {
        const emptyTitle = hasFilter 
            ? `${filterLabel}のレンタルはありません` 
            : (isOwnerView ? '貸し出し中のレンタルはありません' : 'レンタル履歴がありません');
        const emptyDesc = hasFilter 
            ? '他のフィルターを試すか、すべてのレンタルを表示してください' 
            : (isOwnerView ? 'レンタル可能な商品を出品すると、ここに表示されます' : 'レンタル可能な商品を探してみましょう');
        container.innerHTML = `
            <div class="rental-empty-state">
                <i class="bi bi-calendar-x text-muted" style="font-size: 5rem; opacity: 0.5;"></i>
                <h4 class="mt-4 mb-3">${emptyTitle}</h4>
                <p class="text-muted mb-4">${emptyDesc}</p>
                <a href="${window.CONTEXT_PATH}/${isOwnerView ? 'create-listing.jsp' : 'rental.jsp'}" class="btn btn-primary btn-lg">
                    <i class="bi bi-${isOwnerView ? 'plus-circle' : 'calendar-check'} me-2"></i>${isOwnerView ? '商品を出品' : 'レンタルを予約'}
                </a>
            </div>
        `;
        return;
    }
    
    container.innerHTML = rentals.map(rental => {
        const imageUrl = rental.product_image || '/img/default-product.png';
        const fullImageUrl = imageUrl.startsWith('/') ? `${window.CONTEXT_PATH}${imageUrl}` : 
                            (imageUrl.startsWith('http') ? imageUrl : `${window.CONTEXT_PATH}/${imageUrl}`);
        
        const statusBadge = getStatusBadge(rental.status);
        const paymentBadge = getPaymentBadge(rental.payment_status);
        
        const startDate = rental.start_date ? new Date(rental.start_date).toLocaleDateString('ja-JP') : '-';
        const endDate = rental.end_date ? new Date(rental.end_date).toLocaleDateString('ja-JP') : '-';
        const createdAt = rental.created_at ? new Date(rental.created_at).toLocaleDateString('ja-JP') : '-';
        
        const canCancel = rental.status === 'pending' || rental.status === 'confirmed';
        
        return `
            <div class="rental-history-card">
                <div class="row g-0">
                    <div class="col-md-2 col-sm-3 p-3">
                        <img src="${fullImageUrl}" 
                             class="rental-item-image" 
                             alt="${escapeHtml(rental.product_name || '')}"
                             onerror="this.src='${window.CONTEXT_PATH}/img/default-product.png'">
                    </div>
                    <div class="col-md-10 col-sm-9">
                        <div class="rental-item-info">
                            <div class="rental-item-header">
                                <div class="flex-grow-1">
                                    <h6 class="rental-item-title mb-2">
                                        <i class="bi bi-box-seam me-2 text-primary"></i>${escapeHtml(rental.product_name || '商品名不明')}
                                    </h6>
                                    <div class="rental-item-meta">
                                        <div class="mb-1">
                                            <i class="bi bi-hash me-1"></i><strong>レンタル番号:</strong> ${escapeHtml(rental.rental_number || '')}
                                        </div>
                                        <div class="mb-1">
                                            <i class="bi bi-calendar-range me-1"></i><strong>期間:</strong> ${startDate} ～ ${endDate}
                                        </div>
                                        <div>
                                            <i class="bi bi-clock me-1"></i><strong>予約日:</strong> ${createdAt}
                                        </div>
                                    </div>
                                </div>
                                <div class="text-end ms-3">
                                    <div class="mb-2">${statusBadge}</div>
                                    <div class="mb-3">${paymentBadge}</div>
                                    <div class="rental-item-price">${KaruruUtils.formatPrice(rental.total_amount || 0)}</div>
                                </div>
                            </div>
                            <div class="d-flex gap-2 flex-wrap">
                                ${!isOwnerView ? `
                                    ${rental.status !== 'cancelled' && rental.payment_status === 'pending' ? `
                                        <button class="btn btn-sm btn-success" 
                                                data-bs-toggle="modal" 
                                                data-bs-target="#paymentRentalModal"
                                                data-rental-id="${rental.rental_id}"
                                                data-rental-amount="${rental.total_amount || 0}">
                                            <i class="bi bi-credit-card me-1"></i>支払う
                                        </button>
                                    ` : ''}
                                    ${canCancel ? `
                                        <button class="btn btn-sm btn-outline-danger" 
                                                data-bs-toggle="modal" 
                                                data-bs-target="#cancelRentalModal"
                                                data-rental-id="${rental.rental_id}">
                                            <i class="bi bi-x-circle me-1"></i>キャンセル
                                        </button>
                                    ` : ''}
                                    ${rental.status !== 'cancelled' && rental.payment_status === 'paid' && rental.owner_id ? `
                                        <a href="${window.CONTEXT_PATH}/messages.jsp?user_id=${rental.owner_id}&product_id=${rental.product_id}&type=product" 
                                           class="btn btn-sm btn-primary">
                                            <i class="bi bi-chat-dots me-1"></i>出品者に連絡
                                        </a>
                                    ` : ''}
                                    ${(rental.status === 'confirmed' || rental.status === 'active') && rental.status !== 'cancelled' ? `
                                        <button class="btn btn-sm btn-outline-success" 
                                                data-bs-toggle="modal" 
                                                data-bs-target="#completeRentalModal"
                                                data-rental-id="${rental.rental_id}">
                                            <i class="bi bi-check2-circle me-1"></i>返却完了
                                        </button>
                                    ` : ''}
                                ` : `
                                    ${rental.status === 'pending' && rental.payment_status === 'paid' ? `
                                        <a href="${window.CONTEXT_PATH}/rental-detail.jsp?id=${rental.rental_id}" class="btn btn-sm btn-primary">
                                            <i class="bi bi-check-circle me-1"></i>レンタルを確定
                                        </a>
                                    ` : ''}
                                    ${rental.status !== 'cancelled' && rental.renter_id ? `
                                        <a href="${window.CONTEXT_PATH}/messages.jsp?user_id=${rental.renter_id}&product_id=${rental.product_id}&type=product" 
                                           class="btn btn-sm btn-outline-primary">
                                            <i class="bi bi-chat-dots me-1"></i>借り手に連絡
                                        </a>
                                    ` : ''}
                                `}
                                <button class="btn btn-sm btn-outline-primary" 
                                        onclick="viewRentalDetails(${rental.rental_id})">
                                    <i class="bi bi-eye me-1"></i>詳細を見る
                                </button>
                                <a href="${window.CONTEXT_PATH}/product-detail.jsp?id=${rental.product_id}" 
                                   class="btn btn-sm btn-outline-info">
                                    <i class="bi bi-box-arrow-up-right me-1"></i>商品を見る
                                </a>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }).join('');
}

function getStatusBadge(status) {
    const badges = {
        'pending': '<span class="rental-status-badge bg-warning text-dark"><i class="bi bi-clock me-1"></i>保留中</span>',
        'confirmed': '<span class="rental-status-badge bg-info"><i class="bi bi-check-circle me-1"></i>確定済み</span>',
        'active': '<span class="rental-status-badge bg-success"><i class="bi bi-play-circle me-1"></i>利用中</span>',
        'completed': '<span class="rental-status-badge bg-secondary"><i class="bi bi-check2-all me-1"></i>完了</span>',
        'cancelled': '<span class="rental-status-badge bg-danger"><i class="bi bi-x-circle me-1"></i>キャンセル</span>'
    };
    return badges[status] || `<span class="rental-status-badge bg-secondary">${escapeHtml(status || '不明')}</span>`;
}

function getPaymentBadge(paymentStatus) {
    const badges = {
        'pending': '<span class="rental-status-badge bg-warning text-dark"><i class="bi bi-hourglass-split me-1"></i>未払い</span>',
        'paid': '<span class="rental-status-badge bg-success"><i class="bi bi-check-circle me-1"></i>支払済み</span>',
        'refunded': '<span class="rental-status-badge bg-info"><i class="bi bi-arrow-counterclockwise me-1"></i>返金済み</span>'
    };
    return badges[paymentStatus] || `<span class="rental-status-badge bg-secondary">${escapeHtml(paymentStatus || '不明')}</span>`;
}

async function cancelRental(rentalId) {
    try {
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/RentalServlet`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                action: 'cancelRental',
                rental_id: rentalId
            })
        });
        
        if (data && data.success !== false) {
            if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
                KaruruUtils.showNotification('レンタルをキャンセルしました', 'success');
            }
            
            // Close modal
            const modal = bootstrap.Modal.getInstance(document.getElementById('cancelRentalModal'));
            if (modal) {
                modal.hide();
            }
            
            // Reload rentals
            loadRentals();
        } else {
            throw new Error(data?.error || data?.message || 'レンタルのキャンセルに失敗しました');
        }
    } catch (error) {
        console.error('Error cancelling rental:', error);
        if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
            KaruruUtils.showNotification(error.message || 'キャンセルできませんでした', 'danger');
        }
    }
}

async function payRental(rentalId) {
    try {
        const methodRadio = document.querySelector('input[name="rentalPaymentMethod"]:checked');
        const paymentMethod = methodRadio ? methodRadio.value : 'wallet';
        
        if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
            KaruruUtils.showNotification('支払いを処理しています...', 'info');
        }
        
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/RentalServlet`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: new URLSearchParams({
                action: 'payRental',
                rental_id: String(parseInt(rentalId, 10)),
                payment_method: paymentMethod
            })
        });
        
        if (data && data.success !== false) {
            if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
                KaruruUtils.showNotification(data.message || '支払いが完了しました', 'success');
            }
            
            const modal = bootstrap.Modal.getInstance(document.getElementById('paymentRentalModal'));
            if (modal) {
                modal.hide();
            }
            
            loadRentals();
        } else {
            throw new Error(data?.error || data?.message || '支払い処理に失敗しました');
        }
    } catch (error) {
        console.error('Error paying rental:', error);
        if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
            KaruruUtils.showNotification(error.message || '支払いできませんでした', 'danger');
        }
    }
}

async function completeRental(rentalId) {
    try {
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/RentalServlet`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams({
                action: 'completeRental',
                rental_id: String(parseInt(rentalId, 10))
            })
        });
        if (data && data.success !== false) {
            KaruruUtils.showNotification(data.message || '返却が完了しました', 'success');
            const modal = bootstrap.Modal.getInstance(document.getElementById('completeRentalModal'));
            if (modal) modal.hide();
            loadRentals();
        } else {
            throw new Error(data?.error || data?.message || '返却の登録に失敗しました');
        }
    } catch (error) {
        console.error('Error completing rental:', error);
        KaruruUtils.showNotification(error.message || '返却の登録に失敗しました', 'danger');
    }
}

function viewRentalDetails(rentalId) {
    window.location.href = `${window.CONTEXT_PATH}/rental-detail.jsp?id=${rentalId}`;
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

