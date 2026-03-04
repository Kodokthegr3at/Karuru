// Categories page JavaScript

document.addEventListener('DOMContentLoaded', function() {
    loadCategories();
});

async function loadCategories() {
    try {
        // Try CategoryServlet first, fallback to ProductServlet if not available
        let response = await fetch(`${window.CONTEXT_PATH}/CategoryServlet?action=getCategories`);
        
        // If CategoryServlet returns 404, try ProductServlet as fallback
        if (!response.ok && response.status === 404) {
            console.log('CategoryServlet not found, trying ProductServlet as fallback...');
            response = await fetch(`${window.CONTEXT_PATH}/ProductServlet?action=getCategories`);
        }
        
        if (!response.ok) {
            throw new Error('Failed to load categories');
        }
        
        const contentType = response.headers.get('content-type');
        let categories = [];
        
        if (contentType && contentType.includes('application/json')) {
            const data = await response.json();
            categories = Array.isArray(data) ? data : (data.categories || data.data || []);
        } else {
            const text = await response.text();
            try {
                categories = JSON.parse(text);
                if (!Array.isArray(categories)) {
                    categories = categories.categories || categories.data || [];
                }
            } catch (e) {
                console.error('Failed to parse categories response:', e);
            }
        }
        
        console.log('Categories loaded:', categories);
        renderCategories(categories);
    } catch (error) {
        console.error('Error loading categories:', error);
        const grid = document.getElementById('categoriesGrid');
        if (grid) {
            grid.innerHTML = '<div class="col-12"><p class="text-center text-muted">カテゴリーの読み込みに失敗しました</p></div>';
        }
    }
}

