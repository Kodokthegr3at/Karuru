// Product detail page JavaScript

let currentProductId = null;

document.addEventListener('DOMContentLoaded', function() {
    // Update badges on page load
    if (window.currentUserId && typeof window.updateAllBadges === 'function') {
        window.updateAllBadges();
    }
    
    // Get product ID from URL
    const urlParams = new URLSearchParams(window.location.search);
    currentProductId = urlParams.get('id');
    
    if (currentProductId) {
        loadProductDetail(currentProductId);
        loadReviews(currentProductId);
        loadRelatedProducts(currentProductId);
        checkPurchaseStatusAndSetupReviewForm(currentProductId);
    } else {
        const container = document.getElementById('productDetail');
        if (container) {
            container.innerHTML = '<div class="error-state"><p>商品IDが指定されていません</p></div>';
        }
    }
});

async function loadProductDetail(id) {
    try {
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/ProductServlet?action=getProductById&id=${id}`);
        
        // Debug: log response
        console.log('Product detail response:', data);
        
        // Extract product from response
        let product = null;
        if (data && data.product) {
            product = data.product;
        } else if (data && data.product_id) {
            // If response is the product itself
            product = data;
        } else {
            // Try extractData as fallback
            product = KaruruUtils.extractData(data, 'product') || data;
        }
        
        console.log('Extracted product:', product);
        
        if (product && product.product_id) {
            renderProductDetail(product);
        } else {
            console.error('Product not found or invalid:', product);
            throw new Error('Product not found');
        }
    } catch (error) {
        console.error('Error loading product detail:', error);
        const container = document.getElementById('productDetail');
        if (container) {
            container.innerHTML = `
                <div class="text-center py-5 text-danger">
                    <i class="bi bi-exclamation-triangle" style="font-size: 3rem;"></i>
                    <p class="mt-3 mb-0">商品情報の読み込みに失敗しました</p>
                    <small class="text-muted d-block mt-2">${error.message || ''}</small>
                </div>
            `;
        }
    }
}

function renderProductDetail(product) {
    const container = document.getElementById('productDetail');
    if (!container) return;
    
    // Get all images
    const allImages = [];
    if (product.image_url) {
        allImages.push(product.image_url);
    }
    if (product.images && Array.isArray(product.images)) {
        product.images.forEach(img => {
            if (img && !allImages.includes(img)) {
                allImages.push(img);
            }
        });
    }
    if (allImages.length === 0) {
        allImages.push('/img/default-product.png');
    }
    
    // Format rating display
    const rating = product.rating_avg || 0;
    const ratingCount = product.rating_count || 0;
    const stars = Math.round(rating);
    const ratingStars = Array.from({ length: 5 }, (_, i) => 
        i < stars ? '<i class="bi bi-star-fill text-warning"></i>' : '<i class="bi bi-star text-muted"></i>'
    ).join('');
    
    // Status badge (normalize status for comparison)
    const statusLower = String(product.status || '').toLowerCase();
    // Show action buttons when: status is 'available' OR API says is_available (real-time check)
    const isAvailable = statusLower === 'available' || product.is_available === true;
    console.log('[Product Detail] status:', product.status, 'statusLower:', statusLower, 'isAvailable:', isAvailable, 'is_available(API):', product.is_available);
    
    const statusBadge = isAvailable ? 
        '<span class="badge bg-success"><i class="bi bi-check-circle me-1"></i>販売中</span>' :
        statusLower === 'sold' ? 
        '<span class="badge bg-danger"><i class="bi bi-x-circle me-1"></i>売り切れ</span>' :
        statusLower === 'reserved' ? 
        '<span class="badge bg-warning text-dark"><i class="bi bi-clock me-1"></i>予約中</span>' :
        '<span class="badge bg-secondary">' + (product.status || '不明') + '</span>';
    
    container.innerHTML = `
        <div class="product-detail-wrapper">
            <div class="row g-4">
                <!-- Product Images - sticky on scroll -->
                <div class="col-lg-6">
                    <div class="product-image-card card card-light shadow-lg product-image-sticky">
                        <div class="card-body p-3">
                            <div class="product-image-gallery">
                                <div id="mainImageCarousel" class="carousel slide carousel-fade" data-bs-ride="carousel" data-bs-interval="false">
                                    <div class="carousel-inner rounded-3 overflow-hidden">
                                        ${allImages.map((img, index) => {
                                            const imageUrl = KaruruUtils.resolveProductImageUrl(img);
                                            return `
                                            <div class="carousel-item ${index === 0 ? 'active' : ''}">
                                                <img src="${imageUrl}" 
                                                     class="d-block w-100" 
                                                     alt="${escapeHtml(product.product_name || 'Product')}" 
                                                     style="height: 550px; object-fit: contain; background: linear-gradient(135deg, #F8F9FA 0%, #E9ECEF 100%);"
                                                     onerror="this.onerror=null; this.src='${KaruruUtils.resolveProductImageUrl('')}';">
                                            </div>
                                        `;
                                        }).join('')}
                                    </div>
                                    ${allImages.length > 1 ? `
                                        <button class="carousel-control-prev" type="button" data-bs-target="#mainImageCarousel" data-bs-slide="prev">
                                            <span class="carousel-control-prev-icon" aria-hidden="true"></span>
                                            <span class="visually-hidden">前へ</span>
                                        </button>
                                        <button class="carousel-control-next" type="button" data-bs-target="#mainImageCarousel" data-bs-slide="next">
                                            <span class="carousel-control-next-icon" aria-hidden="true"></span>
                                            <span class="visually-hidden">次へ</span>
                                        </button>
                                    ` : ''}
                                </div>
                                ${allImages.length > 1 ? `
                                    <div class="thumbnail-gallery mt-3">
                                        <div class="row g-2">
                                            ${allImages.map((img, index) => {
                                                const imageUrl = KaruruUtils.resolveProductImageUrl(img);
                                                return `
                                                    <div class="col-3">
                                                        <img src="${imageUrl}" 
                                                             class="thumbnail-img ${index === 0 ? 'active' : ''}" 
                                                             alt="Thumbnail ${index + 1}"
                                                             data-index="${index}"
                                                             onerror="this.onerror=null; this.src='${KaruruUtils.resolveProductImageUrl('')}';"
                                                             style="cursor: pointer;">
                                                    </div>
                                                `;
                                            }).join('')}
                                        </div>
                                    </div>
                                ` : ''}
                            </div>
                        </div>
                    </div>
                </div>
                
                <!-- Product Info -->
                <div class="col-lg-6">
                    <div class="product-info-card card card-light shadow-lg h-100">
                        <div class="card-body p-4">
                            <!-- Title and Status -->
                            <div class="d-flex justify-content-between align-items-start mb-3">
                                <h1 class="product-title mb-0 flex-grow-1 me-3" style="font-size: 2rem; font-weight: 700; line-height: 1.2; color: var(--text-color);">
                                    ${escapeHtml(product.product_name || '商品名なし')}
                                </h1>
                                ${statusBadge}
                            </div>
                            
                            <!-- Rating -->
                            ${ratingCount > 0 ? `
                                <div class="product-rating mb-3 d-flex align-items-center">
                                    <div class="rating-stars me-2" style="font-size: 1.1rem;">
                                        ${ratingStars}
                                    </div>
                                    <span class="text-muted me-2">${rating.toFixed(1)}</span>
                                    <span class="text-muted small">(${ratingCount}件のレビュー)</span>
                                </div>
                            ` : `
                                <div class="product-rating mb-3">
                                    <span class="text-muted small"><i class="bi bi-star me-1"></i>レビューなし</span>
                                </div>
                            `}
                            
                            <!-- Price Section -->
                            <div class="product-price-section mb-4 p-3 rounded-3" style="background: linear-gradient(135deg, rgba(30, 136, 229, 0.1) 0%, rgba(30, 136, 229, 0.05) 100%); border: 1px solid rgba(30, 136, 229, 0.2);">
                                <div class="d-flex align-items-baseline mb-2">
                                    <h2 class="text-primary fw-bold mb-0 me-2" style="font-size: 2.5rem;">
                                        ${KaruruUtils.formatPrice(product.price || 0)}
                                    </h2>
                                    ${product.original_price && product.original_price > product.price ? `
                                        <span class="text-muted text-decoration-line-through" style="font-size: 1.2rem;">
                                            ${KaruruUtils.formatPrice(product.original_price)}
                                        </span>
                                    ` : ''}
                                </div>
                                ${product.discount_percentage && product.discount_percentage > 0 ? `
                                    <span class="badge bg-danger" style="font-size: 0.9rem;">
                                        <i class="bi bi-tag me-1"></i>${product.discount_percentage}% OFF
                                    </span>
                                ` : ''}
                                ${product.is_negotiable ? `
                                    <div class="mt-2">
                                        <span class="badge bg-warning text-dark" style="font-size: 0.9rem;">
                                            <i class="bi bi-hand-thumbs-up me-1"></i>価格交渉可能
                                        </span>
                                    </div>
                                ` : ''}
                                ${(product.rental_price_daily || product.rental_price_weekly || product.rental_price_monthly) ? `
                                    <div class="mt-2">
                                        <p class="text-muted mb-1 small">
                                            <i class="bi bi-calendar3 me-1"></i>レンタル価格:
                                        </p>
                                        <div class="d-flex flex-wrap gap-2">
                                            ${product.rental_price_daily ? `
                                                <span class="badge bg-info">
                                                    ${KaruruUtils.formatPrice(product.rental_price_daily)}/日
                                                </span>
                                            ` : ''}
                                            ${product.rental_price_weekly ? `
                                                <span class="badge bg-success">
                                                    ${KaruruUtils.formatPrice(product.rental_price_weekly)}/週
                                                </span>
                                            ` : ''}
                                            ${product.rental_price_monthly ? `
                                                <span class="badge bg-warning text-dark">
                                                    ${KaruruUtils.formatPrice(product.rental_price_monthly)}/月
                                                </span>
                                            ` : ''}
                                        </div>
                                    </div>
                                ` : ''}
                            </div>
                            
                            <!-- Product Meta -->
                            <div class="product-meta mb-4">
                                <div class="row g-3">
                                    ${product.seller_name && product.seller_id ? `
                                        <div class="col-12">
                                            <a href="${window.CONTEXT_PATH}/seller.jsp?seller_id=${product.seller_id}" class="text-decoration-none" style="color: inherit;">
                                                <div class="meta-item d-flex align-items-center p-3 rounded-3" style="background: var(--bg-secondary); border: 1px solid var(--border-color); cursor: pointer; transition: all 0.3s ease;">
                                                    <div class="meta-icon me-3" style="width: 40px; height: 40px; background: var(--accent-light); border-radius: 10px; display: flex; align-items: center; justify-content: center;">
                                                        <i class="bi bi-person-fill text-primary" style="font-size: 1.2rem;"></i>
                                                    </div>
                                                    <div class="flex-grow-1">
                                                        <div class="text-muted small mb-1">出品者</div>
                                                        <div class="fw-semibold d-flex align-items-center" style="color: var(--text-color);">
                                                            ${escapeHtml(product.seller_name)}
                                                            <i class="bi bi-arrow-right ms-2 text-primary" style="font-size: 0.9rem; opacity: 0.7;"></i>
                                                        </div>
                                                    </div>
                                                </div>
                                            </a>
                                        </div>
                                    ` : ''}
                                    ${product.condition ? `
                                        <div class="col-6">
                                            <div class="meta-item p-3 rounded-3" style="background: var(--bg-secondary); border: 1px solid var(--border-color);">
                                                <div class="text-muted small mb-1">
                                                    <i class="bi bi-check-circle me-1"></i>状態
                                                </div>
                                                <div class="fw-semibold" style="color: var(--text-color);">${getConditionText(product.condition)}</div>
                                            </div>
                                        </div>
                                    ` : ''}
                                    ${product.stock_quantity !== undefined ? `
                                        <div class="col-6">
                                            <div class="meta-item p-3 rounded-3" style="background: var(--bg-secondary); border: 1px solid var(--border-color);">
                                                <div class="text-muted small mb-1">
                                                    <i class="bi bi-box-seam me-1"></i>在庫
                                                </div>
                                                <div class="fw-semibold" style="color: var(--text-color);">
                                                    ${product.stock_quantity}個
                                                    ${product.stock_quantity > 0 ? '<span class="badge bg-success ms-2">在庫あり</span>' : '<span class="badge bg-danger ms-2">在庫なし</span>'}
                                                </div>
                                            </div>
                                        </div>
                                    ` : ''}
                                    ${product.weight && product.weight > 0 ? `
                                        <div class="col-6">
                                            <div class="meta-item p-3 rounded-3" style="background: var(--bg-secondary); border: 1px solid var(--border-color);">
                                                <div class="text-muted small mb-1">
                                                    <i class="bi bi-rulers me-1"></i>重量
                                                </div>
                                                <div class="fw-semibold" style="color: var(--text-color);">${formatWeight(product.weight)}</div>
                                            </div>
                                        </div>
                                    ` : ''}
                                </div>
                            </div>
                            
                            <!-- Product Stats -->
                            <div class="product-stats mb-4 d-flex gap-3 flex-wrap">
                                ${product.views_count > 0 ? `
                                    <div class="stat-badge">
                                        <i class="bi bi-eye text-muted me-1"></i>
                                        <span class="text-muted small">${product.views_count}回閲覧</span>
                                    </div>
                                ` : ''}
                                ${product.likes_count > 0 ? `
                                    <div class="stat-badge">
                                        <i class="bi bi-heart text-danger me-1"></i>
                                        <span class="text-muted small">${product.likes_count}いいね</span>
                                    </div>
                                ` : ''}
                                ${product.sold_count > 0 ? `
                                    <div class="stat-badge">
                                        <i class="bi bi-cart-check text-success me-1"></i>
                                        <span class="text-muted small">${product.sold_count}個販売</span>
                                    </div>
                                ` : ''}
                            </div>
                            
                            <!-- Actions -->
                            <div class="product-actions">
                                <div class="d-grid gap-2">
                                    ${isAvailable ? `
                                        ${product.is_rental ? `
                                            <div class="d-grid gap-2" style="grid-template-columns: 1fr 1fr;">
                                                <button class="btn btn-success btn-lg py-3 fw-semibold" onclick="buyNow(${product.product_id})" style="font-size: 1.1rem; border-radius: 12px;">
                                                    <i class="bi bi-cart-check me-2"></i>今すぐ購入
                                                </button>
                                                <button class="btn btn-warning btn-lg py-3 fw-semibold" onclick="rentNow(${product.product_id})" style="font-size: 1.1rem; border-radius: 12px;">
                                                    <i class="bi bi-calendar-check me-2"></i>レンタル
                                                </button>
                                            </div>
                                        ` : `
                                            <button class="btn btn-success btn-lg py-3 fw-semibold" onclick="buyNow(${product.product_id})" style="font-size: 1.1rem; border-radius: 12px;">
                                                <i class="bi bi-cart-check me-2"></i>今すぐ購入
                                            </button>
                                        `}
                                    <button class="btn btn-primary btn-lg py-3 fw-semibold" onclick="addToCart(${product.product_id})" style="font-size: 1.1rem; border-radius: 12px;">
                                        <i class="bi bi-cart-plus me-2"></i>カートに追加
                                    </button>
                                    <button class="btn btn-warning btn-lg py-3 fw-semibold" onclick="openOfferModal(${product.product_id}, ${product.price})" style="font-size: 1.1rem; border-radius: 12px;">
                                        <i class="bi bi-hand-thumbs-up me-2"></i>値段交渉
                                    </button>
                                        <button class="btn btn-info btn-lg py-3 fw-semibold" onclick="askSeller(${product.product_id}, ${product.user_id || product.seller_id})" style="font-size: 1.1rem; border-radius: 12px;">
                                            <i class="bi bi-chat-dots me-2"></i>販売者に質問
                                        </button>
                                    ` : `
                                        <button class="btn btn-secondary btn-lg py-3 fw-semibold" disabled style="font-size: 1.1rem; border-radius: 12px;">
                                            <i class="bi bi-x-circle me-2"></i>${statusLower === 'sold' ? '売却済み' : statusLower === 'rented' ? 'レンタル中' : '利用不可'}
                                        </button>
                                        <button class="btn btn-info btn-lg py-3 fw-semibold" onclick="askSeller(${product.product_id}, ${product.user_id || product.seller_id})" style="font-size: 1.1rem; border-radius: 12px;">
                                            <i class="bi bi-chat-dots me-2"></i>販売者に質問
                                        </button>
                                    `}
                                    <div class="d-grid gap-2" style="grid-template-columns: 1fr 1fr;">
                                        <button class="btn btn-outline-primary py-2" onclick="toggleFavorite(${product.product_id})" id="favoriteBtn" style="border-radius: 12px;">
                                            <i class="bi bi-heart me-2"></i>お気に入り
                                        </button>
                                        <button class="btn btn-outline-secondary py-2" onclick="shareProduct(${product.product_id})" style="border-radius: 12px;">
                                            <i class="bi bi-share me-2"></i>シェア
                                        </button>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            
            <!-- Description Section -->
            ${product.description ? `
                <div class="row mt-4">
                    <div class="col-12">
                        <div class="card card-light shadow-lg">
                            <div class="card-body p-4">
                                <h5 class="mb-3 product-description-title d-flex align-items-center" style="color: var(--text-color);">
                                    <i class="bi bi-info-circle me-2 text-primary" style="font-size: 1.1rem;"></i>商品説明
                                </h5>
                                <div class="product-description-text">
                                    ${formatProductDescription(product.description)}
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            ` : ''}
            
            <!-- Mobile sticky bottom actions (includes 値段交渉) -->
            ${isAvailable ? `
            <div class="product-detail-mobile-actions d-lg-none fixed-bottom bg-white border-top shadow-lg py-2 px-3" style="z-index: 1035; padding-bottom: calc(0.5rem + env(safe-area-inset-bottom, 0)) !important; bottom: calc(52px + env(safe-area-inset-bottom, 0));">
                <div class="d-flex gap-2 flex-wrap justify-content-center">
                    ${product.is_rental ? `
                        <button class="btn btn-success btn-sm flex-grow-1" onclick="buyNow(${product.product_id})" style="min-width: 80px;">
                            <i class="bi bi-cart-check me-1"></i>購入
                        </button>
                        <button class="btn btn-warning btn-sm flex-grow-1" onclick="rentNow(${product.product_id})" style="min-width: 80px;">
                            <i class="bi bi-calendar-check me-1"></i>レンタル
                        </button>
                    ` : `
                        <button class="btn btn-success btn-sm flex-grow-1" onclick="buyNow(${product.product_id})" style="min-width: 80px;">
                            <i class="bi bi-cart-check me-1"></i>今すぐ購入
                        </button>
                    `}
                    <button class="btn btn-primary btn-sm flex-grow-1" onclick="addToCart(${product.product_id})" style="min-width: 80px;">
                        <i class="bi bi-cart-plus me-1"></i>カート
                    </button>
                    <button class="btn btn-warning btn-sm flex-grow-1" onclick="openOfferModal(${product.product_id}, ${product.price})" style="min-width: 80px;">
                        <i class="bi bi-hand-thumbs-up me-1"></i>価格交渉
                    </button>
                    <button class="btn btn-info btn-sm flex-grow-1" onclick="askSeller(${product.product_id}, ${product.user_id || product.seller_id})" style="min-width: 80px;">
                        <i class="bi bi-chat-dots me-1"></i>質問
                    </button>
                </div>
            </div>
            ` : ''}
        </div>
    `;
    
    // Setup thumbnail gallery
    if (allImages.length > 1) {
        setupProductDetailCarousel(allImages);
    }
    
    // Check favorite status
    checkFavoriteStatus(product.product_id);
}

function setupProductDetailCarousel(images) {
    const carousel = document.getElementById('mainImageCarousel');
    if (!carousel) {
        console.warn('Carousel not found for setup');
        return;
    }
    
    try {
        // Initialize Bootstrap carousel if not already initialized
        let carouselInstance = bootstrap.Carousel.getInstance(carousel);
        if (!carouselInstance) {
            carouselInstance = new bootstrap.Carousel(carousel, {
                interval: false,
                wrap: true,
                ride: false
            });
        }
        
        // Add event listener for carousel slide events to update active thumbnail
        carousel.addEventListener('slid.bs.carousel', function(event) {
            const activeIndex = event.to;
            document.querySelectorAll('.thumbnail-img').forEach((thumb) => {
                const thumbIndex = parseInt(thumb.getAttribute('data-index'));
                if (!isNaN(thumbIndex) && thumbIndex === activeIndex) {
                    thumb.classList.add('active');
                } else {
                    thumb.classList.remove('active');
                }
            });
        });
        
        // Add click handlers to thumbnails using data-index attribute
        const thumbnailImgs = document.querySelectorAll('.thumbnail-img');
        thumbnailImgs.forEach((thumb) => {
            // Remove any existing click listeners
            const newThumb = thumb.cloneNode(true);
            thumb.parentNode.replaceChild(newThumb, thumb);
            
            // Get index from data-index attribute
            const index = parseInt(newThumb.getAttribute('data-index'));
            if (!isNaN(index)) {
                newThumb.addEventListener('click', function(e) {
                    e.preventDefault();
                    e.stopPropagation();
                    window.changeMainImage(index);
                });
            }
        });
    } catch (error) {
        console.error('Error setting up product detail carousel:', error);
    }
}

// Make changeMainImage available globally for onclick handlers
window.changeMainImage = function(index) {
    const carousel = document.getElementById('mainImageCarousel');
    if (!carousel) {
        console.warn('Carousel not found');
        return;
    }
    
    try {
        // Get or create Bootstrap carousel instance
        let carouselInstance = bootstrap.Carousel.getInstance(carousel);
        if (!carouselInstance) {
            carouselInstance = new bootstrap.Carousel(carousel, {
                interval: false,
                wrap: true,
                ride: false
            });
        }
        
        // Navigate to the specified slide
        carouselInstance.to(index);
        
        // Update active thumbnail
        document.querySelectorAll('.thumbnail-img').forEach((thumb, idx) => {
            if (idx === index) {
                thumb.classList.add('active');
            } else {
                thumb.classList.remove('active');
            }
        });
    } catch (error) {
        console.error('Error changing main image:', error);
    }
};

async function loadReviews(productId) {
    try {
        // Use ProductDetailsServlet for reviews
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/ProductDetailsServlet?action=getReviews&productId=${productId}`);
        
        const reviews = KaruruUtils.extractData(data, 'reviews') || data.reviews || [];
        
        const container = document.getElementById('reviewsContainer');
        if (!container) return;
        
        if (reviews && reviews.length > 0) {
            container.innerHTML = `
                <div class="mb-4">
                    <h5 class="mb-3" style="color: var(--text-color);">
                        <i class="bi bi-star-fill text-warning me-2"></i>レビュー (${reviews.length})
                    </h5>
                    <div class="reviews-list">
                        ${reviews.map(review => {
                            const rating = parseInt(review.rating || 0);
                            const reviewText = review.review_text || review.comment || '';
                            const userName = review.username || review.user_name || review.full_name || 'ユーザー';
                            const createdAt = review.created_at || review.createdAt || '';
                            const isVerified = review.is_verified_purchase || false;
                            
                            return `
                                <div class="card card-light mb-3">
                                    <div class="card-body">
                                        <div class="d-flex justify-content-between align-items-start mb-2">
                                            <div class="flex-grow-1">
                                                <div class="d-flex align-items-center mb-2">
                                                    <h6 class="mb-0 me-2" style="color: var(--text-color);">${escapeHtml(userName)}</h6>
                                                    ${isVerified ? `
                                                        <span class="badge bg-success me-2">
                                                            <i class="bi bi-check-circle me-1"></i>購入済み
                                                        </span>
                                                    ` : ''}
                                                </div>
                                                <div class="rating-stars mb-2">
                                                    ${Array.from({ length: 5 }, (_, i) => `
                                                        <i class="bi ${i < rating ? 'bi-star-fill text-warning' : 'bi-star text-muted'}" style="font-size: 1.1rem;"></i>
                                                    `).join('')}
                                                    <span class="ms-2 text-muted">${rating}/5</span>
                                                </div>
                                            </div>
                                            <small class="text-muted">${KaruruUtils.formatDate(createdAt)}</small>
                                        </div>
                                        ${reviewText ? `
                                            <p class="mb-0" style="white-space: pre-wrap; color: var(--text-color);">${escapeHtml(reviewText)}</p>
                                        ` : ''}
                                        ${review.helpful_count > 0 ? `
                                            <div class="mt-2">
                                                <small class="text-muted">
                                                    <i class="bi bi-hand-thumbs-up me-1"></i>${review.helpful_count}人が役に立った
                                                </small>
                                            </div>
                                        ` : ''}
                                    </div>
                                </div>
                            `;
                        }).join('')}
                    </div>
                </div>
            `;
        } else {
            container.innerHTML = `
                <div class="text-center py-4">
                    <i class="bi bi-star text-muted" style="font-size: 3rem;"></i>
                    <p class="text-muted mt-3 mb-0">レビューがありません</p>
                </div>
            `;
        }
    } catch (error) {
        console.error('Error loading reviews:', error);
        const container = document.getElementById('reviewsContainer');
        if (container) {
            container.innerHTML = `
                <div class="alert alert-warning">
                    <i class="bi bi-exclamation-triangle me-2"></i>レビューの読み込みに失敗しました
                </div>
            `;
        }
    }
}

