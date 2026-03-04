// Home page JavaScript

document.addEventListener('DOMContentLoaded', function() {
    loadBanners();
    loadCategories();
    loadFeaturedProducts();
    loadRecentProducts();
    loadPopularProducts();
});

async function loadBanners() {
    try {
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/BannerServlet?action=getActive`);
        const banners = KaruruUtils.extractData(data, 'banners') || [];
        
        const slider = document.getElementById('bannerSlider');
        if (!slider) return;
        
        const carouselInner = slider.querySelector('.carousel-inner');
        if (!carouselInner) return;
        
        if (banners && banners.length > 0) {
            carouselInner.innerHTML = '';
            
            banners.forEach((banner, index) => {
                const bannerElement = document.createElement('div');
                bannerElement.className = `carousel-item ${index === 0 ? 'active' : ''}`;
                
                const imageUrl = banner.image_url || '/img/default-banner.jpg';
                const fullImageUrl = imageUrl.startsWith('/') ? window.CONTEXT_PATH + imageUrl : 
                                    (imageUrl.startsWith('http') ? imageUrl : window.CONTEXT_PATH + '/' + imageUrl);
                
                // Handle link_url - if it already contains context path (like /KaruruFleaMarket/products.jsp), use as is
                // Otherwise, add context path
                let linkUrl = banner.link_url || null;
                
                if (linkUrl && linkUrl.trim() !== '' && linkUrl !== 'null' && linkUrl !== 'undefined') {
                    linkUrl = linkUrl.trim();
                    
                    // Normalize home.jsp to index.jsp - check all possible variations
                    const homeJspVariations = ['home.jsp', '/home.jsp', 'home.jsp/', '/home.jsp/'];
                    const lowerLinkUrl = linkUrl.toLowerCase();
                    
                    // Check if linkUrl is exactly home.jsp or ends with /home.jsp (but not if it already has context path)
                    if (!linkUrl.startsWith(window.CONTEXT_PATH)) {
                        if (homeJspVariations.includes(linkUrl) || 
                            lowerLinkUrl === 'home.jsp' || 
                            lowerLinkUrl === '/home.jsp' ||
                            lowerLinkUrl.endsWith('/home.jsp') ||
                            lowerLinkUrl.endsWith('/home.jsp/') ||
                            (linkUrl.toLowerCase().includes('home.jsp') && !linkUrl.toLowerCase().includes('index.jsp'))) {
                            linkUrl = '/index.jsp';
                            console.log('Normalized home.jsp to /index.jsp');
                        }
                    } else if (linkUrl.toLowerCase().includes('/home.jsp')) {
                        // If it has context path but contains home.jsp, replace it
                        linkUrl = linkUrl.replace(/\/home\.jsp$/i, '/index.jsp');
                        linkUrl = linkUrl.replace(/home\.jsp$/i, 'index.jsp');
                        console.log('Normalized home.jsp to index.jsp in context path');
                    }
                    
                    // Handle different URL formats
                    if (linkUrl.startsWith('http://') || linkUrl.startsWith('https://')) {
                        // External URL, use as is
                        console.log('Using external URL as is');
                    } else if (linkUrl.startsWith(window.CONTEXT_PATH)) {
                        // Already contains context path (e.g., /KaruruFleaMarket/products.jsp), use as is
                        console.log('Using URL with context path as is');
                    } else if (linkUrl.startsWith('/')) {
                        // Absolute path without context (e.g., /products.jsp), add context path
                        linkUrl = window.CONTEXT_PATH + linkUrl;
                        console.log('Added context path to absolute path');
                    } else {
                        // Relative path (e.g., products.jsp), add context path with slash
                        linkUrl = window.CONTEXT_PATH + '/' + linkUrl;
                        console.log('Added context path to relative path');
                    }
                } else {
                    // No link URL, use # to prevent navigation
                    linkUrl = '#';
                }
                
                console.log('Banner link URL resolved:', {
                    original: banner.link_url,
                    resolved: linkUrl,
                    contextPath: window.CONTEXT_PATH,
                    bannerId: banner.banner_id
                });
                
                bannerElement.innerHTML = `
                    <a href="${linkUrl}" class="banner-link">
                        <div class="banner-overlay"></div>
                        <img src="${fullImageUrl}" 
                             class="d-block w-100 banner-image" 
                             alt="${escapeHtml(banner.title || 'Banner')}"
                             onerror="this.src='${window.CONTEXT_PATH}/img/default-banner.jpg'">
                        ${banner.title ? `
                            <div class="banner-caption">
                                <h3 class="banner-title">${escapeHtml(banner.title)}</h3>
                            </div>
                        ` : ''}
                    </a>
                `;
                carouselInner.appendChild(bannerElement);
            });
        } else {
            carouselInner.innerHTML = `
                <div class="carousel-item active">
                    <div class="banner-placeholder d-flex align-items-center justify-content-center">
                        <div class="text-center text-muted">
                            <i class="bi bi-image" style="font-size: 4rem; opacity: 0.3;"></i>
                            <p class="mt-3 mb-0">バナーがありません</p>
                        </div>
                    </div>
                </div>
            `;
        }
    } catch (error) {
        console.error('Error loading banners:', error);
        const slider = document.getElementById('bannerSlider');
        if (slider) {
            const carouselInner = slider.querySelector('.carousel-inner');
            if (carouselInner) {
                carouselInner.innerHTML = `
                    <div class="carousel-item active">
                        <div class="banner-placeholder d-flex align-items-center justify-content-center">
                            <div class="text-center text-danger">
                                <i class="bi bi-exclamation-triangle" style="font-size: 3rem;"></i>
                                <p class="mt-3 mb-0">バナーの読み込みに失敗しました</p>
                            </div>
                        </div>
                    </div>
                `;
            }
        }
    }
}

async function loadCategories() {
    try {
        // Try CategoryServlet first, fallback to ProductServlet if not available
        let data;
        try {
            data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/CategoryServlet?action=getCategories`);
        } catch (error) {
            if (error.message && error.message.includes('404')) {
                console.log('CategoryServlet not found, trying ProductServlet as fallback...');
                data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/ProductServlet?action=getCategories`);
            } else {
                throw error;
            }
        }
        const categories = KaruruUtils.extractData(data, 'categories') || [];
        
        const grid = document.getElementById('categoriesGrid');
        if (!grid) return;
        
        if (categories && categories.length > 0) {
            grid.innerHTML = categories.map(category => {
                const slug = category.slug || '';
                
                // Priority: 1. image_url from database, 2. slug-based path from /img/categories/, 3. icon_url, 4. default
                let categoryImage = '';
                
                // First, try image_url from database
                if (category.image_url && category.image_url.trim() !== '' && category.image_url !== 'null') {
                    categoryImage = category.image_url;
                } 
                // Second, try slug-based path from /img/categories/
                else if (slug && slug.trim() !== '') {
                    categoryImage = `/img/categories/${slug}.png`;
                }
                // Third, try icon_url
                else if (category.icon_url && category.icon_url.trim() !== '' && category.icon_url !== 'null') {
                    categoryImage = category.icon_url;
                }
                
                // Resolve full URL using utility function with default fallback
                const resolvedImageUrl = categoryImage ? 
                    KaruruUtils.resolveImageUrl(categoryImage, '/img/default-category.png') : 
                    KaruruUtils.resolveImageUrl('', '/img/default-category.png');
                
                return `
                    <div class="col-6 col-md-4 col-lg-3 col-xl-2">
                        <a href="${window.CONTEXT_PATH}/products.jsp?category=${category.category_id}" 
                           class="category-card">
                            <div class="category-card-icon">
                                <img src="${resolvedImageUrl}" 
                                     alt="${escapeHtml(category.category_name || 'Category')}"
                                     data-slug="${slug}"
                                     data-category-id="${category.category_id}"
                                     onerror="handleCategoryImageError(this, '${slug}', '${category.category_id}')">
                            </div>
                            <div class="category-card-content">
                                <h6 class="category-card-name">${escapeHtml(category.category_name || 'カテゴリー')}</h6>
                                ${category.product_count ? `
                                    <small class="category-card-count">${category.product_count}件</small>
                                ` : ''}
                            </div>
                        </a>
                    </div>
                `;
            }).join('');
        } else {
            grid.innerHTML = `
                <div class="col-12">
                    <div class="text-center py-5 text-muted">
                        <i class="bi bi-tags" style="font-size: 4rem; opacity: 0.5;"></i>
                        <p class="mt-3 mb-0">カテゴリーがありません</p>
                    </div>
                </div>
            `;
        }
    } catch (error) {
        console.error('Error loading categories:', error);
        const grid = document.getElementById('categoriesGrid');
        if (grid) {
            grid.innerHTML = `
                <div class="col-12">
                    <div class="text-center py-5 text-danger">
                        <i class="bi bi-exclamation-triangle" style="font-size: 3rem;"></i>
                        <p class="mt-3 mb-0">カテゴリーの読み込みに失敗しました</p>
                    </div>
                </div>
            `;
        }
    }
}

async function loadFeaturedProducts() {
    try {
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/ProductServlet?action=getFeatured&limit=8`);
        const products = KaruruUtils.extractData(data, 'products') || [];
        renderProducts(products, 'featuredProducts');
    } catch (error) {
        console.error('Error loading featured products:', error);
        showError('featuredProducts', '商品の読み込みに失敗しました');
    }
}

