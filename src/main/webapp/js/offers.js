// Offers management page JavaScript

let currentView = 'received'; // 'received' or 'sent'
let currentStatus = ''; // '', 'pending', 'accepted', 'rejected'

document.addEventListener('DOMContentLoaded', function() {
    // Setup view toggle
    const viewRadios = document.querySelectorAll('input[name="offerView"]');
    viewRadios.forEach(radio => {
        radio.addEventListener('change', function() {
            currentView = this.value;
            loadOffers();
        });
    });
    
    // Setup status tabs
    const statusTabs = document.querySelectorAll('[data-status]');
    statusTabs.forEach(tab => {
        tab.addEventListener('click', function() {
            currentStatus = this.getAttribute('data-status') || '';
            loadOffers();
        });
    });
    
    // Load initial offers
    loadOffers();
});

async function loadOffers() {
    const container = document.getElementById('offersContainer');
    if (!container) return;
    
    container.innerHTML = `
        <div class="text-center py-5">
            <div class="spinner-border text-primary" role="status">
                <span class="visually-hidden">読み込み中...</span>
            </div>
        </div>
    `;
    
    try {
        let data;
        if (currentView === 'received') {
            // Get offers received (for seller)
            const params = new URLSearchParams({ action: 'getOffers' });
            if (currentStatus) {
                params.append('status', currentStatus);
            }
            data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/OfferServlet?${params.toString()}`);
        } else {
            // Get offers sent (for buyer)
            const params = new URLSearchParams({ action: 'getMyOffers' });
            if (currentStatus) {
                params.append('status', currentStatus);
            }
            data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/OfferServlet?${params.toString()}`);
        }
        
        const offers = data.offers || [];
        
        if (offers.length === 0) {
            container.innerHTML = `
                <div class="text-center py-5">
                    <i class="bi bi-inbox" style="font-size: 3rem; color: #6c757d;"></i>
                    <p class="text-muted mt-3 mb-0">オファーがありません</p>
                </div>
            `;
            return;
        }
        
        container.innerHTML = offers.map(offer => renderOfferCard(offer)).join('');
        
        // Setup action buttons
        setupOfferActions();
        
    } catch (error) {
        console.error('Error loading offers:', error);
        container.innerHTML = `
            <div class="alert alert-danger">
                <i class="bi bi-exclamation-triangle me-2"></i>オファーの読み込みに失敗しました
            </div>
        `;
    }
}

function renderOfferCard(offer) {
    const isReceived = currentView === 'received';
    const otherUser = isReceived ? {
        name: offer.buyer_name || offer.buyer_username || 'ユーザー',
        username: offer.buyer_username,
        avatar: offer.buyer_avatar
    } : {
        name: offer.seller_name || offer.seller_username || 'ユーザー',
        username: offer.seller_username,
        avatar: offer.seller_avatar
    };
    
    const statusBadge = getStatusBadge(offer.status);
    const productImage = KaruruUtils.resolveProductImageUrl(offer.image_url);
    const productUrl = `${window.CONTEXT_PATH}/product-detail.jsp?id=${offer.product_id}`;
    
    return `
        <div class="card card-light mb-3 offer-card" data-offer-id="${offer.offer_id}" data-status="${offer.status}">
            <div class="card-body p-4">
                <div class="row align-items-center">
                    <div class="col-md-2 text-center mb-3 mb-md-0">
                        <a href="${productUrl}" class="text-decoration-none">
                            <img src="${productImage}" 
                                 alt="${escapeHtml(offer.product_name)}" 
                                 class="img-fluid rounded"
                                 style="max-height: 100px; object-fit: cover; width: 100%;"
                                 onerror="this.onerror=null; this.src='${KaruruUtils.resolveProductImageUrl('')}';">
                        </a>
                    </div>
                    <div class="col-md-6 offer-main-info">
                        <h5 class="mb-2">
                            <a href="${productUrl}" class="text-decoration-none">
                                ${escapeHtml(offer.product_name)}
                            </a>
                        </h5>
                        <div class="mb-2">
                            <span class="text-muted">${isReceived ? '購入者' : '販売者'}:</span>
                            <span class="ms-2">${escapeHtml(otherUser.name)}</span>
                        </div>
                        <div class="mb-2">
                            <span class="text-muted">商品価格:</span>
                            <span class="text-primary ms-2 fw-bold">${KaruruUtils.formatPrice(offer.product_price)}</span>
                        </div>
                        <div class="mb-2">
                            <span class="text-muted">オファー価格:</span>
                            <span class="text-warning ms-2 fw-bold fs-5">${KaruruUtils.formatPrice(offer.offer_price)}</span>
                        </div>
                        ${offer.message ? `
                            <div class="mt-2">
                                <small class="text-muted">メッセージ:</small>
                                <p class="text-dark mb-0 small">${escapeHtml(offer.message)}</p>
                            </div>
                        ` : ''}
                    </div>
                    <div class="col-md-2 text-center offer-meta mb-3 mb-md-0">
                        ${statusBadge}
                        <div class="mt-2">
                            <small class="text-muted">${formatDate(offer.created_at)}</small>
                        </div>
                    </div>
                    <div class="col-md-2 offer-actions">
                        ${renderOfferActions(offer, isReceived)}
                    </div>
                </div>
            </div>
        </div>
    `;
}