async function loadRelatedProducts(productId) {
    try {
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/ProductServlet?action=getRelated&product_id=${productId}&limit=4`);
        
        // Extract products from response
        let products = [];
        if (data && data.products) {
            products = data.products;
        } else if (Array.isArray(data)) {
            products = data;
        } else {
            products = KaruruUtils.extractData(data, 'products') || [];
        }
        
        console.log('Related products:', products);
        
        const container = document.getElementById('relatedProducts');
        if (!container) return;

        // product-detail.jsp already provides the section title and `row` container.
        // Here we only render the grid items.
        if (products && products.length > 0) {
            container.innerHTML = products.map(product => {
                const images = product.images || (product.image_url ? [product.image_url] : []);
                const firstImage = images.length > 0 ? images[0] : '';
                const fullImageUrl = KaruruUtils.resolveProductImageUrl(firstImage);

                return `
                    <div class="col-6 col-sm-6 col-md-3 mb-4">
                        <a href="${window.CONTEXT_PATH}/product-detail.jsp?id=${product.product_id}" 
                           class="product-card-link text-decoration-none">
                            <div class="card product-card-modern h-100">
                                <div class="product-image-wrapper">
                                    <div class="product-image-carousel">
                                        ${images.length > 0 ? images.map((img, idx) => {
                                            const imgUrl = KaruruUtils.resolveProductImageUrl(img);
                                            return `
                                                <img src="${imgUrl}" 
                                                     class="product-image ${idx === 0 ? 'active' : ''}" 
                                                     alt="${escapeHtml(product.product_name || 'Product')}"
                                                     loading="lazy"
                                                     onerror="this.onerror=null; this.src='${KaruruUtils.resolveProductImageUrl('')}';">
                                            `;
                                        }).join('') : `
                                            <img src="${fullImageUrl}" 
                                                 class="product-image active" 
                                                 alt="${escapeHtml(product.product_name || 'Product')}"
                                                 loading="lazy"
                                                 onerror="this.onerror=null; this.src='${KaruruUtils.resolveProductImageUrl('')}';">
                                        `}
                                    </div>
                                </div>
                                <div class="card-body">
                                    <h6 class="card-title mb-2" style="min-height: 2.85em; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;">
                                        ${escapeHtml(product.product_name || '商品名なし')}
                                    </h6>
                                    <div class="d-flex justify-content-between align-items-center">
                                        <span class="text-primary fw-bold fs-5">${KaruruUtils.formatPrice(product.price || 0)}</span>
                                        ${product.rating_avg > 0 ? `
                                            <small class="text-muted">
                                                <i class="bi bi-star-fill text-warning"></i> ${parseFloat(product.rating_avg).toFixed(1)}
                                            </small>
                                        ` : ''}
                                    </div>
                                </div>
                            </div>
                        </a>
                    </div>
                `;
            }).join('');
        } else {
            container.innerHTML = `
                <div class="col-12">
                    <p class="text-muted mb-0">関連商品がありません</p>
                </div>
            `;
        }
        
        // Setup image carousels for related products
        setTimeout(() => {
            container.querySelectorAll('.product-image-carousel').forEach(carousel => {
                setupProductImageCarousel(carousel);
            });
        }, 100);
        
    } catch (error) {
        console.error('Error loading related products:', error);
        const container = document.getElementById('relatedProducts');
        if (container) {
            container.innerHTML = `
                <h5 class="mb-4" style="color: var(--text-color);">関連商品</h5>
                <div class="alert alert-warning">
                    <i class="bi bi-exclamation-triangle me-2"></i>関連商品の読み込みに失敗しました
                </div>
            `;
        }
    }
}

// Reuse the setupProductImageCarousel function from home.js
function setupProductImageCarousel(carousel) {
    const images = carousel.querySelectorAll('.product-image');
    const cardLink = carousel.closest('.product-card-link');
    const imageWrapper = carousel.closest('.product-image-wrapper');
    const dots = imageWrapper ? imageWrapper.querySelector('.product-image-indicators') : null;
    const dotsArray = dots ? dots.querySelectorAll('.image-dot') : [];
    
    if (images.length <= 1) return;
    
    let currentIndex = 0;
    let hoverInterval = null;
    
    const showImage = (index) => {
        images.forEach((img, idx) => {
            if (idx === index) {
                img.classList.add('active');
                img.style.zIndex = '2';
            } else {
                img.classList.remove('active');
                img.style.zIndex = '1';
            }
        });
        
        dotsArray.forEach((dot, idx) => {
            dot.classList.toggle('active', idx === index);
        });
    };
    
    const nextImage = () => {
        currentIndex = (currentIndex + 1) % images.length;
        showImage(currentIndex);
    };
    
    // Auto-rotate on hover
    const hoverTarget = cardLink || imageWrapper || carousel.parentElement;
    
    if (hoverTarget) {
        hoverTarget.addEventListener('mouseenter', () => {
            if (hoverInterval) {
                clearInterval(hoverInterval);
            }
            nextImage();
            hoverInterval = setInterval(nextImage, 1500);
        });
        
        hoverTarget.addEventListener('mouseleave', () => {
            if (hoverInterval) {
                clearInterval(hoverInterval);
                hoverInterval = null;
            }
            currentIndex = 0;
            showImage(0);
        });
    }
    
    // Dot navigation
    dotsArray.forEach((dot, index) => {
        dot.addEventListener('click', (e) => {
            e.preventDefault();
            e.stopPropagation();
            currentIndex = index;
            showImage(currentIndex);
            if (hoverInterval) {
                clearInterval(hoverInterval);
                hoverInterval = setInterval(nextImage, 1500);
            }
        });
    });
    
    showImage(0);
}

async function addToCart(productId) {
    try {
        // Check if user is logged in
        const response = await fetch(`${window.CONTEXT_PATH}/CartServlet?action=getCartCount`);
        const countData = await response.json();
        
        if (!response.ok) {
            return;
        }
        
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/CartServlet`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: new URLSearchParams({
                action: 'add',
                productId: productId.toString(),
                quantity: '1'
            })
        });
        
        if (data && data.success !== false) {
            // Update cart count in header
            if (typeof window.updateCartCount === 'function') {
                await window.updateCartCount();
            }
        } else {
            throw new Error(data?.error || data?.message || 'Failed to add to cart');
        }
    } catch (error) {
        console.error('Error adding to cart:', error);
    }
}

