// Products page JavaScript

let currentPage = 1;
let currentFilters = {};

document.addEventListener('DOMContentLoaded', async function() {
    // Check URL for category parameter first
    const urlParams = new URLSearchParams(window.location.search);
    const categoryParam = urlParams.get('category');
    const searchQuery = urlParams.get('query') || urlParams.get('search');
    
    // Show/hide back button based on filters (not search query)
    const backButton = document.getElementById('backButton');
    if (backButton) {
        // Show back button if category filter is applied or other filters exist
        if (categoryParam && !searchQuery) {
            backButton.classList.remove('d-none');
            backButton.style.display = '';
        } else {
            backButton.classList.add('d-none');
            backButton.style.display = 'none';
        }
    }
    
    // Show/hide category title banner based on category parameter
    const categoryTitleBanner = document.getElementById('categoryTitleBanner');
    if (categoryTitleBanner) {
        if (categoryParam) {
            categoryTitleBanner.classList.remove('d-none');
            categoryTitleBanner.style.display = '';
            // Category name will be set after categories are loaded
        } else {
            categoryTitleBanner.classList.add('d-none');
            categoryTitleBanner.style.display = 'none';
        }
    }
    
    // Load categories first, then set filter and load products
    await loadCategories();
    
    if (categoryParam) {
        // Set category filter from URL parameter
        const categoryFilter = document.getElementById('categoryFilter');
        if (categoryFilter) {
            categoryFilter.value = categoryParam;
            // Apply filter after categories are loaded
            currentFilters.categories = categoryParam;
            console.log('Category filter set from URL:', categoryParam);
        } else {
            console.warn('Category filter element not found');
        }
    }
    
    // Load products with the filter applied
    loadProducts();
    setupPriceRangeSlider();
    setupFilterToggle();
    
    // Event listeners
    const searchBtn = document.getElementById('searchBtn');
    const searchInput = document.getElementById('searchInput');
    const categoryFilter = document.getElementById('categoryFilter');
    const sortFilter = document.getElementById('sortFilter');
    const applyFiltersBtn = document.getElementById('applyFilters');
    const resetFiltersBtn = document.getElementById('resetFilters');
    const applyFiltersBtnMobile = document.getElementById('applyFiltersMobile');
    const resetFiltersBtnMobile = document.getElementById('resetFiltersMobile');
    
    if (searchBtn) {
        searchBtn.addEventListener('click', handleSearch);
    }
    if (searchInput) {
        searchInput.addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                hideSuggestions();
                handleSearch();
            }
        });
        
        // Autocomplete suggestions after 2 characters
        let suggestionTimeout;
        searchInput.addEventListener('input', function(e) {
            const query = this.value.trim();
            
            clearTimeout(suggestionTimeout);
            
            if (query.length >= 2) {
                suggestionTimeout = setTimeout(() => {
                    loadSearchSuggestions(query);
                }, 300); // Debounce 300ms
            } else {
                hideSuggestions();
            }
        });
        
        // No need for search type change listener anymore
        
        // Hide suggestions when clicking outside (but not navbar dropdowns)
        document.addEventListener('click', function(e) {
            const suggestions = document.getElementById('searchSuggestions');
            const inputGroup = searchInput.closest('.input-group');
            
            // Don't hide if clicking on navbar dropdown
            const isNavbarDropdown = e.target.closest('.navbar .dropdown-menu') || 
                                    e.target.closest('.navbar .dropdown-toggle');
            
            if (suggestions && inputGroup && !inputGroup.contains(e.target) && !isNavbarDropdown) {
                hideSuggestions();
            }
        });
        
        // Handle arrow keys navigation
        searchInput.addEventListener('keydown', function(e) {
            const suggestions = document.getElementById('searchSuggestions');
            if (!suggestions || suggestions.style.display === 'none') return;
            
            const items = suggestions.querySelectorAll('.suggestion-item');
            const activeItem = suggestions.querySelector('.suggestion-item.active');
            let currentIndex = -1;
            
            if (activeItem) {
                currentIndex = Array.from(items).indexOf(activeItem);
            }
            
            if (e.key === 'ArrowDown') {
                e.preventDefault();
                if (currentIndex < items.length - 1) {
                    if (activeItem) activeItem.classList.remove('active');
                    items[currentIndex + 1].classList.add('active');
                    items[currentIndex + 1].scrollIntoView({ block: 'nearest' });
                }
            } else if (e.key === 'ArrowUp') {
                e.preventDefault();
                if (currentIndex > 0) {
                    if (activeItem) activeItem.classList.remove('active');
                    items[currentIndex - 1].classList.add('active');
                    items[currentIndex - 1].scrollIntoView({ block: 'nearest' });
                } else if (currentIndex === 0) {
                    if (activeItem) activeItem.classList.remove('active');
                }
            } else if (e.key === 'Enter' && activeItem) {
                e.preventDefault();
                activeItem.click();
            } else if (e.key === 'Escape') {
                hideSuggestions();
            }
        });
    }
    if (categoryFilter) {
        categoryFilter.addEventListener('change', function() {
            // Update category title banner when category changes
            const categoryTitleBanner = document.getElementById('categoryTitleBanner');
            const categoryTitleText = document.getElementById('categoryTitleText');
            if (categoryTitleBanner && categoryTitleText) {
                const selectedOption = this.options[this.selectedIndex];
                if (this.value && selectedOption && selectedOption.textContent) {
                    categoryTitleText.textContent = selectedOption.textContent;
                    // Show banner if category is selected and no search query
                    const searchInput = document.getElementById('searchInput');
                    const query = searchInput ? searchInput.value.trim() : '';
                    if (!query) {
                        categoryTitleBanner.classList.remove('d-none');
                        categoryTitleBanner.style.display = '';
                    }
                } else {
                    // Hide banner if no category selected
                    categoryTitleBanner.classList.add('d-none');
                    categoryTitleBanner.style.display = 'none';
                }
            }
            applyFilters();
        });
    }
    if (sortFilter) {
        sortFilter.addEventListener('change', applyFilters);
    }
    if (applyFiltersBtn) {
        applyFiltersBtn.addEventListener('click', applyFilters);
    }
    if (resetFiltersBtn) {
        resetFiltersBtn.addEventListener('click', resetFilters);
    }
    if (applyFiltersBtnMobile) {
        applyFiltersBtnMobile.addEventListener('click', function() {
            // Sync mobile values to desktop before applying
            syncMobileToDesktopFilters();
            applyFilters();
            // Close mobile filter collapse
            const filterCollapse = document.getElementById('filterCollapse');
            if (filterCollapse) {
                const bsCollapse = bootstrap.Collapse.getInstance(filterCollapse);
                if (bsCollapse) {
                    bsCollapse.hide();
                }
            }
        });
    }
    if (resetFiltersBtnMobile) {
        resetFiltersBtnMobile.addEventListener('click', function() {
            resetFilters();
            // Close mobile filter collapse
            const filterCollapse = document.getElementById('filterCollapse');
            if (filterCollapse) {
                const bsCollapse = bootstrap.Collapse.getInstance(filterCollapse);
                if (bsCollapse) {
                    bsCollapse.hide();
                }
            }
        });
    }
    
    // Sync mobile and desktop filters
    syncDesktopToMobileFilters();
    
    // Add listeners to sync when desktop filters change
    const desktopFilters = ['minPrice', 'maxPrice', 'conditionNew', 'conditionLikeNew', 'conditionGood', 'conditionFair', 'rentalFilter'];
    desktopFilters.forEach(id => {
        const element = document.getElementById(id);
        if (element) {
            element.addEventListener('change', syncDesktopToMobileFilters);
            element.addEventListener('input', syncDesktopToMobileFilters);
        }
    });
});

