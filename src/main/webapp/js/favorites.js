// Favorites page JavaScript

document.addEventListener('DOMContentLoaded', function() {
    loadFavorites();
});

async function loadFavorites() {
    try {
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/FavoriteServlet?action=getFavorites`);
        
        // FavoriteServlet returns array directly, not wrapped in object
        const favorites = Array.isArray(data) ? data : (data.items || data.products || []);
        
        const grid = document.getElementById('favoritesGrid');
        const emptyState = document.getElementById('emptyFavorites');
        
        if (!grid || !emptyState) return;
        
        if (favorites && favorites.length > 0) {
            renderFavorites(favorites);
            grid.style.display = 'flex';
            emptyState.style.display = 'none';
        } else {
            grid.innerHTML = '';
            grid.style.display = 'none';
            emptyState.style.display = 'flex';
        }
    } catch (error) {
        console.error('Error loading favorites:', error);
        const grid = document.getElementById('favoritesGrid');
        const emptyState = document.getElementById('emptyFavorites');
        
        if (grid) {
            grid.innerHTML = `
                <div class="col-12">
                    <div class="text-center py-5 text-danger">
                        <i class="bi bi-exclamation-triangle" style="font-size: 3rem;"></i>
                        <p class="mt-3 mb-0">お気に入りの読み込みに失敗しました</p>
                        <small class="text-muted d-block mt-2">${error.message || ''}</small>
                    </div>
                </div>
            `;
        }
    }
}

function renderFavorites(favorites) {
    const grid = document.getElementById('favoritesGrid');
    if (!grid) return;
    
    grid.innerHTML = favorites.map(product => {
        // Get all images
        const images = product.images || [];
        const mainImage = product.image_url || (images.length > 0 ? images[0] : '/img/default-product.png');
        const allImages = [mainImage, ...images.filter(img => img !== mainImage && img)].slice(0, 4);
        
        const productUrl = `${window.CONTEXT_PATH}/product-detail.jsp?id=${product.product_id}`;
        
        return `
            <div class="col-6 col-lg-3 col-md-4 col-sm-6 mb-4">
                <div class="position-relative">
                    <a href="${productUrl}" class="product-card-link text-decoration-none">
                        <div class="card product-card-modern h-100">
                            <div class="product-image-wrapper">
                                <div class="product-image-carousel" data-product-id="${product.product_id}">
                                    ${allImages.map((img, idx) => {
                                        const imageUrl = img.startsWith('/') ? `${window.CONTEXT_PATH}${img}` : 
                                                        img.startsWith('http') ? img : 
                                                        `${window.CONTEXT_PATH}/${img}`;
                                        return `
                                            <img src="${imageUrl}" 
                                                 class="product-image ${idx === 0 ? 'active' : ''}" 
                                                 alt="${escapeHtml(product.product_name || '商品')}"
                                                 loading="lazy"
                                                 onerror="this.src='${window.CONTEXT_PATH}/img/default-product.png'">
                                        `;
                                    }).join('')}
                                    ${allImages.length === 0 ? `
                                        <img src="${window.CONTEXT_PATH}/img/default-product.png" 
                                             class="product-image active" 
                                             alt="${escapeHtml(product.product_name || '商品')}"
                                             loading="lazy">
                                    ` : ''}
                                </div>
                                ${allImages.length > 1 ? `
                                    <div class="product-image-indicators">
                                        ${allImages.slice(0, 4).map((_, idx) => `
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
                                <h6 class="card-title mb-2" style="display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;">
                                    ${escapeHtml(product.product_name || '商品名なし')}
                                </h6>
                                <div class="d-flex justify-content-between align-items-center mb-2">
                                    <span class="text-primary fw-bold fs-5">${KaruruUtils.formatPrice(product.price || 0)}</span>
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
                                ${product.seller_name ? `
                                    <small class="d-block text-muted mb-2">
                                        <i class="bi bi-person me-1"></i>${escapeHtml(product.seller_name)}
                                    </small>
                                ` : ''}
                                ${product.rating_avg > 0 ? `
                                    <small class="d-block text-muted">
                                        <i class="bi bi-star-fill text-warning me-1"></i>${product.rating_avg.toFixed(1)}
                                        ${product.rating_count > 0 ? `(${product.rating_count})` : ''}
                                    </small>
                                ` : ''}
                            </div>
                        </div>
                    </a>
                    <button class="btn btn-outline-danger btn-sm position-absolute top-0 end-0 m-2" 
                            onclick="removeFavorite(${product.product_id}, event)" 
                            style="z-index: 10; backdrop-filter: blur(4px); background: rgba(220, 53, 69, 0.8);"
                            title="お気に入りから削除">
                        <i class="bi bi-heart-fill"></i>
                    </button>
                </div>
            </div>
        `;
    }).join('');
    
    // Setup image carousels for all products after a short delay to ensure DOM is ready
    setTimeout(() => {
        grid.querySelectorAll('.product-image-carousel').forEach(carousel => {
            setupProductImageCarousel(carousel);
        });
    }, 100);
}

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
}

async function removeFavorite(productId, event) {
    if (event) {
        event.preventDefault();
        event.stopPropagation();
    }
    
    if (!KaruruUtils.confirmDialog('お気に入りから削除しますか？')) {
        return;
    }
    
    try {
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/FavoriteServlet`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: new URLSearchParams({
                action: 'remove',
                productId: productId.toString()
            })
        });
        
        if (data && data.success !== false) {
            // Update favorite icon in header
            if (typeof window !== 'undefined' && window.updateFavoriteIcon) {
                window.updateFavoriteIcon();
            }
            
            loadFavorites();
        } else {
            throw new Error(data?.error || data?.message || 'Failed to remove favorite');
        }
    } catch (error) {
        console.error('Error removing favorite:', error);
    }
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