// ==================== BUY NOW FUNCTION ====================
async function buyNow(productId) {
    // Find the button that was clicked by searching for onclick attribute
    const buyNowBtn = document.querySelector(`button[onclick*="buyNow(${productId})"]`);
    const originalHtml = buyNowBtn ? buyNowBtn.innerHTML : '';
    
    try {
        // Show loading state
        if (buyNowBtn) {
            buyNowBtn.disabled = true;
            buyNowBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>処理中...';
        }
        
        if (!window.currentUserId) {
            window.location.href = `${window.CONTEXT_PATH}/login.jsp?redirect=${encodeURIComponent(window.location.pathname + window.location.search)}`;
            return;
        }
        
        // Check if user is logged in by checking cart count
        const response = await fetch(`${window.CONTEXT_PATH}/CartServlet?action=getCartCount`);
        if (!response.ok) {
            if (buyNowBtn) {
                buyNowBtn.disabled = false;
                buyNowBtn.innerHTML = originalHtml;
            }
            window.location.href = `${window.CONTEXT_PATH}/login.jsp?redirect=${encodeURIComponent(window.location.pathname + window.location.search)}`;
            return;
        }
        
        // Add product to cart first
        try {
            const addToCartData = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/CartServlet`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded'
                },
                body: new URLSearchParams({
                    action: 'add',
                    productId: productId.toString(),
                    quantity: '1'
                })
            });
            
            if (addToCartData && addToCartData.success !== false) {
                // Successfully added to cart, now redirect to checkout
                // Small delay before redirect
                setTimeout(() => {
                    window.location.href = `${window.CONTEXT_PATH}/checkout.jsp`;
                }, 500);
            } else {
                throw new Error(addToCartData?.error || addToCartData?.message || 'カートへの追加に失敗しました');
            }
        } catch (cartError) {
            console.error('Error adding to cart:', cartError);
            
            // Reset button state
            if (buyNowBtn) {
                buyNowBtn.disabled = false;
                buyNowBtn.innerHTML = originalHtml;
            }
            
            // Check if product is already in cart
            const errorMsg = cartError.message || '';
            if (errorMsg.includes('既に') || errorMsg.includes('already') || errorMsg.includes('すでに')) {
                // Product already in cart, just redirect to checkout
                if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
                    KaruruUtils.showNotification('商品は既にカートにあります。チェックアウトページに移動します...', 'info');
                }
                setTimeout(() => {
                    window.location.href = `${window.CONTEXT_PATH}/checkout.jsp`;
                }, 500);
            } else {
                // Show error and don't redirect
                if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
                }
            }
        }
    } catch (error) {
        console.error('Error in buyNow:', error);
        
        // Reset button state
        if (buyNowBtn) {
            buyNowBtn.disabled = false;
            buyNowBtn.innerHTML = originalHtml;
        }
        
    }
}

// ==================== RENT NOW FUNCTION ====================
async function rentNow(productId) {
    try {
        if (!window.currentUserId) {
            window.location.href = `${window.CONTEXT_PATH}/login.jsp?redirect=${encodeURIComponent(window.location.pathname + window.location.search)}`;
            return;
        }
        
        // Redirect to rental page with product ID
        window.location.href = `${window.CONTEXT_PATH}/rental.jsp?product_id=${productId}`;
    } catch (error) {
        console.error('Error in rentNow:', error);
    }
}

// Note: Rental functionality moved to rental.jsp

// ==================== ASK SELLER FUNCTION ====================
async function askSeller(productId, sellerId) {
    try {
        console.log('askSeller called with:', { productId, sellerId });
        
        if (!window.currentUserId) {
            window.location.href = `${window.CONTEXT_PATH}/login.jsp?redirect=${encodeURIComponent(window.location.pathname + window.location.search)}`;
            return;
        }
        
        if (!sellerId || sellerId === 0) {
            if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
                KaruruUtils.showNotification('出品者情報が見つかりません', 'warning');
            }
            return;
        }
        
        // Don't allow messaging yourself
        if (parseInt(sellerId) === parseInt(window.currentUserId)) {
            if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
                KaruruUtils.showNotification('自分の商品にはメッセージできません', 'info');
            }
            return;
        }
        
        // Check if conversation already exists (optional - can skip if slow)
        let shouldCreateNew = true;
        try {
            const checkResponse = await fetch(`${window.CONTEXT_PATH}/MessagesServlet?action=getConversations`, {
                credentials: 'same-origin'
            });
            
            if (checkResponse.ok) {
                const data = await checkResponse.json();
                const conversations = Array.isArray(data) ? data : (data.conversations || data.data || []);
                
                const existingConv = conversations.find(c => {
                    const otherId = c.other_user_id || c.user_id;
                    return parseInt(otherId) === parseInt(sellerId) || otherId === sellerId;
                });
            
                if (existingConv) {
                    // Go to existing conversation with product context
                    shouldCreateNew = false;
                    const redirectUrl = `${window.CONTEXT_PATH}/messages.jsp?user_id=${sellerId}${productId ? '&product_id=' + productId + '&type=product' : '&type=product'}`;
                    window.location.href = redirectUrl;
                    return;
                }
            }
        } catch (e) {
            console.warn('Could not check existing conversations:', e);
            // Continue anyway - will create new conversation
        }
        
        // Redirect to messages page with seller and product (type=product for product inquiry)
        if (shouldCreateNew) {
            const redirectUrl = `${window.CONTEXT_PATH}/messages.jsp?user_id=${sellerId}${productId ? '&product_id=' + productId + '&type=product' : '&type=product'}`;
            window.location.href = redirectUrl;
        }
    } catch (error) {
        console.error('Error in askSeller:', error);
        if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
            KaruruUtils.showNotification(error.message || 'メッセージページに移動できませんでした', 'danger');
        }
    }
}

async function toggleFavorite(productId) {
    try {
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/FavoriteServlet`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: new URLSearchParams({
                action: 'toggle',
                productId: productId.toString()
            })
        });
        
        if (data && data.success !== false) {
            const btn = document.getElementById('favoriteBtn');
            if (btn) {
                // Handle different response formats
                const isFavorite = data.isFavorite === true || data.is_favorite === true || 
                                 data.isFavorite === 'true' || data.is_favorite === 'true' ||
                                 data.isFavorite === 1 || data.is_favorite === 1;
                
                if (isFavorite) {
                    btn.classList.add('active');
                    btn.classList.add('btn-danger');
                    btn.classList.remove('btn-outline-primary');
                    btn.innerHTML = '<i class="bi bi-heart-fill me-2"></i>お気に入り解除';
                } else {
                    btn.classList.remove('active');
                    btn.classList.remove('btn-danger');
                    btn.classList.add('btn-outline-primary');
                    btn.innerHTML = '<i class="bi bi-heart me-2"></i>お気に入り';
                }
            }
            
            // Update favorite icon in header
            if (typeof window !== 'undefined' && window.updateFavoriteIcon) {
                window.updateFavoriteIcon();
            }
            
            // Notification removed as requested
        } else {
            throw new Error(data?.error || data?.message || 'Failed to toggle favorite');
        }
    } catch (error) {
        console.error('Error toggling favorite:', error);
    }
}