// Sync desktop filters to mobile
function syncDesktopToMobileFilters() {
    const desktopToMobile = {
        'minPrice': 'minPriceMobile',
        'maxPrice': 'maxPriceMobile',
        'minPriceDisplay': 'minPriceDisplayMobile',
        'maxPriceDisplay': 'maxPriceDisplayMobile',
        'minPriceValue': 'minPriceValueMobile',
        'maxPriceValue': 'maxPriceValueMobile',
        'conditionNew': 'conditionNewMobile',
        'conditionLikeNew': 'conditionLikeNewMobile',
        'conditionGood': 'conditionGoodMobile',
        'conditionFair': 'conditionFairMobile',
        'rentalFilter': 'rentalFilterMobile'
    };
    
    Object.keys(desktopToMobile).forEach(desktopId => {
        const desktopEl = document.getElementById(desktopId);
        const mobileEl = document.getElementById(desktopToMobile[desktopId]);
        if (desktopEl && mobileEl) {
            if (desktopEl.type === 'checkbox') {
                mobileEl.checked = desktopEl.checked;
            } else if (desktopEl.type === 'range') {
                mobileEl.value = desktopEl.value;
            } else {
                mobileEl.textContent = desktopEl.textContent;
            }
        }
    });
}

// Sync mobile filters to desktop
function syncMobileToDesktopFilters() {
    const mobileToDesktop = {
        'minPriceMobile': 'minPrice',
        'maxPriceMobile': 'maxPrice',
        'conditionNewMobile': 'conditionNew',
        'conditionLikeNewMobile': 'conditionLikeNew',
        'conditionGoodMobile': 'conditionGood',
        'conditionFairMobile': 'conditionFair',
        'rentalFilterMobile': 'rentalFilter'
    };
    
    Object.keys(mobileToDesktop).forEach(mobileId => {
        const mobileEl = document.getElementById(mobileId);
        const desktopEl = document.getElementById(mobileToDesktop[mobileId]);
        if (mobileEl && desktopEl) {
            if (mobileEl.type === 'checkbox') {
                desktopEl.checked = mobileEl.checked;
            } else if (mobileEl.type === 'range') {
                desktopEl.value = mobileEl.value;
                // Trigger update for price sliders
                if (desktopEl.id === 'minPrice' || desktopEl.id === 'maxPrice') {
                    desktopEl.dispatchEvent(new Event('input'));
                }
            }
        }
    });
}

function setupPriceRangeSlider() {
    const minPriceSlider = document.getElementById('minPrice');
    const maxPriceSlider = document.getElementById('maxPrice');
    const minPriceDisplay = document.getElementById('minPriceDisplay');
    const maxPriceDisplay = document.getElementById('maxPriceDisplay');
    const minPriceValue = document.getElementById('minPriceValue');
    const maxPriceValue = document.getElementById('maxPriceValue');
    
    // Also setup mobile sliders
    const minPriceSliderMobile = document.getElementById('minPriceMobile');
    const maxPriceSliderMobile = document.getElementById('maxPriceMobile');
    const minPriceDisplayMobile = document.getElementById('minPriceDisplayMobile');
    const maxPriceDisplayMobile = document.getElementById('maxPriceDisplayMobile');
    const minPriceValueMobile = document.getElementById('minPriceValueMobile');
    const maxPriceValueMobile = document.getElementById('maxPriceValueMobile');
    
    if (!minPriceSlider || !maxPriceSlider) return;
    
    // Minimum gap between sliders (5% of total range = 50,000)
    const MIN_GAP = 50000;
    const MAX_VALUE = parseInt(minPriceSlider.max);
    
    function formatPrice(price) {
        return '¥' + price.toLocaleString('ja-JP');
    }
    
    function updatePriceDisplay() {
        let minVal = parseInt(minPriceSlider.value);
        let maxVal = parseInt(maxPriceSlider.value);
        
        // Ensure minimum gap between sliders
        if (maxVal - minVal < MIN_GAP) {
            // If min slider is being moved
            if (document.activeElement === minPriceSlider) {
                maxVal = Math.min(minVal + MIN_GAP, MAX_VALUE);
                maxPriceSlider.value = maxVal;
            }
            // If max slider is being moved
            else if (document.activeElement === maxPriceSlider) {
                minVal = Math.max(maxVal - MIN_GAP, 0);
                minPriceSlider.value = minVal;
            }
            // Default: adjust max to maintain gap
            else {
                maxVal = Math.min(minVal + MIN_GAP, MAX_VALUE);
                maxPriceSlider.value = maxVal;
            }
        }
        
        // Ensure min doesn't exceed max (fallback)
        if (minVal > maxVal) {
            minVal = maxVal - MIN_GAP;
            if (minVal < 0) minVal = 0;
            minPriceSlider.value = minVal;
        }
        
        // Ensure max doesn't go below min (fallback)
        if (maxVal < minVal) {
            maxVal = minVal + MIN_GAP;
            if (maxVal > MAX_VALUE) maxVal = MAX_VALUE;
            maxPriceSlider.value = maxVal;
        }
        
        // Update display values (desktop) - ensure white color
        if (minPriceDisplay) {
            minPriceDisplay.textContent = formatPrice(minVal);
            minPriceDisplay.classList.remove('text-muted');
            minPriceDisplay.classList.remove('text-muted');
        }
        if (maxPriceDisplay) {
            maxPriceDisplay.textContent = formatPrice(maxVal);
            maxPriceDisplay.classList.remove('text-muted');
            maxPriceDisplay.classList.remove('text-muted');
        }
        if (minPriceValue) {
            minPriceValue.textContent = formatPrice(minVal);
            minPriceValue.classList.remove('text-muted');
            minPriceValue.classList.add('fw-bold');
        }
        if (maxPriceValue) {
            maxPriceValue.textContent = formatPrice(maxVal);
            maxPriceValue.classList.remove('text-muted');
            maxPriceValue.classList.add('fw-bold');
        }
        
        // Update mobile display values
        if (minPriceDisplayMobile) {
            minPriceDisplayMobile.textContent = formatPrice(minVal);
            minPriceDisplayMobile.classList.remove('text-muted');
        }
        if (maxPriceDisplayMobile) {
            maxPriceDisplayMobile.textContent = formatPrice(maxVal);
            maxPriceDisplayMobile.classList.remove('text-muted');
        }
        if (minPriceValueMobile) {
            minPriceValueMobile.textContent = formatPrice(minVal);
            minPriceValueMobile.classList.remove('text-muted');
            minPriceValueMobile.classList.add('fw-bold');
        }
        if (maxPriceValueMobile) {
            maxPriceValueMobile.textContent = formatPrice(maxVal);
            maxPriceValueMobile.classList.remove('text-muted');
            maxPriceValueMobile.classList.add('fw-bold');
        }
        
        // Sync to mobile sliders
        if (minPriceSliderMobile) {
            minPriceSliderMobile.value = minVal;
        }
        if (maxPriceSliderMobile) {
            maxPriceSliderMobile.value = maxVal;
        }
        
        // Update active range track
        updateRangeTrack();
    }
    
    function updateRangeTrack() {
        const minVal = parseInt(minPriceSlider.value);
        const maxVal = parseInt(maxPriceSlider.value);
        const minPercent = (minVal / 1000000) * 100;
        const maxPercent = (maxVal / 1000000) * 100;
        
        const wrapper = minPriceSlider.closest('.price-range-wrapper');
        if (wrapper) {
            wrapper.style.setProperty('--range-left', minPercent + '%');
            wrapper.style.setProperty('--range-right', (100 - maxPercent) + '%');
        }
    }
    
    // Initialize display
    updatePriceDisplay();
    
    // Add event listeners (desktop)
    minPriceSlider.addEventListener('input', updatePriceDisplay);
    maxPriceSlider.addEventListener('input', updatePriceDisplay);
    
    // Add event listeners (mobile) - sync to desktop
    if (minPriceSliderMobile) {
        minPriceSliderMobile.addEventListener('input', function() {
            minPriceSlider.value = this.value;
            updatePriceDisplay();
        });
    }
    if (maxPriceSliderMobile) {
        maxPriceSliderMobile.addEventListener('input', function() {
            maxPriceSlider.value = this.value;
            updatePriceDisplay();
        });
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
        
        const select = document.getElementById('categoryFilter');
        if (!select) {
            console.warn('Category filter select not found');
            return;
        }
        
        // Clear existing options (except the default one)
        while (select.children.length > 1) {
            select.removeChild(select.lastChild);
        }
        
        if (categories && categories.length > 0) {
            categories.forEach(category => {
                const option = document.createElement('option');
                option.value = category.category_id;
                option.textContent = category.category_name || 'カテゴリー';
                select.appendChild(option);
            });
        }
        
        console.log('Categories loaded:', categories.length);
        return categories;
    } catch (error) {
        console.error('Error loading categories:', error);
        return [];
    }
}

