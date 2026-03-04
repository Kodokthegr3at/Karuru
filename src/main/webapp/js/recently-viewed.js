// Recently Viewed Page JavaScript

document.addEventListener('DOMContentLoaded', function() {
    loadRecentlyViewed();
});

async function loadRecentlyViewed() {
    const container = document.getElementById('recentlyViewedProducts');
    const emptyState = document.getElementById('emptyRecentlyViewed');
    
    if (!container || !emptyState) {
        console.error('Container elements not found');
        return;
    }
    
    try {
        const contextPath = window.CONTEXT_PATH || '';
        const url = `${contextPath}/RecentlyViewedServlet?action=getRecentlyViewed`;
        console.log('Fetching recently viewed from:', url);
        
        const response = await fetch(url, {
            method: 'GET',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            },
            credentials: 'same-origin'
        });
        
        console.log('Response status:', response.status, response.statusText);
        
        if (!response.ok) {
            const errorText = await response.text();
            console.error('Error response:', errorText);
            throw new Error(`HTTP error! status: ${response.status}, message: ${errorText}`);
        }
        
        const contentType = response.headers.get('content-type');
        if (!contentType || !contentType.includes('application/json')) {
            const text = await response.text();
            console.error('Response is not JSON:', text);
            throw new Error('Response is not JSON format');
        }
        
        const data = await response.json();
        console.log('Recently viewed data received:', data);
        
        // Hide spinner
        container.innerHTML = '';
        
        if (data && data.success !== false && data.products && Array.isArray(data.products) && data.products.length > 0) {
            console.log('Rendering', data.products.length, 'products');
            renderRecentlyViewed(data.products);
            container.style.display = 'flex';
            emptyState.style.display = 'none';
        } else {
            console.log('No products found or empty array');
            container.style.display = 'none';
            emptyState.style.display = 'flex';
        }
    } catch (error) {
        console.error('Error loading recently viewed:', error);
        console.error('Error stack:', error.stack);
        
        if (container) {
            container.innerHTML = `
                <div class="col-12">
                    <div class="text-center py-5 text-danger">
                        <i class="bi bi-exclamation-triangle" style="font-size: 3rem;"></i>
                        <p class="mt-3 mb-0">読み込みに失敗しました</p>
                        <small class="text-muted d-block mt-2">${error.message || 'エラーが発生しました'}</small>
                        <button onclick="loadRecentlyViewed()" class="btn btn-primary mt-3">
                            <i class="bi bi-arrow-clockwise me-1"></i>再読み込み
                        </button>
                    </div>
                </div>
            `;
            container.style.display = 'block';
        }
        if (emptyState) {
            emptyState.style.display = 'none';
        }
    }
}

function renderRecentlyViewed(products) {
    const container = document.getElementById('recentlyViewedProducts');
    if (!container) return;
    
    const contextPath = window.CONTEXT_PATH || '';
    
    container.innerHTML = products.map(product => {
        // Handle image URL - same logic as products.js
        const images = product.images || (product.image_url ? [product.image_url] : []);
        let firstImage = images.length > 0 ? images[0] : null;
        
        // If no image found, use default
        if (!firstImage || firstImage.trim() === '') {
            firstImage = '/img/default-product.png';
        }
        
        // Build image URL - handle all cases
        let imageUrl;
        if (firstImage.startsWith('/')) {
            imageUrl = `${contextPath}${firstImage}`;
        } else if (firstImage.startsWith('http://') || firstImage.startsWith('https://')) {
            imageUrl = firstImage;
        } else {
            // Relative path like "img/products/iphone14.jpg"
            imageUrl = `${contextPath}/${firstImage}`;
        }
        
        console.log(`Product ${product.product_id} (${product.product_name}) image:`, {
            original: firstImage,
            final: imageUrl,
            hasImages: images.length > 0,
            image_url: product.image_url,
            images_array: product.images
        });
        
        const productUrl = `${contextPath}/product-detail.jsp?id=${product.product_id}`;
        
        return `
            <div class="col-6 col-md-6 col-lg-4 col-xl-3 mb-4">
                <a href="${productUrl}" class="product-card-link text-decoration-none">
                    <div class="card card-light h-100 product-card-modern">
                        <div class="product-image-wrapper">
                            <div class="product-image-carousel">
                                <img src="${imageUrl}" 
                                     class="product-image active" 
                                     alt="${escapeHtml(product.product_name || '商品')}"
                                     loading="lazy"
                                     onerror="console.error('Image failed to load:', '${imageUrl}'); this.src='${contextPath}/img/default-product.png'; this.onerror=null;">
                            </div>
                            <div class="product-overlay">
                                <span class="badge bg-primary px-3 py-2">
                                    <i class="bi bi-eye me-1"></i>詳細を見る
                                </span>
                            </div>
                            <div class="product-badges position-absolute top-0 start-0 end-0 p-2 d-flex flex-wrap gap-1" style="z-index: 4;">
                                ${product.is_negotiable ? '<span class="product-badge product-badge-nego"><i class="bi bi-hand-thumbs-up me-1"></i>価格交渉可</span>' : ''}
                                ${product.is_rental ? '<span class="product-badge product-badge-rental"><i class="bi bi-calendar-check me-1"></i>レンタル</span>' : ''}
                            </div>
                        </div>
                        <div class="card-body d-flex flex-column">
                            <h6 class="card-title mb-2" style="display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;">
                                ${escapeHtml(product.product_name || '商品')}
                            </h6>
                            <p class="text-primary fw-bold fs-5 mb-auto">
                                ${formatPrice(product.price || 0)}
                            </p>
                            <div class="mt-2">
                                <span class="text-muted product-date d-block">
                                    <i class="bi bi-clock me-1"></i>
                                    ${formatDate(product.last_viewed || product.viewed_at || product.created_at)}
                                </span>
                            </div>
                        </div>
                    </div>
                </a>
            </div>
        `;
    }).join('');
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
    const now = new Date();
    const diff = now - date;
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);
    
    if (minutes < 60) {
        return `${minutes}分前`;
    } else if (hours < 24) {
        return `${hours}時間前`;
    } else if (days < 7) {
        return `${days}日前`;
    } else {
        return date.toLocaleDateString('ja-JP', { 
            year: 'numeric', 
            month: 'long', 
            day: 'numeric'
        });
    }
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