async function checkFavoriteStatus(productId) {
    try {
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/FavoriteServlet?action=check&product_id=${productId}`);
        const btn = document.getElementById('favoriteBtn');
        if (!btn) return;
        
        // Handle different response formats
        const isFavorite = data?.isFavorite === true || data?.is_favorite === true || data?.isFavorite === 'true' || data?.is_favorite === 'true';
        
        if (isFavorite) {
            btn.classList.add('active');
            btn.classList.add('btn-danger');
            btn.classList.remove('btn-outline-primary');
            btn.innerHTML = '<i class="bi bi-heart-fill me-2"></i>お気に入り解除';
        } else {
            btn.classList.remove('active');
            btn.classList.remove('btn-danger');
            btn.classList.add('btn-outline-primary');
            btn.innerHTML = '<i class="bi bi-heart me-2"></i>お気に入り';
        }
    } catch (error) {
        console.error('Error checking favorite status:', error);
        // Silently fail - favorite status check is not critical
        // But ensure button is in default state
        const btn = document.getElementById('favoriteBtn');
        if (btn) {
            btn.classList.remove('active');
            btn.classList.remove('btn-danger');
            btn.classList.add('btn-outline-primary');
            btn.innerHTML = '<i class="bi bi-heart me-2"></i>お気に入り';
        }
    }
}

function shareProduct(productId) {
    const url = window.location.href;
    if (navigator.share) {
        navigator.share({
            title: document.querySelector('.product-title')?.textContent || '商品',
            text: 'この商品をチェックしてください',
            url: url
        }).catch(err => {
            console.log('Error sharing:', err);
            copyToClipboard(url);
        });
    } else {
        copyToClipboard(url);
    }
}

function copyToClipboard(text) {
    if (navigator.clipboard) {
        navigator.clipboard.writeText(text).then(() => {
        }).catch(err => {
            console.error('Failed to copy:', err);
            fallbackCopyToClipboard(text);
        });
    } else {
        fallbackCopyToClipboard(text);
    }
}

function fallbackCopyToClipboard(text) {
    const textArea = document.createElement('textarea');
    textArea.value = text;
    textArea.style.position = 'fixed';
    textArea.style.left = '-999999px';
    document.body.appendChild(textArea);
    textArea.focus();
    textArea.select();
    try {
        document.execCommand('copy');
    } catch (err) {
        console.error('Fallback copy failed:', err);
    }
    document.body.removeChild(textArea);
}

function getConditionText(condition) {
    const conditions = {
        'new': '新品',
        'like_new': '新品同様',
        'good': '良好',
        'fair': '可'
    };
    return conditions[condition] || condition;
}

// Check if user has purchased the product and setup review form accordingly
async function checkPurchaseStatusAndSetupReviewForm(productId) {
    // Only check if user is logged in
    if (!window.currentUserId) {
        // Hide review form if not logged in (handled by JSP)
        return;
    }
    
    try {
        // Check if user has purchased this product
        const purchaseCheck = await KaruruUtils.apiFetch(
            `${window.CONTEXT_PATH}/ReviewServlet?action=checkPurchaseStatus&product_id=${productId}`
        );
        
        const hasPurchased = purchaseCheck.has_purchased === true;
        const reviewFormContainer = document.getElementById('reviewFormContainer');
        const reviewForm = document.getElementById('reviewForm');
        
        if (reviewFormContainer) {
            if (hasPurchased) {
                // Show review form if user has purchased
                reviewFormContainer.style.display = 'block';
                setupReviewForm(productId);
            } else {
                // Hide review form and show message if user hasn't purchased
                reviewFormContainer.innerHTML = `
                    <div class="alert alert-warning">
                        <i class="bi bi-info-circle me-2"></i>
                        この商品を購入したユーザーのみレビューを投稿できます。
                    </div>
                `;
            }
        }
    } catch (error) {
        console.error('Error checking purchase status:', error);
        // On error, still try to setup review form (will be validated on server side)
        setupReviewForm(productId);
    }
}

function setupReviewForm(productId) {
    const reviewForm = document.getElementById('reviewForm');
    if (!reviewForm) return;
    
    // Setup star rating
    setupStarRating();
    
    // Handle form submission
    reviewForm.addEventListener('submit', async function(e) {
        e.preventDefault();
        
        const rating = document.getElementById('reviewRating')?.value;
        const reviewText = document.getElementById('reviewText')?.value;
        
        if (!rating || rating === '') {
            return;
        }
        
        if (!reviewText || reviewText.trim() === '') {
            return;
        }
        
        try {
            const submitBtn = reviewForm.querySelector('button[type="submit"]');
            if (submitBtn) {
                submitBtn.disabled = true;
                submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>投稿中...';
            }
            
            const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/ReviewServlet`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded'
                },
                body: new URLSearchParams({
                    action: 'submitReview',
                    product_id: productId,
                    rating: rating,
                    review_text: reviewText
                })
            });
            
            if (data.success !== false) {
                
                // Reset form
                reviewForm.reset();
                resetStarRating();
                
                // Reload reviews after a short delay
                setTimeout(() => {
                    loadReviews(productId);
                }, 1000);
            } else {
                throw new Error(data.error || data.message || 'Failed to submit review');
            }
        } catch (error) {
            console.error('Error submitting review:', error);
        } finally {
            const submitBtn = reviewForm.querySelector('button[type="submit"]');
            if (submitBtn) {
                submitBtn.disabled = false;
                submitBtn.innerHTML = '<i class="bi bi-send"></i> レビューを投稿';
            }
        }
    });
}