function renderOfferActions(offer, isReceived) {
    if (offer.status === 'pending') {
        if (isReceived) {
            return `
                <div class="d-grid gap-2">
                    <button class="btn btn-success btn-sm" onclick="acceptOffer(${offer.offer_id})">
                        <i class="bi bi-check-circle me-1"></i>承認
                    </button>
                    <button class="btn btn-danger btn-sm" onclick="rejectOffer(${offer.offer_id})">
                        <i class="bi bi-x-circle me-1"></i>拒否
                    </button>
                </div>
            `;
        } else {
            return `
                <div class="d-grid gap-2">
                    <button class="btn btn-secondary btn-sm" onclick="cancelOffer(${offer.offer_id})">
                        <i class="bi bi-x-circle me-1"></i>キャンセル
                    </button>
                </div>
            `;
        }
    }
    if (offer.status === 'accepted' && !isReceived) {
        return `
            <div class="d-grid gap-2">
                <button class="btn btn-success btn-sm" onclick="purchaseFromOffer(${offer.offer_id}, ${offer.product_id})">
                    <i class="bi bi-cart-check me-1"></i>購入する
                </button>
            </div>
        `;
    }
    return '<span class="text-muted small">処理済み</span>';
}

function getStatusBadge(status) {
    const badges = {
        'pending': '<span class="badge bg-warning text-dark"><i class="bi bi-clock me-1"></i>保留中</span>',
        'accepted': '<span class="badge bg-success"><i class="bi bi-check-circle me-1"></i>承認済み</span>',
        'rejected': '<span class="badge bg-danger"><i class="bi bi-x-circle me-1"></i>拒否済み</span>',
        'cancelled': '<span class="badge bg-secondary"><i class="bi bi-x-circle me-1"></i>キャンセル済み</span>'
    };
    return badges[status] || '<span class="badge bg-secondary">' + status + '</span>';
}

async function acceptOffer(offerId) {
    if (!confirm('このオファーを承認しますか？承認すると、他の保留中のオファーは自動的に拒否されます。')) {
        return;
    }
    
    try {
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/OfferServlet`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: new URLSearchParams({
                action: 'accept',
                offer_id: offerId.toString()
            })
        });
        
        if (data && data.success !== false) {
            loadOffers();
        } else {
            throw new Error(data?.error || data?.message || 'Failed to accept offer');
        }
    } catch (error) {
        console.error('Error accepting offer:', error);
    }
}

async function rejectOffer(offerId) {
    if (!confirm('このオファーを拒否しますか？')) {
        return;
    }
    
    try {
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/OfferServlet`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: new URLSearchParams({
                action: 'reject',
                offer_id: offerId.toString()
            })
        });
        
        if (data && data.success !== false) {
            loadOffers();
        } else {
            throw new Error(data?.error || data?.message || 'Failed to reject offer');
        }
    } catch (error) {
        console.error('Error rejecting offer:', error);
    }
}

async function purchaseFromOffer(offerId, productId) {
    try {
        KaruruUtils.showNotification && KaruruUtils.showNotification('カートに追加しています...', 'info');
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/CartServlet`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams({
                action: 'add',
                productId: productId.toString(),
                offer_id: offerId.toString(),
                quantity: '1'
            })
        });
        if (data && data.success !== false) {
            KaruruUtils.showNotification && KaruruUtils.showNotification(data.message || 'カートに追加しました', 'success');
            setTimeout(() => {
                window.location.href = `${window.CONTEXT_PATH}/checkout.jsp`;
            }, 500);
        } else {
            throw new Error(data?.error || data?.message || 'カートへの追加に失敗しました');
        }
    } catch (error) {
        console.error('Error purchasing from offer:', error);
        KaruruUtils.showNotification && KaruruUtils.showNotification(error.message || 'カートへの追加に失敗しました', 'danger');
    }
}

async function cancelOffer(offerId) {
    if (!confirm('このオファーをキャンセルしますか？')) {
        return;
    }
    
    try {
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/OfferServlet`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: new URLSearchParams({
                action: 'cancel',
                offer_id: offerId.toString()
            })
        });
        
        if (data && data.success !== false) {
            loadOffers();
        } else {
            throw new Error(data?.error || data?.message || 'Failed to cancel offer');
        }
    } catch (error) {
        console.error('Error cancelling offer:', error);
    }
}

function setupOfferActions() {
    // Actions are already set up via onclick handlers
}

function formatDate(dateString) {
    if (!dateString) return '';
    const date = new Date(dateString);
    return date.toLocaleDateString('ja-JP', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
    });
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