async function loadProducts(page = 1) {
    // Show back button when filters are applied (not just when no search)
    const backButton = document.getElementById('backButton');
    if (backButton) {
        const searchInput = document.getElementById('searchInput');
        const query = searchInput ? searchInput.value.trim() : '';
        const urlParams = new URLSearchParams(window.location.search);
        const categoryParam = urlParams.get('category');
        const hasFilters = categoryParam || Object.keys(currentFilters).length > 0;
        
        // Show button if no search query but filters are applied
        if (!query && hasFilters) {
            backButton.classList.remove('d-none');
            backButton.style.display = '';
        } else {
            backButton.classList.add('d-none');
            backButton.style.display = 'none';
        }
    }
    
    // Show category title banner when category filter is applied
    const categoryTitleBanner = document.getElementById('categoryTitleBanner');
    if (categoryTitleBanner) {
        const urlParams = new URLSearchParams(window.location.search);
        const categoryParam = urlParams.get('category');
        const searchInput = document.getElementById('searchInput');
        const query = searchInput ? searchInput.value.trim() : '';
        if (categoryParam && !query) {
            categoryTitleBanner.classList.remove('d-none');
            categoryTitleBanner.style.display = '';
            
            // Update category title text from select dropdown
            const categoryFilter = document.getElementById('categoryFilter');
            if (categoryFilter && categoryFilter.value) {
                const selectedOption = categoryFilter.options[categoryFilter.selectedIndex];
                if (selectedOption && selectedOption.textContent) {
                    const categoryTitleText = document.getElementById('categoryTitleText');
                    if (categoryTitleText) {
                        categoryTitleText.textContent = selectedOption.textContent;
                    }
                }
            }
        } else {
            categoryTitleBanner.classList.add('d-none');
            categoryTitleBanner.style.display = 'none';
        }
    }
    
    try {
        const grid = document.getElementById('productsGrid');
        if (!grid) return;
        
        // Check if there's a search query
        const searchInput = document.getElementById('searchInput');
        const query = searchInput ? searchInput.value.trim() : '';
        
        // If there's a search query, use SearchServlet
        if (query) {
            await searchProducts(query, page);
            return;
        }
        
        KaruruUtils.showLoading(grid);
        
        const params = {
            action: 'getProducts',
            page: page,
            limit: 20
        };
        
        // Add filters from currentFilters
        if (currentFilters.categories) {
            params.categories = currentFilters.categories; // category_id
            console.log('Applying category filter:', currentFilters.categories);
        }
        if (currentFilters.sort) {
            params.sort = currentFilters.sort;
        }
        if (currentFilters.conditions) {
            params.conditions = currentFilters.conditions;
        }
        if (currentFilters.is_rental) {
            params.is_rental = currentFilters.is_rental;
        }
        // Price filter - ProductServlet expects price range format
        if (currentFilters.minPrice || currentFilters.maxPrice) {
            const min = currentFilters.minPrice || '0';
            const max = currentFilters.maxPrice || '1000000';
            // Only send price filter if not at default values
            if (min !== '0' || max !== '1000000') {
                params.price = `${min}-${max}`;
            }
        }
        
        const url = KaruruUtils.buildUrl(`${window.CONTEXT_PATH}/ProductServlet`, params);
        console.log('=== Loading Products with Filters ===');
        console.log('URL:', url);
        console.log('Params:', params);
        console.log('Current Filters:', currentFilters);
        
        const data = await KaruruUtils.apiFetch(url);
        console.log('API Response:', data);
        
        const products = KaruruUtils.extractData(data, 'products') || [];
        console.log('Extracted Products:', products);
        
        if (products && products.length > 0) {
            renderProducts(products);
            const totalPages = data.totalPages || data.total_pages || Math.ceil((data.total || products.length) / 20);
            renderPagination(totalPages, page);
            
            // Update results info
            updateResultsInfo(data.total || products.length, page, totalPages);
        } else {
            grid.innerHTML = `
                <div class="col-12">
                    <div class="text-center py-5 text-muted">
                        <i class="bi bi-box-seam" style="font-size: 4rem; opacity: 0.5;"></i>
                        <p class="mt-3 mb-0">商品が見つかりませんでした</p>
                    </div>
                </div>
            `;
            // Update results info for empty state
            updateResultsInfo(0, page, 0);
        }
    } catch (error) {
        console.error('Error loading products:', error);
        const grid = document.getElementById('productsGrid');
        if (grid) {
            grid.innerHTML = `
                <div class="col-12">
                    <div class="text-center py-5 text-danger">
                        <i class="bi bi-exclamation-triangle" style="font-size: 3rem;"></i>
                        <p class="mt-3 mb-0">商品の読み込みに失敗しました</p>
                    </div>
                </div>
            `;
        }
        // Update results info for error state
        updateResultsInfo(0, page, 0);
    }
}