function setupStarRating() {
    const ratingSelect = document.getElementById('reviewRating');
    if (!ratingSelect) return;
    
    // Create star rating display
    const starContainer = document.createElement('div');
    starContainer.className = 'star-rating-display mb-2';
    starContainer.innerHTML = `
        <div class="d-flex align-items-center">
            <div class="star-rating-input">
                ${Array.from({ length: 5 }, (_, i) => `
                    <i class="bi bi-star star-icon" data-rating="${i + 1}" style="font-size: 1.5rem; cursor: pointer; color: #6c757d; transition: color 0.2s;"></i>
                `).join('')}
            </div>
            <span class="ms-2 text-muted" id="ratingText">評価を選択</span>
        </div>
    `;
    
    ratingSelect.parentElement.insertBefore(starContainer, ratingSelect);
    ratingSelect.style.display = 'none';
    
    const stars = starContainer.querySelectorAll('.star-icon');
    const ratingText = document.getElementById('ratingText');
    
    stars.forEach((star, index) => {
        star.addEventListener('mouseenter', function() {
            highlightStars(stars, index + 1);
            updateRatingText(ratingText, index + 1);
        });
        
        star.addEventListener('click', function() {
            const rating = index + 1;
            ratingSelect.value = rating;
            highlightStars(stars, rating);
            updateRatingText(ratingText, rating);
        });
    });
    
    starContainer.addEventListener('mouseleave', function() {
        const currentRating = parseInt(ratingSelect.value) || 0;
        highlightStars(stars, currentRating);
        updateRatingText(ratingText, currentRating);
    });
}

