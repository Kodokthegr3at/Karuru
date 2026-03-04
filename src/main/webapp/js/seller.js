// Seller profile page JavaScript

document.addEventListener('DOMContentLoaded', function() {
    const urlParams = new URLSearchParams(window.location.search);
    const sellerId = urlParams.get('seller_id');
    
    if (sellerId) {
        loadSellerProfile(sellerId);
        loadSellerProducts(sellerId);
    } else {
        const container = document.getElementById('sellerProfile');
        if (container) {
            container.innerHTML = `
                <div class="text-center py-5 text-danger">
                    <i class="bi bi-exclamation-triangle" style="font-size: 3rem;"></i>
                    <p class="mt-3 mb-0">出品者IDが必要です</p>
                </div>
            `;
        }
    }
});

async function loadSellerProfile(sellerId) {
    try {
        console.log('Loading seller profile for seller_id:', sellerId);
        const url = `${window.CONTEXT_PATH}/SellerProfileServlet?seller_id=${sellerId}`;
        console.log('Fetching from URL:', url);
        
        const data = await KaruruUtils.apiFetch(url);
        console.log('Received data:', data);
        
        const seller = KaruruUtils.extractData(data, 'seller') || data.seller;
        
        if (!seller) {
            throw new Error('Seller not found');
        }
        
        console.log('Seller data:', seller);
        renderSellerProfile(seller);
    } catch (error) {
        console.error('Error loading seller profile:', error);
        console.error('Error stack:', error.stack);
        const container = document.getElementById('sellerProfile');
        if (container) {
            container.innerHTML = `
                <div class="text-center py-5 text-danger">
                    <i class="bi bi-exclamation-triangle" style="font-size: 3rem;"></i>
                    <p class="mt-3 mb-0">出品者情報の読み込みに失敗しました</p>
                    <small class="text-muted d-block mt-2">${error.message || ''}</small>
                    <button class="btn btn-primary mt-3" onclick="location.reload()">再読み込み</button>
                </div>
            `;
        }
    }
}

function renderSellerProfile(seller) {
    const container = document.getElementById('sellerProfile');
    if (!container) return;
    
    // Resolve avatar URL with proper context path handling
    const avatarUrl = KaruruUtils.resolveAvatarUrl(seller.avatar_url);
    const rating = seller.avg_rating || 0;
    const ratingCount = seller.total_reviews || 0;
    const stars = Math.round(rating);
    const ratingStars = Array.from({ length: 5 }, (_, i) => 
        i < stars ? '<i class="bi bi-star-fill text-warning"></i>' : '<i class="bi bi-star text-muted"></i>'
    ).join('');
    
    container.innerHTML = `
        <div class="card card-light shadow-lg">
            <div class="card-body p-4">
                <div class="row align-items-center">
                    <div class="col-md-3 text-center mb-3 mb-md-0">
                        <img src="${avatarUrl}" 
                             alt="${escapeHtml(seller.username || 'Seller')}" 
                             class="rounded-circle mb-3"
                             style="width: 120px; height: 120px; object-fit: cover; border: 3px solid var(--bs-primary);"
                             onerror="this.onerror=null; this.src='${KaruruUtils.resolveAvatarUrl('')}';">
                        ${seller.is_verified ? `
                            <div class="mb-2">
                                <span class="badge bg-primary">
                                    <i class="bi bi-check-circle me-1"></i>認証済み
                                </span>
                            </div>
                        ` : ''}
                    </div>
                    <div class="col-md-9">
                        <h2 class="mb-2">${escapeHtml(seller.full_name || seller.username || '出品者')}</h2>
                        <p class="text-muted mb-3">@${escapeHtml(seller.username || '')}</p>
                        
                        ${seller.bio ? `
                            <p class="text-muted mb-3">${escapeHtml(seller.bio)}</p>
                        ` : ''}
                        
                        <div class="row g-3 mb-3">
                            <div class="col-6 col-md-3">
                                <div class="text-center p-3 rounded-3" style="background: rgba(255, 255, 255, 0.03); border: 1px solid rgba(255, 255, 255, 0.1);">
                                    <div class="text-primary fw-bold fs-4">${seller.total_products || 0}</div>
                                    <div class="text-muted small">出品数</div>
                                </div>
                            </div>
                            <div class="col-6 col-md-3">
                                <div class="text-center p-3 rounded-3" style="background: rgba(255, 255, 255, 0.03); border: 1px solid rgba(255, 255, 255, 0.1);">
                                    <div class="text-success fw-bold fs-4">${seller.total_sales || 0}</div>
                                    <div class="text-muted small">販売数</div>
                                </div>
                            </div>
                            <div class="col-6 col-md-3">
                                <div class="text-center p-3 rounded-3" style="background: rgba(255, 255, 255, 0.03); border: 1px solid rgba(255, 255, 255, 0.1);">
                                    <div class="text-warning fw-bold fs-4">${rating.toFixed(1)}</div>
                                    <div class="text-muted small">評価</div>
                                    ${ratingCount > 0 ? `
                                        <div class="rating-stars mt-1" style="font-size: 0.8rem;">
                                            ${ratingStars}
                                        </div>
                                        <div class="text-muted" style="font-size: 0.75rem;">(${ratingCount}件)</div>
                                    ` : '<div class="text-muted" style="font-size: 0.75rem;">レビューなし</div>'}
                                </div>
                            </div>
                            <div class="col-6 col-md-3">
                                <div class="text-center p-3 rounded-3" style="background: rgba(255, 255, 255, 0.03); border: 1px solid rgba(255, 255, 255, 0.1);">
                                    <div class="text-info fw-bold fs-4">${seller.total_reviews || 0}</div>
                                    <div class="text-muted small">レビュー</div>
                                </div>
                            </div>
                        </div>
                        
                        ${seller.created_at ? `
                            <p class="text-muted small mb-0">
                                <i class="bi bi-calendar me-1"></i>登録日: ${KaruruUtils.formatDate(seller.created_at)}
                            </p>
                        ` : ''}
                    </div>
                </div>
            </div>
        </div>
    `;
}