function renderProducts(products) {
    const grid = document.getElementById('productsGrid');
    if (!grid) return;
    
    grid.innerHTML = products.map(product => {
        const images = product.images || [];
        const mainImage = product.image_url || (images.length > 0 ? images[0] : '');
        const allImages = [mainImage, ...images.filter(img => img !== mainImage)].slice(0, 4);
        const price = parseInt(product.price || 0).toLocaleString('ja-JP');
        
        const productUrl = `${window.CONTEXT_PATH}/product-detail.jsp?id=${product.product_id}`;
        
        return `
            <div class="col-6 col-md-4 col-lg-3 mb-4">
                <a href="${productUrl}" class="product-card-link text-decoration-none">
                    <div class="card product-card-modern h-100">
                        <div class="product-image-wrapper">
                            <div class="product-image-carousel" data-product-id="${product.product_id}">
                                ${allImages.map((img, idx) => {
                                    const imageUrl = KaruruUtils.resolveProductImageUrl(img);
                                    return `
                                    <img src="${imageUrl}" 
                                         alt="${escapeHtml(product.product_name || '商品')}" 
                                         class="product-image ${idx === 0 ? 'active' : ''}"
                                         loading="lazy"
                                         onerror="this.onerror=null; this.src='${KaruruUtils.resolveProductImageUrl('')}';">
                                `;
                                }).join('')}
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
                            <small class="d-block">
                                <i class="bi bi-person me-1"></i> ${escapeHtml(product.seller_name || 'ユーザー')}
                            </small>
                        </div>
                    </div>
                </a>
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

async function loadSearchSuggestions(query) {
    try {
        // Search all types simultaneously - no type parameter needed
        const url = `${window.CONTEXT_PATH}/SearchServlet?action=suggestions&query=${encodeURIComponent(query)}&limit=8`;
        const data = await KaruruUtils.apiFetch(url);
        
        const suggestions = data.suggestions || [];
        const suggestionsDiv = document.getElementById('searchSuggestions');
        if (!suggestionsDiv) return;
        
        if (suggestions.length > 0) {
            suggestionsDiv.innerHTML = suggestions.map(item => {
                const type = item.type || 'product';
                let icon = 'bi-box-seam';
                if (type === 'category') icon = 'bi-tag';
                else if (type === 'seller' || type === 'user') icon = 'bi-person';
                
                const highlight = item.highlight || item.text || item.name || '';
                const queryValue = (item.text || item.name || query).replace(/"/g, '&quot;').replace(/'/g, '&#39;');
                
                return `
                    <a href="#" class="dropdown-item suggestion-item text-dark" 
                       data-query="${queryValue}" 
                       data-type="${type}"
                       ${item.category_id ? `data-category-id="${item.category_id}"` : ''}
                       ${item.seller_id ? `data-seller-id="${item.seller_id}"` : ''}>
                        <i class="bi ${icon} me-2"></i>
                        <span>${highlight}</span>
                    </a>
                `;
            }).join('');
            
            // Add click handlers
            suggestionsDiv.querySelectorAll('.suggestion-item').forEach(item => {
                item.addEventListener('click', function(e) {
                    e.preventDefault();
                    const query = this.dataset.query;
                    const searchInput = document.getElementById('searchInput');
                    if (searchInput) {
                        searchInput.value = query;
                    }
                    
                    hideSuggestions();
                    handleSearch();
                });
            });
            
            showSuggestions();
        } else {
            hideSuggestions();
        }
    } catch (error) {
        console.error('Error loading suggestions:', error);
        hideSuggestions();
    }
}

function showSuggestions() {
    const suggestions = document.getElementById('searchSuggestions');
    const searchInput = document.getElementById('searchInput');
    if (suggestions && searchInput) {
        suggestions.style.display = 'block';
        suggestions.style.zIndex = '10000';
        suggestions.style.position = 'absolute';
        
        // Position dropdown below input
        const inputGroup = searchInput.closest('.input-group');
        const searchContainer = searchInput.closest('.col-12.col-md-6');
        const cardBody = searchInput.closest('.card-body');
        const card = searchInput.closest('.card');
        
        // Ensure all parent containers have high z-index
        if (inputGroup) {
            if (getComputedStyle(inputGroup).position === 'static') {
                inputGroup.style.position = 'relative';
            }
            inputGroup.style.zIndex = '10000';
        }
        if (searchContainer) {
            searchContainer.style.zIndex = '10000';
        }
        if (cardBody) {
            cardBody.style.zIndex = '10000';
            cardBody.style.position = 'relative';
        }
        if (card) {
            card.style.zIndex = '10000';
            card.style.position = 'relative';
            card.style.isolation = 'isolate';
        }
        
        // Ensure filter collapse has low z-index
        const filterCollapse = document.getElementById('filterCollapse');
        if (filterCollapse) {
            filterCollapse.style.zIndex = '1';
            const filterCard = filterCollapse.querySelector('.card');
            if (filterCard) {
                filterCard.style.zIndex = '1';
            }
        }
        
        suggestions.style.top = '100%';
        suggestions.style.left = '0';
        suggestions.style.right = '0';
    }
}

function hideSuggestions() {
    const suggestions = document.getElementById('searchSuggestions');
    if (suggestions) {
        suggestions.style.display = 'none';
    }
}

// Hide suggestions when clicking outside
document.addEventListener('click', function(event) {
    const searchInput = document.getElementById('searchInput');
    const searchBtn = document.getElementById('searchBtn');
    const suggestions = document.getElementById('searchSuggestions');
    const inputGroup = searchInput ? searchInput.closest('.input-group') : null;
    
    if (suggestions && searchInput && inputGroup) {
        // Check if click is outside the input group
        if (!inputGroup.contains(event.target)) {
            hideSuggestions();
        }
    }
});

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Function to view all products (clear search and show all)
function viewAllProducts() {
    resetToAllProducts();
}

// Function to reset to all products (clear all filters and search)
function resetToAllProducts() {
    // Clear search input
    const searchInput = document.getElementById('searchInput');
    if (searchInput) {
        searchInput.value = '';
    }
    
    // Clear all URL parameters
    const url = new URL(window.location);
    url.searchParams.delete('query');
    url.searchParams.delete('search');
    url.searchParams.delete('category'); // Remove category to show all products
    window.history.pushState({}, '', url);
    
    // Reset filter form fields manually (to avoid double loadProducts call from resetFilters)
    const categoryFilter = document.getElementById('categoryFilter');
    const sortFilter = document.getElementById('sortFilter');
    const minPrice = document.getElementById('minPrice');
    const maxPrice = document.getElementById('maxPrice');
    const conditionNew = document.getElementById('conditionNew');
    const conditionLikeNew = document.getElementById('conditionLikeNew');
    const conditionGood = document.getElementById('conditionGood');
    const conditionFair = document.getElementById('conditionFair');
    const rentalFilter = document.getElementById('rentalFilter');
    
    if (categoryFilter) categoryFilter.value = '';
    if (sortFilter) sortFilter.value = 'newest';
    if (minPrice) minPrice.value = '0';
    if (maxPrice) maxPrice.value = '1000000';
    if (conditionNew) conditionNew.checked = false;
    if (conditionLikeNew) conditionLikeNew.checked = false;
    if (conditionGood) conditionGood.checked = false;
    if (conditionFair) conditionFair.checked = false;
    if (rentalFilter) rentalFilter.checked = false;
    
    // Update price displays
    const minPriceDisplay = document.getElementById('minPriceDisplay');
    const maxPriceDisplay = document.getElementById('maxPriceDisplay');
    const minPriceValue = document.getElementById('minPriceValue');
    const maxPriceValue = document.getElementById('maxPriceValue');
    if (minPriceDisplay) minPriceDisplay.textContent = '¥0';
    if (maxPriceDisplay) maxPriceDisplay.textContent = '¥1,000,000';
    if (minPriceValue) minPriceValue.textContent = '¥0';
    if (maxPriceValue) maxPriceValue.textContent = '¥1,000,000';
    
    // Reset filters object
    currentFilters = {};
    currentPage = 1;
    
    // Hide back button (since we're now showing all products)
    const backButton = document.getElementById('backButton');
    if (backButton) {
        backButton.classList.add('d-none');
        backButton.style.display = 'none';
    }
    
    // Hide category title banner
    const categoryTitleBanner = document.getElementById('categoryTitleBanner');
    if (categoryTitleBanner) {
        categoryTitleBanner.classList.add('d-none');
        categoryTitleBanner.style.display = 'none';
    }
    
    // Load all products without filters
    loadProducts(currentPage);
}

// Handle category image error - try alternative paths, then use default
function handleCategoryImageError(img, slug, categoryId) {
    // Prevent infinite loop
    if (img.dataset.errorHandled === 'true') {
        // Use default category image
        const defaultImageUrl = KaruruUtils.resolveImageUrl('', '/img/default-category.png');
        img.src = defaultImageUrl;
        img.dataset.errorHandled = 'false';
        img.onerror = function() {
            // If default also fails, show fallback icon
            this.style.display = 'none';
            const fallback = this.parentElement.querySelector('.category-image-fallback');
            if (fallback) fallback.style.display = 'flex';
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
    
    // If default image also fails, show fallback icon
    img.onerror = function() {
        this.style.display = 'none';
        const fallback = this.parentElement.querySelector('.category-image-fallback');
        if (fallback) {
            fallback.style.display = 'flex';
        }
    };
}

function updateResultsInfo(total, currentPage, totalPages) {
    const resultsInfo = document.getElementById('resultsInfo');
    if (resultsInfo) {
        const start = (currentPage - 1) * 20 + 1;
        const end = Math.min(currentPage * 20, total);
        resultsInfo.innerHTML = `
            <span class="fw-semibold">${total.toLocaleString('ja-JP')}</span>
            <span class="text-muted">件の商品が見つかりました</span>
            ${totalPages > 1 ? `
                <span class="text-muted ms-2">
                    (${start.toLocaleString('ja-JP')} - ${end.toLocaleString('ja-JP')}件を表示 / ${totalPages}ページ中${currentPage}ページ目)
                </span>
            ` : ''}
        `;
    }
}

async function handleSearch() {
    const searchInput = document.getElementById('searchInput');
    if (!searchInput) return;
    
    const query = searchInput.value.trim();
    
    // Update back button visibility - show when filters are applied (not just when no query)
    const backButton = document.getElementById('backButton');
    if (backButton) {
        const urlParams = new URLSearchParams(window.location.search);
        const categoryParam = urlParams.get('category');
        const hasFilters = categoryParam || Object.keys(currentFilters).length > 0;
        
        // Show button if no search query but filters are applied
        if (!query && hasFilters) {
            backButton.classList.remove('d-none');
            backButton.style.display = '';
        } else {
            backButton.classList.add('d-none');
            backButton.style.display = 'none';
        }
    }
    
    // Update category title banner visibility
    const categoryTitleBanner = document.getElementById('categoryTitleBanner');
    if (categoryTitleBanner) {
        const urlParams = new URLSearchParams(window.location.search);
        const categoryParam = urlParams.get('category');
        if (categoryParam && !query) {
            categoryTitleBanner.classList.remove('d-none');
            categoryTitleBanner.style.display = '';
            
            // Update category title text from select dropdown
            const categoryFilter = document.getElementById('categoryFilter');
            if (categoryFilter && categoryFilter.value) {
                const selectedOption = categoryFilter.options[categoryFilter.selectedIndex];
                if (selectedOption && selectedOption.textContent) {
                    const categoryTitleText = document.getElementById('categoryTitleText');
                    if (categoryTitleText) {
                        categoryTitleText.textContent = selectedOption.textContent;
                    }
                }
            }
        } else {
            categoryTitleBanner.classList.add('d-none');
            categoryTitleBanner.style.display = 'none';
        }
    }
    
    if (!query) {
        // If search is empty, load all products
        currentFilters = {};
        currentPage = 1;
        loadProducts(currentPage);
        return;
    }
    
    // Collapse filter when searching to prevent overlap
    const filterCollapse = document.getElementById('filterCollapse');
    if (filterCollapse && filterCollapse.classList.contains('show')) {
        const bsCollapse = bootstrap.Collapse.getInstance(filterCollapse);
        if (bsCollapse) {
            bsCollapse.hide();
        }
    }
    
    currentPage = 1;
    await searchProducts(query, currentPage);
}

async function applyFilters() {
    const searchInput = document.getElementById('searchInput');
    const query = searchInput ? searchInput.value.trim() : '';
    
    // Update back button visibility - show when filters are applied (not just when no query)
    const backButton = document.getElementById('backButton');
    if (backButton) {
        const urlParams = new URLSearchParams(window.location.search);
        const categoryParam = urlParams.get('category');
        const hasFilters = categoryParam || Object.keys(currentFilters).length > 0;
        
        // Show button if no search query but filters are applied
        if (!query && hasFilters) {
            backButton.classList.remove('d-none');
            backButton.style.display = '';
        } else {
            backButton.classList.add('d-none');
            backButton.style.display = 'none';
        }
    }
    
    // Update category title banner visibility
    const categoryTitleBanner = document.getElementById('categoryTitleBanner');
    if (categoryTitleBanner) {
        const urlParams = new URLSearchParams(window.location.search);
        const categoryParam = urlParams.get('category');
        if (categoryParam && !query) {
            categoryTitleBanner.classList.remove('d-none');
            categoryTitleBanner.style.display = '';
            
            // Update category title text from select dropdown
            const categoryFilter = document.getElementById('categoryFilter');
            if (categoryFilter && categoryFilter.value) {
                const selectedOption = categoryFilter.options[categoryFilter.selectedIndex];
                if (selectedOption && selectedOption.textContent) {
                    const categoryTitleText = document.getElementById('categoryTitleText');
                    if (categoryTitleText) {
                        categoryTitleText.textContent = selectedOption.textContent;
                    }
                }
            }
        } else {
            categoryTitleBanner.classList.add('d-none');
            categoryTitleBanner.style.display = 'none';
        }
    }
    
    // If there's a search query, use SearchServlet
    if (query) {
        currentPage = 1;
        await searchProducts(query, currentPage);
    } else {
        // Otherwise use regular ProductServlet
        const categoryFilter = document.getElementById('categoryFilter');
        const sortFilter = document.getElementById('sortFilter');
        const minPrice = document.getElementById('minPrice');
        const maxPrice = document.getElementById('maxPrice');
        const conditionNew = document.getElementById('conditionNew');
        const conditionLikeNew = document.getElementById('conditionLikeNew');
        const conditionGood = document.getElementById('conditionGood');
        const conditionFair = document.getElementById('conditionFair');
        const rentalFilter = document.getElementById('rentalFilter');
        
        currentFilters = {};
        
        console.log('=== Applying Filters ===');
        
        // Category filter - ProductServlet expects category_id, not slug
        if (categoryFilter && categoryFilter.value) {
            currentFilters.categories = categoryFilter.value; // category_id
            console.log('Category filter:', categoryFilter.value);
        }
        
        // Sort filter
        if (sortFilter && sortFilter.value) {
            currentFilters.sort = sortFilter.value;
            console.log('Sort filter:', sortFilter.value);
        }
        
        // Price range filter - get values from sliders
        const minPriceVal = minPrice ? minPrice.value : '';
        const maxPriceVal = maxPrice ? maxPrice.value : '';
        console.log('Price range:', minPriceVal, '-', maxPriceVal);
        // Only apply filter if not at default values (0 and 1000000)
        if (minPriceVal && maxPriceVal && (minPriceVal !== '0' || maxPriceVal !== '1000000')) {
            currentFilters.minPrice = minPriceVal;
            currentFilters.maxPrice = maxPriceVal;
            console.log('Price filter applied:', minPriceVal, '-', maxPriceVal);
        }
        
        // Condition filter
        const conditions = [];
        if (conditionNew && conditionNew.checked) conditions.push('new');
        if (conditionLikeNew && conditionLikeNew.checked) conditions.push('like_new');
        if (conditionGood && conditionGood.checked) conditions.push('good');
        if (conditionFair && conditionFair.checked) conditions.push('fair');
        if (conditions.length > 0) {
            currentFilters.conditions = conditions.join(',');
            console.log('Condition filter:', currentFilters.conditions);
        }
        
        // Rental filter
        if (rentalFilter && rentalFilter.checked) {
            currentFilters.is_rental = 'true';
            console.log('Rental filter: true');
        }
        
        console.log('Final currentFilters:', currentFilters);
        
        // Update back button visibility after applying filters
        const backButton = document.getElementById('backButton');
        if (backButton) {
            const urlParams = new URLSearchParams(window.location.search);
            const categoryParam = urlParams.get('category');
            const hasFilters = categoryParam || Object.keys(currentFilters).length > 0;
            
            // Show button if filters are applied
            if (hasFilters && !query) {
                backButton.classList.remove('d-none');
                backButton.style.display = '';
            } else {
                backButton.classList.add('d-none');
                backButton.style.display = 'none';
            }
        }
        
        currentPage = 1;
        loadProducts(currentPage);
    }
}

function resetFilters() {
    console.log('=== Resetting Filters ===');
    
    // Reset category filter
    const categoryFilter = document.getElementById('categoryFilter');
    if (categoryFilter) {
        categoryFilter.value = '';
    }
    
    // Reset sort filter
    const sortFilter = document.getElementById('sortFilter');
    if (sortFilter) {
        sortFilter.value = 'newest';
    }
    
    // Reset price sliders (desktop)
    const minPrice = document.getElementById('minPrice');
    const maxPrice = document.getElementById('maxPrice');
    if (minPrice) {
        minPrice.value = '0';
    }
    if (maxPrice) {
        maxPrice.value = '1000000';
    }
    
    // Update price display
    const minPriceDisplay = document.getElementById('minPriceDisplay');
    const maxPriceDisplay = document.getElementById('maxPriceDisplay');
    const minPriceValue = document.getElementById('minPriceValue');
    const maxPriceValue = document.getElementById('maxPriceValue');
    
    if (minPriceDisplay) minPriceDisplay.textContent = '¥0';
    if (maxPriceDisplay) maxPriceDisplay.textContent = '¥1,000,000';
    if (minPriceValue) minPriceValue.textContent = '¥0';
    if (maxPriceValue) maxPriceValue.textContent = '¥1,000,000';
    
    // Update range track
    const wrapper = minPrice ? minPrice.closest('.price-range-wrapper') : null;
    if (wrapper) {
        wrapper.style.setProperty('--range-left', '0%');
        wrapper.style.setProperty('--range-right', '0%');
    }
    
    // Trigger updatePriceDisplay by dispatching input events
    if (minPrice && maxPrice) {
        const inputEvent = new Event('input', { bubbles: true });
        minPrice.dispatchEvent(inputEvent);
        maxPrice.dispatchEvent(inputEvent);
    }
    
    // Reset condition checkboxes (desktop)
    const conditionNew = document.getElementById('conditionNew');
    const conditionLikeNew = document.getElementById('conditionLikeNew');
    const conditionGood = document.getElementById('conditionGood');
    const conditionFair = document.getElementById('conditionFair');
    if (conditionNew) conditionNew.checked = false;
    if (conditionLikeNew) conditionLikeNew.checked = false;
    if (conditionGood) conditionGood.checked = false;
    if (conditionFair) conditionFair.checked = false;
    
    // Reset condition checkboxes (mobile)
    const conditionNewMobile = document.getElementById('conditionNewMobile');
    const conditionLikeNewMobile = document.getElementById('conditionLikeNewMobile');
    const conditionGoodMobile = document.getElementById('conditionGoodMobile');
    const conditionFairMobile = document.getElementById('conditionFairMobile');
    if (conditionNewMobile) conditionNewMobile.checked = false;
    if (conditionLikeNewMobile) conditionLikeNewMobile.checked = false;
    if (conditionGoodMobile) conditionGoodMobile.checked = false;
    if (conditionFairMobile) conditionFairMobile.checked = false;
    
    // Reset rental filter (desktop)
    const rentalFilter = document.getElementById('rentalFilter');
    if (rentalFilter) {
        rentalFilter.checked = false;
    }
    
    // Reset rental filter (mobile)
    const rentalFilterMobile = document.getElementById('rentalFilterMobile');
    if (rentalFilterMobile) {
        rentalFilterMobile.checked = false;
    }
    
    // Reset mobile price sliders
    const minPriceMobile = document.getElementById('minPriceMobile');
    const maxPriceMobile = document.getElementById('maxPriceMobile');
    if (minPriceMobile) minPriceMobile.value = '0';
    if (maxPriceMobile) maxPriceMobile.value = '1000000';
    
    // Update mobile price displays - ensure white color
    const minPriceDisplayMobile = document.getElementById('minPriceDisplayMobile');
    const maxPriceDisplayMobile = document.getElementById('maxPriceDisplayMobile');
    const minPriceValueMobile = document.getElementById('minPriceValueMobile');
    const maxPriceValueMobile = document.getElementById('maxPriceValueMobile');
    if (minPriceDisplayMobile) {
        minPriceDisplayMobile.textContent = '¥0';
        minPriceDisplayMobile.classList.remove('text-muted');
    }
    if (maxPriceDisplayMobile) {
        maxPriceDisplayMobile.textContent = '¥1,000,000';
        maxPriceDisplayMobile.classList.remove('text-muted');
        maxPriceDisplayMobile.classList.remove('text-muted');
    }
    if (minPriceValueMobile) {
        minPriceValueMobile.textContent = '¥0';
        minPriceValueMobile.classList.remove('text-muted');
        minPriceValueMobile.classList.add('fw-bold');
    }
    if (maxPriceValueMobile) {
        maxPriceValueMobile.textContent = '¥1,000,000';
        maxPriceValueMobile.classList.remove('text-muted');
        maxPriceValueMobile.classList.add('fw-bold');
    }
    
    // Clear current filters
    currentFilters = {};
    
    // Reset search input
    const searchInput = document.getElementById('searchInput');
    if (searchInput) {
        searchInput.value = '';
    }
    
    // Hide back button after resetting filters
    const backButton = document.getElementById('backButton');
    if (backButton) {
        backButton.classList.add('d-none');
        backButton.style.display = 'none';
    }
    
    // Hide category title banner after resetting
    const categoryTitleBanner = document.getElementById('categoryTitleBanner');
    if (categoryTitleBanner) {
        categoryTitleBanner.classList.add('d-none');
        categoryTitleBanner.style.display = 'none';
    }
    
    // Remove category from URL
    const url = new URL(window.location);
    url.searchParams.delete('category');
    window.history.pushState({}, '', url);
    
    // Reload products without filters
    currentPage = 1;
    loadProducts(currentPage);
    
    console.log('✅ Filters reset');
}

async function searchProducts(query, page = 1) {
    // Hide back button when searching
    const backButton = document.getElementById('backButton');
    if (backButton) {
        backButton.classList.add('d-none');
        backButton.style.display = 'none';
    }
    
    // Hide category title banner when searching
    const categoryTitleBanner = document.getElementById('categoryTitleBanner');
    if (categoryTitleBanner) {
        categoryTitleBanner.classList.add('d-none');
        categoryTitleBanner.style.display = 'none';
    }
    
    try {
        const grid = document.getElementById('productsGrid');
        if (!grid) return;
        
        KaruruUtils.showLoading(grid);
        
        // Get all filter values
        const categoryFilter = document.getElementById('categoryFilter');
        const sortFilter = document.getElementById('sortFilter');
        const minPrice = document.getElementById('minPrice');
        const maxPrice = document.getElementById('maxPrice');
        const conditionNew = document.getElementById('conditionNew');
        const conditionLikeNew = document.getElementById('conditionLikeNew');
        const conditionGood = document.getElementById('conditionGood');
        const conditionFair = document.getElementById('conditionFair');
        const rentalFilter = document.getElementById('rentalFilter');
        
        // Build search parameters
        const params = {
            query: query,
            page: page
            // No type parameter - will search all types simultaneously
        };
        
        // Add category filter - SearchServlet now supports both category_id and category_name
        if (categoryFilter && categoryFilter.value) {
            // Use category_id directly (SearchServlet now supports it)
            params.categories = categoryFilter.value;
        }
        
        // Add price range filter - get values from sliders
        const minPriceVal = minPrice ? minPrice.value : '';
        const maxPriceVal = maxPrice ? maxPrice.value : '';
        // Only apply filter if not at default values (0 and 1000000)
        if (minPriceVal && maxPriceVal && (minPriceVal !== '0' || maxPriceVal !== '1000000')) {
            const priceRange = minPriceVal + '-' + maxPriceVal;
            params.price = priceRange;
        }
        
        // Add condition filter
        const conditions = [];
        if (conditionNew && conditionNew.checked) conditions.push('new');
        if (conditionLikeNew && conditionLikeNew.checked) conditions.push('like_new');
        if (conditionGood && conditionGood.checked) conditions.push('good');
        if (conditionFair && conditionFair.checked) conditions.push('fair');
        if (conditions.length > 0) {
            params.conditions = conditions.join(',');
        }
        
        // Add sort filter
        if (sortFilter && sortFilter.value) {
            const sortValue = sortFilter.value;
            // Map frontend sort values to SearchServlet format
            switch(sortValue) {
                case 'price_low':
                    params.sort = 'price-low';
                    break;
                case 'price_high':
                    params.sort = 'price-high';
                    break;
                case 'popular':
                    params.sort = 'popular';
                    break;
                case 'newest':
                default:
                    params.sort = 'newest';
                    break;
            }
        }
        
        // Add rental filter (if needed, SearchServlet doesn't have this yet, but we can add it)
        if (rentalFilter && rentalFilter.checked) {
            // Note: SearchServlet doesn't support rental filter yet
            // We'll filter on client side or add it to SearchServlet later
        }
        
        const url = KaruruUtils.buildUrl(`${window.CONTEXT_PATH}/SearchServlet`, params);
        console.log('Search URL:', url);
        console.log('Search params:', params);
        
        const data = await KaruruUtils.apiFetch(url);
        console.log('Search response:', data);
        
        // SearchServlet returns {products, categories, sellers, totalCount, page, hasMore}
        // Use extractData for consistency, but also support direct access
        const products = KaruruUtils.extractData(data, 'products') || data.products || [];
        const categories = KaruruUtils.extractData(data, 'categories') || data.categories || [];
        const sellers = KaruruUtils.extractData(data, 'sellers') || data.sellers || [];
        
        console.log('Products found:', products.length);
        console.log('Categories found:', categories.length);
        console.log('Sellers found:', sellers.length);
        
        // Debug: log the actual data structure
        if (categories.length > 0) {
            console.log('Sample category:', categories[0]);
        }
        if (sellers.length > 0) {
            console.log('Sample seller:', sellers[0]);
        }
        
        // Build HTML for all results
        let resultsHtml = '';
        let hasResults = false;
        
        // Render categories section
        if (categories && categories.length > 0) {
            hasResults = true;
            resultsHtml += `
                <div class="col-12 mb-4">
                    <h4 class="mb-3">
                        <i class="bi bi-tags me-2"></i>カテゴリー (${categories.length})
                    </h4>
                    <div class="row g-4">
                        ${categories.map(category => {
                            const categoryUrl = `${window.CONTEXT_PATH}/products.jsp?category=${category.category_id}`;
                            const categoryIcon = category.icon_url || '';
                            
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
                                <div class="col-6 col-md-4 col-lg-3 mb-4">
                                    <a href="${categoryUrl}" class="text-decoration-none product-card-link">
                                        <div class="card bg-black border-secondary h-100 product-card-modern" style="transition: all 0.3s ease;">
                                            <div class="product-image-wrapper" style="height: 200px; overflow: hidden; position: relative;">
                                                <img src="${resolvedImageUrl}" 
                                                     class="product-image active" 
                                                     alt="${escapeHtml(category.category_name || 'カテゴリー')}"
                                                     style="object-fit: cover; width: 100%; height: 100%;"
                                                     data-slug="${slug}"
                                                     data-category-id="${category.category_id}"
                                                     onerror="handleCategoryImageError(this, '${slug}', '${category.category_id}')">
                                                <div class="category-image-fallback d-flex align-items-center justify-content-center h-100" 
                                                     style="display: none; position: absolute; top: 0; left: 0; width: 100%; height: 100%; background: linear-gradient(135deg, rgba(13, 110, 253, 0.2) 0%, rgba(13, 110, 253, 0.1) 100%);">
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
                        }).join('')}
                    </div>
                </div>
            `;
        }
        
        // Render sellers section
        if (sellers && sellers.length > 0) {
            hasResults = true;
            resultsHtml += `
                <div class="col-12 mb-4">
                    <h4 class="mb-3">
                        <i class="bi bi-person me-2"></i>出品者 (${sellers.length})
                    </h4>
                    <div class="row g-4">
                        ${sellers.map(seller => {
                            const sellerUrl = `${window.CONTEXT_PATH}/seller.jsp?seller_id=${seller.seller_id || seller.user_id}`;
                            return `
                                <div class="col-6 col-md-4 col-lg-3 mb-4">
                                    <a href="${sellerUrl}" class="text-decoration-none product-card-link">
                                        <div class="card card-light h-100 product-card-modern" style="transition: all 0.3s ease;">
                                            <div class="card-body text-center p-5" style="background: linear-gradient(135deg, rgba(13, 110, 253, 0.1) 0%, rgba(13, 110, 253, 0.05) 100%);">
                                                <i class="bi bi-person-circle text-primary" style="font-size: 4rem;"></i>
                                            </div>
                                            <div class="card-body text-center p-4">
                                                <h5 class="card-title mb-2 fw-bold">${escapeHtml(seller.username || '出品者')}</h5>
                                                <span class="badge bg-primary px-3 py-2">
                                                    <i class="bi bi-box-seam me-1"></i>${seller.product_count || 0}商品
                                                </span>
                                            </div>
                                        </div>
                                    </a>
                                </div>
                            `;
                        }).join('')}
                    </div>
                </div>
            `;
        }
        
        // Render products section
        if (products && products.length > 0) {
            hasResults = true;
            resultsHtml += `
                <div class="col-12 mb-4">
                    <h4 class="mb-3">
                        <i class="bi bi-box-seam me-2"></i>商品 (${data.totalCount || products.length})
                    </h4>
                </div>
            `;
            
            // Map SearchServlet product format to our renderProducts format
            const mappedProducts = products.map(product => ({
                product_id: product.product_id,
                product_name: product.product_name,
                description: product.description,
                price: product.price,
                original_price: product.original_price,
                status: product.status,
                image_url: product.image_url,
                images: product.images || [],
                is_rental: product.is_rental,
                rental_price_daily: product.rental_price_daily,
                condition: product.condition,
                views_count: product.views_count,
                likes_count: product.likes_count,
                seller_id: product.seller_id,
                seller_name: product.seller_name,
                rating_avg: product.rating_avg || 0,
                rating_count: product.rating_count || 0,
                created_at: product.created_at
            }));
            
            // Render products in grid - using same layout as favorites
            resultsHtml += mappedProducts.map(product => {
                const images = product.images || (product.image_url ? [product.image_url] : []);
                const mainImage = product.image_url || (images.length > 0 ? images[0] : '');
                const allImages = [mainImage, ...images.filter(img => img !== mainImage && img)].slice(0, 4);
                
                const productUrl = `${window.CONTEXT_PATH}/product-detail.jsp?id=${product.product_id}`;
                
                return `
                    <div class="col-6 col-md-4 col-lg-3 mb-4">
                        <a href="${productUrl}" class="product-card-link text-decoration-none">
                            <div class="card product-card-modern h-100">
                                <div class="product-image-wrapper">
                                    <div class="product-image-carousel" data-product-id="${product.product_id}">
                                        ${allImages.map((img, idx) => {
                                            const imageUrl = KaruruUtils.resolveProductImageUrl(img);
                                            return `
                                                <img src="${imageUrl}" 
                                                     class="product-image ${idx === 0 ? 'active' : ''}" 
                                                     alt="${escapeHtml(product.product_name || '商品')}"
                                                     loading="lazy"
                                                     onerror="this.onerror=null; this.src='${KaruruUtils.resolveProductImageUrl('')}';">
                                            `;
                                        }).join('')}
                                        ${allImages.length === 0 ? `
                                            <img src="${KaruruUtils.resolveProductImageUrl('')}" 
                                                 class="product-image active" 
                                                 alt="${escapeHtml(product.product_name || '商品')}"
                                                 loading="lazy"
                                                 onerror="this.onerror=null; this.src='${KaruruUtils.resolveProductImageUrl('')}';">
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
                                        ${product.rental_price_daily ? `
                                            <small class="text-muted">/日 ${KaruruUtils.formatPrice(product.rental_price_daily)}</small>
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
                    </div>
                `;
            }).join('');
            
            // Setup image carousels after rendering
            setTimeout(() => {
                const grid = document.getElementById('productsGrid');
                if (grid) {
                    grid.querySelectorAll('.product-image-carousel').forEach(carousel => {
                        setupProductImageCarousel(carousel);
                    });
                }
            }, 100);
            
            const totalCount = data.totalCount || products.length;
            const totalPages = Math.ceil(totalCount / 20);
            renderPagination(totalPages, page);
            
            // Update results info
            updateResultsInfo(totalCount, page, totalPages);
        }
        
        if (hasResults) {
            grid.innerHTML = resultsHtml;
            
            // Collapse filter when search results are shown to prevent overlap
            const filterCollapse = document.getElementById('filterCollapse');
            if (filterCollapse && filterCollapse.classList.contains('show')) {
                const bsCollapse = bootstrap.Collapse.getInstance(filterCollapse);
                if (bsCollapse) {
                    bsCollapse.hide();
                }
            }
            
            // Setup image carousels for search results
            setTimeout(() => {
                grid.querySelectorAll('.product-image-carousel').forEach(carousel => {
                    setupProductImageCarousel(carousel);
                });
            }, 100);
        } else {
            // No search results found - show empty state with button to view all products
            grid.innerHTML = `
                <div class="col-12">
                    <div class="text-center py-5 text-muted">
                        <i class="bi bi-search" style="font-size: 4rem; opacity: 0.5;"></i>
                        <p class="mt-3 mb-0">検索結果が見つかりませんでした</p>
                        <p class="text-muted small mt-2 mb-4">別のキーワードで検索してみてください</p>
                        <button class="btn btn-primary btn-lg" onclick="viewAllProducts()">
                            <i class="bi bi-grid-3x3-gap me-2"></i>すべての商品を見る
                        </button>
                    </div>
                </div>
            `;
            // Update results info for empty search
            updateResultsInfo(0, page, 0);
        }
    } catch (error) {
        console.error('Error searching products:', error);
        const grid = document.getElementById('productsGrid');
        if (grid) {
            grid.innerHTML = `
                <div class="col-12">
                    <div class="text-center py-5 text-danger">
                        <i class="bi bi-exclamation-triangle" style="font-size: 3rem;"></i>
                        <p class="mt-3 mb-0">検索に失敗しました</p>
                    </div>
                </div>
            `;
        }
    }
}

function renderPagination(totalPages, currentPage) {
    const pagination = document.getElementById('pagination');
    if (!pagination) return;
    
    if (totalPages <= 1) {
        pagination.innerHTML = '';
        return;
    }
    
    let paginationHTML = '<nav><ul class="pagination justify-content-center">';
    
    // Previous button
    paginationHTML += `
        <li class="page-item ${currentPage === 1 ? 'disabled' : ''}">
            <a class="page-link" href="#" onclick="changePage(${currentPage - 1}); return false;">前へ</a>
        </li>
    `;
    
    // Page numbers
    const maxPages = 10;
    let startPage = Math.max(1, currentPage - Math.floor(maxPages / 2));
    let endPage = Math.min(totalPages, startPage + maxPages - 1);
    
    if (endPage - startPage < maxPages - 1) {
        startPage = Math.max(1, endPage - maxPages + 1);
    }
    
    for (let i = startPage; i <= endPage; i++) {
        paginationHTML += `
            <li class="page-item ${i === currentPage ? 'active' : ''}">
                <a class="page-link" href="#" onclick="changePage(${i}); return false;">${i}</a>
            </li>
        `;
    }
    
    // Next button
    paginationHTML += `
        <li class="page-item ${currentPage === totalPages ? 'disabled' : ''}">
            <a class="page-link" href="#" onclick="changePage(${currentPage + 1}); return false;">次へ</a>
        </li>
    `;
    
    paginationHTML += '</ul></nav>';
    pagination.innerHTML = paginationHTML;
}

function changePage(page) {
    if (page < 1) return;
    currentPage = page;
    const searchInput = document.getElementById('searchInput');
    const query = searchInput ? searchInput.value.trim() : '';
    
    // If there's a search query, use SearchServlet
    if (query) {
        searchProducts(query, page);
    } else {
        loadProducts(page);
    }
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

// Setup filter toggle button state
function setupFilterToggle() {
    const filterToggleBtn = document.getElementById('filterToggleBtn');
    const filterCollapse = document.getElementById('filterCollapse');
    
    if (filterToggleBtn && filterCollapse) {
        // Update button text/icon when filter is shown/hidden
        filterCollapse.addEventListener('show.bs.collapse', function() {
            if (filterToggleBtn) {
                filterToggleBtn.innerHTML = '<i class="bi bi-funnel-fill me-2"></i>フィルターを閉じる';
                filterToggleBtn.classList.remove('btn-outline-primary');
                filterToggleBtn.classList.add('btn-primary');
            }
        });
        
        filterCollapse.addEventListener('hide.bs.collapse', function() {
            if (filterToggleBtn) {
                filterToggleBtn.innerHTML = '<i class="bi bi-funnel me-2"></i>フィルター';
                filterToggleBtn.classList.remove('btn-primary');
                filterToggleBtn.classList.add('btn-outline-primary');
            }
        });
    }
}