function highlightStars(stars, rating) {
    stars.forEach((star, index) => {
        if (index < rating) {
            star.classList.remove('bi-star');
            star.classList.add('bi-star-fill');
            star.style.color = '#ffc107';
        } else {
            star.classList.remove('bi-star-fill');
            star.classList.add('bi-star');
            star.style.color = '#6c757d';
        }
    });
}

function updateRatingText(ratingText, rating) {
    const texts = {
        0: '評価を選択',
        1: '最悪',
        2: '悪い',
        3: '普通',
        4: '良い',
        5: '最高'
    };
    if (ratingText) {
        ratingText.textContent = texts[rating] || '評価を選択';
    }
}

function resetStarRating() {
    const stars = document.querySelectorAll('.star-icon');
    const ratingText = document.getElementById('reviewRating');
    const ratingTextSpan = document.getElementById('ratingText');
    
    if (ratingText) {
        ratingText.value = '';
    }
    if (ratingTextSpan) {
        ratingTextSpan.textContent = '評価を選択';
    }
    if (stars.length > 0) {
        highlightStars(Array.from(stars), 0);
    }
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function formatProductDescription(description) {
    if (!description) return '';
    
    // Escape HTML first to prevent XSS
    let formatted = escapeHtml(description);
    
    // Convert line breaks to <br> tags
    formatted = formatted.replace(/\n/g, '<br>');
    
    // Format numbered lists (lines starting with numbers like "1. ", "2. ", etc.)
    formatted = formatted.replace(/(\d+)\.\s+(.+?)(?=<br>|$)/g, '<div class="mb-2"><strong class="text-primary">$1.</strong> $2</div>');
    
    // Format bold text (text between **)
    formatted = formatted.replace(/\*\*(.+?)\*\*/g, '<strong style="color: var(--text-color);">$1</strong>');
    
    // Format text in brackets like 【text】
    formatted = formatted.replace(/【(.+?)】/g, '<span class="text-info fw-semibold">【$1】</span>');
    
    // Format text in parentheses like (text)
    formatted = formatted.replace(/\((.+?)\)/g, '<span class="text-muted">($1)</span>');
    
    return formatted;
}

// ==================== OFFER FUNCTIONS ====================
function openOfferModal(productId, productPrice) {
    if (!window.currentUserId) {
        window.location.href = `${window.CONTEXT_PATH}/login.jsp?redirect=${encodeURIComponent(window.location.pathname + window.location.search)}`;
        return;
    }
    
    // Create modal if it doesn't exist
    let modal = document.getElementById('offerModal');
    if (!modal) {
        modal = document.createElement('div');
        modal.id = 'offerModal';
        modal.className = 'modal fade';
        modal.setAttribute('tabindex', '-1');
        modal.innerHTML = `
            <div class="modal-dialog modal-dialog-centered">
                <div class="modal-content">
                    <div class="modal-header">
                        <h5 class="modal-title">
                            <i class="bi bi-hand-thumbs-up me-2 text-warning"></i>値段交渉
                        </h5>
                        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                    </div>
                    <div class="modal-body">
                        <div class="mb-3">
                            <label class="form-label">現在の価格</label>
                            <div class="text-primary fw-bold fs-4" id="currentPriceDisplay">¥0</div>
                        </div>
                        <div class="mb-3">
                            <label for="offerPrice" class="form-label">
                                オファー価格 <span class="text-danger">*</span>
                            </label>
                            <div class="input-group">
                                <span class="input-group-text input-group-text-light">¥</span>
                                <input type="number" class="form-control form-control-light" 
                                       id="offerPrice" step="0.01" min="0.01" required
                                       placeholder="例: 2500">
                            </div>
                            <small class="text-muted d-block mt-1">
                                <i class="bi bi-info-circle me-1"></i>現在の価格より低い価格を提案できます（最大: <span id="maxOfferPrice" class="fw-semibold text-primary"></span>）
                            </small>
                        </div>
                        <div class="mb-3">
                            <label for="offerMessage" class="form-label">メッセージ（任意）</label>
                            <textarea class="form-control form-control-light" 
                                      id="offerMessage" rows="3" 
                                      placeholder="オファーに関するメッセージを入力してください"></textarea>
                        </div>
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">キャンセル</button>
                        <button type="button" class="btn btn-warning" onclick="submitOffer(${productId})">
                            <i class="bi bi-send me-2"></i>オファーを送信
                        </button>
                    </div>
                </div>
            </div>
        `;
        document.body.appendChild(modal);
    }
    
    // Set current price
    const currentPriceDisplay = document.getElementById('currentPriceDisplay');
    if (currentPriceDisplay) {
        currentPriceDisplay.textContent = KaruruUtils.formatPrice(productPrice);
    }
    
    // Set max offer price display
    const maxOfferPriceDisplay = document.getElementById('maxOfferPrice');
    if (maxOfferPriceDisplay) {
        const maxOfferPrice = productPrice - 0.01;
        maxOfferPriceDisplay.textContent = KaruruUtils.formatPrice(maxOfferPrice);
    }
    
    // Set max price (must be less than product price)
    const offerPriceInput = document.getElementById('offerPrice');
    if (offerPriceInput) {
        const maxOfferPrice = productPrice - 0.01;
        offerPriceInput.max = maxOfferPrice;
        offerPriceInput.value = '';
        offerPriceInput.setAttribute('data-product-id', productId);
        offerPriceInput.setAttribute('data-product-price', productPrice);
        
        // Add validation on input
        offerPriceInput.addEventListener('input', function() {
            const value = parseFloat(this.value);
            const maxPrice = parseFloat(this.max);
            const minPrice = 0.01;
            
            if (isNaN(value) || value <= 0) {
                this.setCustomValidity('オファー価格を入力してください');
            } else if (value > maxPrice) {
                this.setCustomValidity(`オファー価格は現在の価格（${KaruruUtils.formatPrice(productPrice)}）より低くする必要があります`);
            } else if (value < minPrice) {
                this.setCustomValidity('オファー価格は0より大きい必要があります');
            } else {
                this.setCustomValidity('');
            }
        });
    }
    
    // Clear message
    const offerMessage = document.getElementById('offerMessage');
    if (offerMessage) {
        offerMessage.value = '';
    }
    
    // Show modal
    const bsModal = new bootstrap.Modal(modal);
    bsModal.show();
}

async function submitOffer(productId) {
    const offerPriceInput = document.getElementById('offerPrice');
    const offerMessage = document.getElementById('offerMessage');
    
    if (!offerPriceInput || !offerPriceInput.value) {
        return;
    }
    
    const offerPrice = parseFloat(offerPriceInput.value);
    const productPrice = parseFloat(offerPriceInput.getAttribute('data-product-price') || 0);
    const message = offerMessage ? offerMessage.value : '';
    
    if (offerPrice <= 0) {
        offerPriceInput.focus();
        return;
    }
    
    if (offerPrice >= productPrice) {
        offerPriceInput.focus();
        return;
    }
    
    try {
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/OfferServlet`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: new URLSearchParams({
                action: 'create',
                product_id: productId.toString(),
                offer_price: offerPrice.toString(),
                message: message
            })
        });
        
        if (data && data.success !== false) {
            // Close modal
            const modal = document.getElementById('offerModal');
            if (modal) {
                const bsModal = bootstrap.Modal.getInstance(modal);
                if (bsModal) {
                    bsModal.hide();
                }
            }
            
            // Clear form
            const offerPriceInput = document.getElementById('offerPrice');
            const offerMessage = document.getElementById('offerMessage');
            if (offerPriceInput) offerPriceInput.value = '';
            if (offerMessage) offerMessage.value = '';
            
            // Notification
            if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
                KaruruUtils.showNotification(data.message || 'オファーを送信しました', 'success');
            }
            
            // Redirect to chat with seller (like ask seller) to discuss the offer
            const sellerId = data.seller_id;
            if (sellerId) {
                setTimeout(() => {
                    window.location.href = `${window.CONTEXT_PATH}/messages.jsp?user_id=${sellerId}&product_id=${productId}&type=product`;
                }, 800);
            } else {
                // Fallback: redirect to offers page
                setTimeout(() => {
                    window.location.href = `${window.CONTEXT_PATH}/offers.jsp`;
                }, 800);
            }
        } else {
            throw new Error(data?.error || data?.message || 'Failed to create offer');
        }
    } catch (error) {
        console.error('Error submitting offer:', error);
        if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
            KaruruUtils.showNotification(error.message || 'オファーの送信に失敗しました', 'danger');
        }
    }
}