async function loadSellerProducts(sellerId) {
    try {
        console.log('Loading seller products for seller_id:', sellerId);
        const url = `${window.CONTEXT_PATH}/SellerProfileServlet?seller_id=${sellerId}`;
        console.log('Fetching from URL:', url);
        
        const data = await KaruruUtils.apiFetch(url);
        console.log('Received data:', data);
        
        const products = KaruruUtils.extractData(data, 'products') || data.products || [];
        console.log('Products data:', products);
        console.log('Products count:', products.length);
        
        if (products.length > 0) {
            console.log('First product sample:', {
                product_id: products[0].product_id,
                product_name: products[0].product_name,
                image_url: products[0].image_url,
                images: products[0].images,
                hasImages: products[0].images && products[0].images.length > 0,
                imagesType: typeof products[0].images,
                imagesIsArray: Array.isArray(products[0].images)
            });
            
            // Log all products image data
            products.forEach((p, idx) => {
                console.log(`Product ${idx + 1} (ID: ${p.product_id}, Name: ${p.product_name}):`, {
                    image_url: p.image_url,
                    images: p.images,
                    imagesType: typeof p.images,
                    imagesIsArray: Array.isArray(p.images),
                    imagesLength: Array.isArray(p.images) ? p.images.length : 'N/A'
                });
            });
        }
        
        renderSellerProducts(products);
    } catch (error) {
        console.error('Error loading seller products:', error);
        console.error('Error stack:', error.stack);
        const container = document.getElementById('sellerProducts');
        if (container) {
            container.innerHTML = `
                <div class="col-12 text-center py-5 text-danger">
                    <i class="bi bi-exclamation-triangle" style="font-size: 2rem;"></i>
                    <p class="mt-3 mb-0">商品の読み込みに失敗しました</p>
                    <button class="btn btn-primary mt-3" onclick="location.reload()">再読み込み</button>
                </div>
            `;
        }
    }
}