function renderCategories(categories) {
    const grid = document.getElementById('categoriesGrid');
    if (!grid) return;
    
    if (!categories || categories.length === 0) {
        grid.innerHTML = `
            <div class="col-12">
                <div class="text-center py-5 text-muted">
                    <i class="bi bi-tags" style="font-size: 4rem; opacity: 0.5;"></i>
                    <p class="mt-3 mb-0">カテゴリーがありません</p>
                </div>
            </div>
        `;
        return;
    }
    
    grid.innerHTML = categories.map(category => {
        const categoryUrl = `${window.CONTEXT_PATH}/products.jsp?category=${category.category_id}`;
        const slug = category.slug || '';
        
        // Priority: 1. image_url from database, 2. slug-based path from /img/categories/, 3. icon_url, 4. default
        let categoryImage = '';
        
        // First, try image_url from database
        if (category.image_url && category.image_url.trim() !== '' && category.image_url !== 'null') {
            categoryImage = category.image_url;
            console.log(`Category ${category.category_name}: Using image_url from database: ${categoryImage}`);
        } 
        // Second, try slug-based path from /img/categories/
        else if (slug && slug.trim() !== '') {
            // Use slug to build path: /img/categories/{slug}.png
            categoryImage = `/img/categories/${slug}.png`;
            console.log(`Category ${category.category_name}: Using slug-based path: ${categoryImage}`);
        }
        // Third, try icon_url
        else if (category.icon_url && category.icon_url.trim() !== '' && category.icon_url !== 'null') {
            categoryImage = category.icon_url;
            console.log(`Category ${category.category_name}: Using icon_url: ${categoryImage}`);
        }
        
        // Resolve full URL using utility function with default fallback
        const resolvedImageUrl = categoryImage ? 
            KaruruUtils.resolveImageUrl(categoryImage, '/img/default-category.png') : 
            KaruruUtils.resolveImageUrl('', '/img/default-category.png');
        const defaultImageUrl = KaruruUtils.resolveImageUrl('', '/img/default-category.png');
        
        return `
            <div class="col-6 col-md-4 col-lg-3 mb-4">
                <a href="${categoryUrl}" class="text-decoration-none product-card-link">
                    <div class="card card-light h-100 product-card-modern" style="transition: all 0.3s ease;">
                        <div class="product-image-wrapper" style="height: 200px; overflow: hidden; position: relative;">
                            <img src="${resolvedImageUrl}" 
                                 class="product-image active" 
                                 alt="${escapeHtml(category.category_name || 'カテゴリー')}"
                                 style="object-fit: cover; width: 100%; height: 100%;"
                                 data-slug="${slug}"
                                 data-category-id="${category.category_id}"
                                 data-image-url="${category.image_url || ''}"
                                 onerror="handleCategoryImageError(this, '${slug}', '${category.category_id}')">
                            <div class="category-image-fallback d-flex align-items-center justify-content-center h-100" 
                                 style="display: none; position: absolute; top: 0; left: 0; width: 100%; height: 100%; background: linear-gradient(135deg, rgba(30, 136, 229, 0.1) 0%, rgba(30, 136, 229, 0.05) 100%);">
                                <i class="bi bi-tag-fill text-primary" style="font-size: 4rem;"></i>
                            </div>
                        </div>
                        <div class="card-body text-center p-4">
                            <h5 class="card-title mb-2 fw-bold">${escapeHtml(category.category_name || 'カテゴリー')}</h5>
                            ${category.description ? `
                                <p class="text-muted small mb-3" style="min-height: 40px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;">
                                    ${escapeHtml(category.description)}
                                </p>
                            ` : ''}
                            <span class="badge bg-primary px-3 py-2">
                                <i class="bi bi-arrow-right me-1"></i>商品を見る
                            </span>
                        </div>
                    </div>
                </a>
            </div>
        `;
    }).join('');
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Handle category image error - try alternative paths, then use default
function handleCategoryImageError(img, slug, categoryId) {
    // Prevent infinite loop
    if (img.dataset.errorHandled === 'true') {
        console.log('Image error already handled, using default category image');
        // Use default category image
        const defaultImageUrl = KaruruUtils.resolveImageUrl('', '/img/default-category.png');
        img.src = defaultImageUrl;
        img.dataset.errorHandled = 'false'; // Reset to allow default image to load
        img.onerror = function() {
            // If default also fails, show fallback icon
            this.style.display = 'none';
            const fallback = this.parentElement.querySelector('.category-image-fallback');
            if (fallback) fallback.style.display = 'flex';
        };
        return;
    }
    
    img.dataset.errorHandled = 'true';
    const contextPath = window.CONTEXT_PATH || '';
    const currentSrc = img.src;
    const originalImageUrl = img.dataset.imageUrl || '';
    
    console.log('Category image error:', {
        currentSrc,
        slug,
        categoryId,
        originalImageUrl
    });
    
    // Try alternative image formats/extensions based on slug
    if (slug && slug.trim() !== '') {
        const alternatives = [
            `/img/categories/${slug}.jpg`,
            `/img/categories/${slug}.jpeg`,
            `/img/categories/${slug.toLowerCase()}.png`,
            `/img/categories/${slug.replace(/-/g, '_')}.png`,
            `/img/categories/${slug.replace(/-/g, '')}.png`
        ];
        
        // Find which alternative we're currently trying
        let currentIndex = -1;
        const relativePath = currentSrc.replace(contextPath, '');
        
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
            console.log('Trying alternative path:', fullPath);
            img.src = fullPath;
            img.dataset.errorHandled = 'false'; // Reset to try next alternative
            return;
        }
    }
    
    // If all alternatives failed, use default category image
    console.log('Using default category image');
    const defaultImageUrl = KaruruUtils.resolveImageUrl('', '/img/default-category.png');
    img.src = defaultImageUrl;
    img.dataset.errorHandled = 'false'; // Reset to allow default image to load
    
    // If default image also fails, show fallback icon
    img.onerror = function() {
        console.log('Default category image also failed, showing fallback icon');
        this.style.display = 'none';
        const fallback = this.parentElement.querySelector('.category-image-fallback');
        if (fallback) {
            fallback.style.display = 'flex';
        }
    };
}