async function loadRecentProducts() {
    try {
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/ProductServlet?action=getAvailableProducts&limit=8&sort=newest`);
        const products = KaruruUtils.extractData(data, 'products') || (Array.isArray(data) ? data : []);
        renderProducts(products, 'recentProducts');
    } catch (error) {
        console.error('Error loading recent products:', error);
        showError('recentProducts', '商品の読み込みに失敗しました');
    }
}

async function loadPopularProducts() {
    try {
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/ProductServlet?action=getPopular&limit=8`);
        const products = KaruruUtils.extractData(data, 'products') || (Array.isArray(data) ? data : []);
        renderProducts(products, 'popularProducts');
    } catch (error) {
        console.error('Error loading popular products:', error);
        showError('popularProducts', '商品の読み込みに失敗しました');
    }
}

function renderProducts(products, containerId) {
    const container = document.getElementById(containerId);
    if (!container) return;
    
    if (products && products.length > 0) {
        container.innerHTML = products.map(product => {
            const images = product.images || (product.image_url ? [product.image_url] : []);
            const firstImage = images.length > 0 ? images[0] : '';
            const fullImageUrl = KaruruUtils.resolveProductImageUrl(firstImage);
            
            return `
                <div class="col-6 col-md-4 col-lg-3 col-xl-2 mb-4">
                    <a href="${window.CONTEXT_PATH}/product-detail.jsp?id=${product.product_id}" 
                       class="product-card-link text-decoration-none">
                        <div class="card product-card-modern h-100">
                            <div class="product-image-wrapper">
                                <div class="product-image-carousel">
                                    ${images.slice(0, 4).map((img, idx) => {
                                        const imgUrl = KaruruUtils.resolveProductImageUrl(img);
                                        return `
                                            <img src="${imgUrl}" 
                                                 class="product-image ${idx === 0 ? 'active' : ''}" 
                                                 alt="${escapeHtml(product.product_name || 'Product')}"
                                                 loading="lazy"
                                                 onerror="this.onerror=null; this.src='${KaruruUtils.resolveProductImageUrl('')}';">
                                        `;
                                    }).join('')}
                                    ${images.length === 0 ? `
                                        <img src="${fullImageUrl}" 
                                             class="product-image active" 
                                             alt="${escapeHtml(product.product_name || 'Product')}"
                                             loading="lazy"
                                             onerror="this.onerror=null; this.src='${KaruruUtils.resolveProductImageUrl('')}';">
                                    ` : ''}
                                </div>
                                ${images.length > 1 ? `
                                    <div class="product-image-indicators">
                                        ${images.slice(0, 4).map((_, idx) => `
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
                                <div class="mb-2">
                                    ${product.is_rental && (product.rental_price_daily || product.rental_price_weekly || product.rental_price_monthly) ? `
                                        <div class="mb-1">
                                            <span class="text-primary fw-bold fs-5">
                                                ${product.rental_price_daily ? `${formatPrice(product.rental_price_daily)} /日` : 
                                                  product.rental_price_weekly ? `${formatPrice(product.rental_price_weekly)} /週` : 
                                                  product.rental_price_monthly ? `${formatPrice(product.rental_price_monthly)} /月` : ''}
                                            </span>
                                        </div>
                                        ${product.price && product.price > 0 ? `
                                            <small class="text-muted d-block">
                                                購入: ${formatPrice(product.price)}
                                            </small>
                                        ` : ''}
                                    ` : `
                                        <div>
                                            <span class="text-primary fw-bold fs-5">${formatPrice(product.price || 0)}</span>
                                        </div>
                                        ${(product.rental_price_daily || product.rental_price_weekly || product.rental_price_monthly) ? `
                                            <div class="d-flex flex-wrap gap-1 mt-1">
                                                ${product.rental_price_daily ? `
                                                    <small class="text-muted">レンタル: ${formatPrice(product.rental_price_daily)}/日</small>
                                                ` : ''}
                                                ${product.rental_price_weekly ? `
                                                    <small class="text-muted">レンタル: ${formatPrice(product.rental_price_weekly)}/週</small>
                                                ` : ''}
                                                ${product.rental_price_monthly ? `
                                                    <small class="text-muted">レンタル: ${formatPrice(product.rental_price_monthly)}/月</small>
                                                ` : ''}
                                            </div>
                                        ` : ''}
                                    `}
                                </div>
                                ${product.seller_name ? `
                                    <small class="d-block">
                                        <i class="bi bi-person me-1"></i>${escapeHtml(product.seller_name)}
                                    </small>
                                ` : ''}
                            </div>
                        </div>
                    </a>
                </div>
            `;
        }).join('');
        
        // Setup image carousel for all product cards after a short delay to ensure DOM is ready
        setTimeout(() => {
            container.querySelectorAll('.product-image-carousel').forEach(carousel => {
                setupProductImageCarousel(carousel);
            });
        }, 100);
    } else {
        container.innerHTML = `
            <div class="col-12">
                <div class="text-center py-5 text-muted">
                    <i class="bi bi-box-seam" style="font-size: 4rem; opacity: 0.5;"></i>
                    <p class="mt-3 mb-0">商品がありません</p>
                </div>
            </div>
        `;
    }
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

function formatPrice(amount) {
    return KaruruUtils.formatPrice(amount);
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Handle category image error - use default category image
function handleCategoryImageError(img, slug, categoryId) {
    // Prevent infinite loop
    if (img.dataset.errorHandled === 'true') {
        // Use default category image
        const defaultImageUrl = KaruruUtils.resolveImageUrl('', '/img/default-category.png');
        img.src = defaultImageUrl;
        img.dataset.errorHandled = 'false';
        img.onerror = function() {
            // If default also fails, hide image
            this.style.display = 'none';
        };
        return;
    }
    
    img.dataset.errorHandled = 'true';
    
    // Try alternative image formats/extensions based on slug
    if (slug && slug.trim() !== '') {
        const alternatives = [
            `/img/categories/${slug}.jpg`,
            `/img/categories/${slug}.jpeg`,
            `/img/categories/${slug.toLowerCase()}.png`
        ];
        
        const contextPath = window.CONTEXT_PATH || '';
        const currentSrc = img.src;
        const relativePath = currentSrc.replace(contextPath, '');
        
        // Find which alternative we're currently trying
        let currentIndex = -1;
        for (let i = 0; i < alternatives.length; i++) {
            const altPath = alternatives[i].replace('/img/categories/', '');
            if (relativePath.includes(altPath)) {
                currentIndex = i;
                break;
            }
        }
        
        // Try next alternative
        if (currentIndex >= 0 && currentIndex < alternatives.length - 1) {
            const nextPath = alternatives[currentIndex + 1];
            const fullPath = KaruruUtils.resolveImageUrl(nextPath, '/img/default-category.png');
            img.src = fullPath;
            img.dataset.errorHandled = 'false';
            return;
        }
    }
    
    // If all alternatives failed, use default category image
    const defaultImageUrl = KaruruUtils.resolveImageUrl('', '/img/default-category.png');
    img.src = defaultImageUrl;
    img.dataset.errorHandled = 'false';
    
    // If default image also fails, hide image
    img.onerror = function() {
        this.style.display = 'none';
    };
}

function showError(containerId, message) {
    const container = document.getElementById(containerId);
    if (container) {
        container.innerHTML = `
            <div class="col-12">
                <div class="text-center py-5 text-danger">
                    <i class="bi bi-exclamation-triangle" style="font-size: 3rem;"></i>
                    <p class="mt-3 mb-0">${escapeHtml(message)}</p>
                </div>
            </div>
        `;
    }
}