function renderSellerProducts(products) {
    const container = document.getElementById('sellerProducts');
    if (!container) return;
    
    if (!products || products.length === 0) {
        container.innerHTML = `
            <div class="col-12 text-center py-5">
                <i class="bi bi-box-seam" style="font-size: 3rem; color: var(--bs-primary); opacity: 0.3;"></i>
                <p class="mt-3 text-muted">出品商品がありません</p>
            </div>
        `;
        return;
    }
    
    container.innerHTML = products.map(product => {
        // Handle image URL - exact same logic as products.js
        const images = Array.isArray(product.images) ? product.images.filter(img => img && img.trim && img.trim() !== '') : [];
        let mainImage = product.image_url || (images.length > 0 ? images[0] : '/img/default-product.png');
        
        // Ensure mainImage is valid
        if (!mainImage || mainImage === 'null' || mainImage === 'undefined' || (typeof mainImage === 'string' && mainImage.trim() === '')) {
            mainImage = '/img/default-product.png';
        }
        
        // Build allImages array, filtering out duplicates and invalid values
        const allImages = [mainImage, ...images.filter(img => img && img !== mainImage && img.trim && img.trim() !== '')].slice(0, 4);
        const price = parseInt(product.price || 0).toLocaleString('ja-JP');
        
        const productUrl = `${window.CONTEXT_PATH}/product-detail.jsp?id=${product.product_id}`;
        
        return `
            <div class="col-6 col-md-3 col-sm-6 mb-4">
                <a href="${productUrl}" class="product-card-link text-decoration-none">
                    <div class="card product-card-modern h-100">
                        <div class="product-image-wrapper">
                            <div class="product-image-carousel" data-product-id="${product.product_id}">
                                ${allImages.length > 0 ? allImages.map((img, idx) => {
                                    // Ensure img is a valid string
                                    const imgStr = (img && typeof img === 'string') ? img.trim() : '/img/default-product.png';
                                    const imageUrl = imgStr.startsWith('/') ? `${window.CONTEXT_PATH}${imgStr}` : 
                                                    imgStr.startsWith('http') ? imgStr : 
                                                    `${window.CONTEXT_PATH}/${imgStr}`;
                                    return `
                                    <img src="${imageUrl}" 
                                         alt="${escapeHtml(product.product_name || '商品')}" 
                                         class="product-image ${idx === 0 ? 'active' : ''}"
                                         loading="lazy"
                                         onerror="this.src='${window.CONTEXT_PATH}/img/default-product.png'">
                                `;
                                }).join('') : `
                                    <img src="${window.CONTEXT_PATH}/img/default-product.png" 
                                         alt="${escapeHtml(product.product_name || '商品')}" 
                                         class="product-image active"
                                         loading="lazy">
                                `}
                            </div>
                            ${allImages.length > 1 ? `
                                <div class="product-image-indicators">
                                    ${allImages.map((_, idx) => `
                                        <span class="image-dot ${idx === 0 ? 'active' : ''}" data-index="${idx}"></span>
                                    `).join('')}
                                </div>
                            ` : ''}
                            <div class="product-overlay">
                                <span class="badge bg-primary px-3 py-2">詳細を見る</span>
                            </div>
                            <div class="product-badges position-absolute top-0 start-0 end-0 p-2 d-flex flex-wrap gap-1" style="z-index: 4;">
                                ${product.is_negotiable ? '<span class="product-badge product-badge-nego"><i class="bi bi-hand-thumbs-up me-1"></i>価格交渉可</span>' : ''}
                                ${product.is_rental ? '<span class="product-badge product-badge-rental"><i class="bi bi-calendar-check me-1"></i>レンタル</span>' : ''}
                            </div>
                        </div>
                        <div class="card-body">
                            <h6 class="card-title mb-2" style="min-height: 2.85em; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;">
                                ${escapeHtml(product.product_name || '商品名')}
                            </h6>
                            <div class="d-flex justify-content-between align-items-center mb-2">
                                <span class="fw-bold fs-5">¥${price}</span>
                                ${(product.rental_price_daily || product.rental_price_weekly || product.rental_price_monthly) ? `
                                    <div class="d-flex flex-wrap gap-1 mt-1">
                                        ${product.rental_price_daily ? `
                                            <small class="text-muted">/日 ${KaruruUtils.formatPrice(product.rental_price_daily)}</small>
                                        ` : ''}
                                        ${product.rental_price_weekly ? `
                                            <small class="text-muted">/週 ${KaruruUtils.formatPrice(product.rental_price_weekly)}</small>
                                        ` : ''}
                                        ${product.rental_price_monthly ? `
                                            <small class="text-muted">/月 ${KaruruUtils.formatPrice(product.rental_price_monthly)}</small>
                                        ` : ''}
                                    </div>
                                ` : ''}
                            </div>
                            ${product.rating_avg > 0 ? `
                                <small class="d-block text-muted">
                                    <i class="bi bi-star-fill text-warning"></i> ${product.rating_avg.toFixed(1)}
                                </small>
                            ` : ''}
                        </div>
                    </div>
                </a>
            </div>
        `;
    }).join('');
    
    // Setup image carousels for all products after a short delay to ensure DOM is ready
    setTimeout(() => {
        container.querySelectorAll('.product-image-carousel').forEach(carousel => {
            setupProductImageCarousel(carousel);
        });
    }, 100);
}

function setupProductImageCarousel(carousel) {
    const images = carousel.querySelectorAll('.product-image');
    const cardLink = carousel.closest('.product-card-link');
    const imageWrapper = carousel.closest('.product-image-wrapper') || carousel.closest('.product-image-container');
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
    
    // Auto-rotate on hover - attach to card link or wrapper
    const hoverTarget = cardLink || imageWrapper || carousel.parentElement;
    
    if (hoverTarget) {
        hoverTarget.addEventListener('mouseenter', () => {
            if (hoverInterval) {
                clearInterval(hoverInterval);
            }
            // Start immediately, then continue
            nextImage();
            hoverInterval = setInterval(nextImage, 1500); // Faster rotation
        });
        
        hoverTarget.addEventListener('mouseleave', () => {
            if (hoverInterval) {
                clearInterval(hoverInterval);
                hoverInterval = null;
            }
            // Reset to first image
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
            // Restart interval if hovering
            if (hoverInterval) {
                clearInterval(hoverInterval);
                hoverInterval = setInterval(nextImage, 1500);
            }
        });
    });
    
    // Initialize first image
    showImage(0);
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

