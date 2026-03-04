// Rental page JavaScript

let selectedProduct = null;
let rentalDays = 0;
let cameFromProductDetail = false; // true when opened with product_id in URL

document.addEventListener('DOMContentLoaded', function() {
    // Check if product_id is in URL (from product detail page)
    const urlParams = new URLSearchParams(window.location.search);
    const productId = urlParams.get('product_id');
    
    if (productId) {
        cameFromProductDetail = true;
        loadProductForRental(productId);
    } else {
        loadRentalProducts();
    }
    
    // Setup date validation
    setupDateValidation();
    
    // Setup form listeners
    setupFormListeners();
    
    // Load user addresses
    loadAddresses();
});

async function loadRentalProducts() {
    try {
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/ProductServlet?action=getProducts&is_rental=true`);
        let products = KaruruUtils.extractData(data, 'products') || data || [];
        
        // Filter: hanya produk dengan stock > 0 dan is_rental = true
        products = products.filter(product => {
            return product.is_rental === true && 
                   product.stock_quantity > 0 && 
                   product.status === 'available';
        });
        
        const container = document.getElementById('productSelection');
        if (!container) return;
        
        if (products && products.length > 0) {
            container.innerHTML = products.map(product => {
                const fullImageUrl = KaruruUtils.resolveProductImageUrl(product.image_url);
                
                const isRented = product.is_currently_rented === true;
                const rentalEndDate = product.rental_end_date;
                let rentalStatusBadge = '';
                let rentalInfo = '';
                
                if (isRented && rentalEndDate) {
                    const endDate = new Date(rentalEndDate);
                    const today = new Date();
                    const daysUntilReturn = Math.ceil((endDate - today) / (1000 * 60 * 60 * 24));
                    
                    if (daysUntilReturn > 0) {
                        rentalStatusBadge = `
                            <div class="position-absolute top-0 start-0 m-2">
                                <span class="badge bg-danger">
                                    <i class="bi bi-clock-history me-1"></i>レンタル中
                                </span>
                            </div>
                        `;
                        rentalInfo = `
                            <div class="alert alert-warning mb-3 small">
                                <i class="bi bi-info-circle me-2"></i>
                                <strong>この商品は現在レンタル中です</strong><br>
                                返却予定日: <strong>${endDate.toLocaleDateString('ja-JP')}</strong> (あと${daysUntilReturn}日)
                            </div>
                        `;
                    }
                }
                
                return `
                    <div class="col-6 col-md-4 mb-4">
                        <div class="rental-product-card position-relative ${isRented ? 'opacity-75' : ''}" 
                             onclick="${isRented ? 'showRentalInfo()' : `selectProductForRental(${product.product_id})`}"
                             data-product-id="${product.product_id}"
                             data-is-rented="${isRented}"
                             data-rental-end-date="${rentalEndDate || ''}">
                            ${rentalStatusBadge}
                            <img src="${fullImageUrl}" 
                                 class="rental-product-image" 
                                 alt="${escapeHtml(product.product_name || '')}"
                                 onerror="this.onerror=null; this.src='${KaruruUtils.resolveProductImageUrl('')}';">
                            <div class="rental-product-body">
                                <h6 class="fw-semibold mb-2" style="min-height: 2.5rem;">${escapeHtml(product.product_name || '')}</h6>
                                ${rentalInfo}
                                <p class="text-muted small mb-3" style="min-height: 3rem; overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;">
                                    ${escapeHtml(product.description || '')}
                                </p>
                                <div class="mb-2">
                                    <small class="text-muted">
                                        <i class="bi bi-box-seam me-1"></i>在庫: <strong>${product.stock_quantity || 0}個</strong>
                                    </small>
                                </div>
                                <div class="mb-3 d-flex flex-wrap gap-2">
                                    ${product.rental_price_daily ? `
                                        <span class="rental-price-badge bg-warning text-dark">
                                            <i class="bi bi-calendar-day me-1"></i>¥${parseInt(product.rental_price_daily).toLocaleString('ja-JP')}/日
                                        </span>
                                    ` : ''}
                                    ${product.rental_price_weekly ? `
                                        <span class="rental-price-badge bg-info">
                                            <i class="bi bi-calendar-week me-1"></i>¥${parseInt(product.rental_price_weekly).toLocaleString('ja-JP')}/週
                                        </span>
                                    ` : ''}
                                    ${product.rental_price_monthly ? `
                                        <span class="rental-price-badge bg-success">
                                            <i class="bi bi-calendar-month me-1"></i>¥${parseInt(product.rental_price_monthly).toLocaleString('ja-JP')}/月
                                        </span>
                                    ` : ''}
                                </div>
                                ${isRented ? `
                                    <button class="btn btn-secondary w-100" disabled>
                                        <i class="bi bi-lock me-2"></i>現在レンタル中
                                    </button>
                                ` : `
                                    <button class="btn btn-primary w-100" onclick="event.stopPropagation(); selectProductForRental(${product.product_id})">
                                        <i class="bi bi-check-circle me-2"></i>この商品を選択
                                    </button>
                                `}
                            </div>
                        </div>
                    </div>
                `;
            }).join('');
        } else {
            container.innerHTML = `
                <div class="col-12">
                    <div class="alert alert-info">
                        <i class="bi bi-info-circle me-2"></i>レンタル可能な商品が見つかりませんでした
                    </div>
                </div>
            `;
            const emptyState = document.getElementById('emptyRentalState');
            if (emptyState) emptyState.style.display = 'flex';
        }
    } catch (error) {
        console.error('Error loading rental products:', error);
        const container = document.getElementById('productSelection');
        if (container) {
            container.innerHTML = `
                <div class="col-12">
                    <div class="alert alert-danger">
                        <i class="bi bi-exclamation-triangle me-2"></i>商品の読み込みに失敗しました
                    </div>
                </div>
            `;
        }
    }
}

async function loadProductForRental(productId) {
    try {
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/ProductDetailsServlet?id=${productId}`);
        const product = data.product || data;
        
        if (product && product.product_id) {
            if (!product.is_rental) {
                if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
                    KaruruUtils.showNotification('この商品はレンタル対象ではありません', 'warning');
                }
                cameFromProductDetail = false;
                if (window.history && window.history.replaceState) {
                    window.history.replaceState({}, '', `${window.CONTEXT_PATH}/rental.jsp`);
                }
                loadRentalProducts();
                return;
            }
            selectProductForRental(product.product_id, product);
        } else {
            throw new Error('Product not found');
        }
    } catch (error) {
        console.error('Error loading product for rental:', error);
        if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
            KaruruUtils.showNotification('商品の読み込みに失敗しました。レンタル一覧を表示します。', 'warning');
        }
        cameFromProductDetail = false;
        if (window.history && window.history.replaceState) {
            window.history.replaceState({}, '', `${window.CONTEXT_PATH}/rental.jsp`);
        }
        loadRentalProducts();
    }
}

async function selectProductForRental(productId, productData = null) {
    try {
        if (!productData) {
            const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/ProductDetailsServlet?id=${productId}`);
            productData = data.product || data;
        }
        
        if (!productData || !productData.product_id) {
            throw new Error('Product not found');
        }
        
        selectedProduct = productData;
        
        // Show rental form section
        document.getElementById('rentalFormSection').style.display = 'block';
        document.getElementById('productSelection').style.display = 'none';
        
        // Populate product info
        renderSelectedProductInfo(productData);
        
        // Populate form
        document.getElementById('rentalProductId').value = productData.product_id;
        
        // Update rental type dropdown based on available prices
        updateRentalTypeDropdown(productData);
        
        // Update price info
        updateRentalPriceInfo(productData);
        
        // Show "商品詳細に戻る" when came from product detail
        const backBtn = document.getElementById('backToProductBtn');
        if (backBtn) {
            if (cameFromProductDetail) {
                backBtn.classList.remove('d-none');
            } else {
                backBtn.classList.add('d-none');
            }
        }
        
        // Scroll to form
        document.getElementById('rentalFormSection').scrollIntoView({ behavior: 'smooth', block: 'start' });
        
    } catch (error) {
        console.error('Error selecting product for rental:', error);
    }
}

function backToProductDetail() {
    if (selectedProduct && selectedProduct.product_id) {
        window.location.href = `${window.CONTEXT_PATH}/product-detail.jsp?id=${selectedProduct.product_id}`;
    }
}

function renderSelectedProductInfo(product) {
    const container = document.getElementById('selectedProductInfo');
    if (!container) return;
    
    const fullImageUrl = KaruruUtils.resolveProductImageUrl(product.image_url);
    
    container.innerHTML = `
        <div class="row align-items-center g-3">
            <div class="col-md-3 col-sm-4">
                <img src="${fullImageUrl}" 
                     class="img-fluid rounded shadow" 
                     alt="${escapeHtml(product.product_name || '')}"
                     style="max-height: 200px; object-fit: cover; width: 100%;"
                     onerror="this.src='${window.CONTEXT_PATH}/img/default-product.png'">
            </div>
            <div class="col-md-9 col-sm-8">
                <h5 class="mb-2 fw-bold">
                    <i class="bi bi-box-seam me-2 text-primary"></i>${escapeHtml(product.product_name || '')}
                </h5>
                <p class="text-muted mb-3 small">${escapeHtml(product.description || '').substring(0, 200)}${product.description && product.description.length > 200 ? '...' : ''}</p>
                <div class="d-flex gap-2 flex-wrap">
                    ${product.rental_price_daily ? `
                        <span class="rental-price-badge bg-warning text-dark">
                            <i class="bi bi-calendar-day me-1"></i>日: ¥${parseInt(product.rental_price_daily).toLocaleString('ja-JP')}
                        </span>
                    ` : ''}
                    ${product.rental_price_weekly ? `
                        <span class="rental-price-badge bg-info">
                            <i class="bi bi-calendar-week me-1"></i>週: ¥${parseInt(product.rental_price_weekly).toLocaleString('ja-JP')}
                        </span>
                    ` : ''}
                    ${product.rental_price_monthly ? `
                        <span class="rental-price-badge bg-success">
                            <i class="bi bi-calendar-month me-1"></i>月: ¥${parseInt(product.rental_price_monthly).toLocaleString('ja-JP')}
                        </span>
                    ` : ''}
                </div>
            </div>
        </div>
    `;
}

function updateRentalTypeDropdown(product) {
    const rentalTypeSelect = document.getElementById('rentalType');
    if (!rentalTypeSelect) return;
    
    // Clear existing options
    rentalTypeSelect.innerHTML = '<option value="">選択してください</option>';
    
    // Add options only for available rental types
    const hasDaily = product.rental_price_daily && parseFloat(product.rental_price_daily) > 0;
    const hasWeekly = product.rental_price_weekly && parseFloat(product.rental_price_weekly) > 0;
    const hasMonthly = product.rental_price_monthly && parseFloat(product.rental_price_monthly) > 0;
    
    if (hasDaily) {
        const option = document.createElement('option');
        option.value = 'daily';
        option.textContent = `日単位 (¥${parseFloat(product.rental_price_daily).toLocaleString('ja-JP', {minimumFractionDigits: 0, maximumFractionDigits: 0})}/日)`;
        rentalTypeSelect.appendChild(option);
    }
    
    if (hasWeekly) {
        const option = document.createElement('option');
        option.value = 'weekly';
        option.textContent = `週単位 (¥${parseFloat(product.rental_price_weekly).toLocaleString('ja-JP', {minimumFractionDigits: 0, maximumFractionDigits: 0})}/週)`;
        rentalTypeSelect.appendChild(option);
    }
    
    if (hasMonthly) {
        const option = document.createElement('option');
        option.value = 'monthly';
        option.textContent = `月単位 (¥${parseFloat(product.rental_price_monthly).toLocaleString('ja-JP', {minimumFractionDigits: 0, maximumFractionDigits: 0})}/月)`;
        rentalTypeSelect.appendChild(option);
    }
    
    // If no rental types available, show message
    if (!hasDaily && !hasWeekly && !hasMonthly) {
        const option = document.createElement('option');
        option.value = '';
        option.textContent = 'レンタル価格が設定されていません';
        option.disabled = true;
        rentalTypeSelect.appendChild(option);
    }
    
    // Reset selection
    rentalTypeSelect.value = '';
}

function updateRentalPriceInfo(product) {
    const dailyPriceEl = document.getElementById('dailyPrice');
    const weeklyPriceEl = document.getElementById('weeklyPrice');
    const monthlyPriceEl = document.getElementById('monthlyPrice');
    
    if (dailyPriceEl) {
        if (product.rental_price_daily && product.rental_price_daily > 0) {
            dailyPriceEl.textContent = `¥${parseFloat(product.rental_price_daily).toLocaleString('ja-JP', {minimumFractionDigits: 0, maximumFractionDigits: 0})}/日`;
            dailyPriceEl.classList.remove('text-muted');
        } else {
            dailyPriceEl.textContent = '設定されていません';
            dailyPriceEl.classList.add('text-muted');
        }
    }
    
    if (weeklyPriceEl) {
        if (product.rental_price_weekly && product.rental_price_weekly > 0) {
            weeklyPriceEl.textContent = `¥${parseFloat(product.rental_price_weekly).toLocaleString('ja-JP', {minimumFractionDigits: 0, maximumFractionDigits: 0})}/週`;
            weeklyPriceEl.classList.remove('text-muted');
        } else {
            weeklyPriceEl.textContent = '設定されていません';
            weeklyPriceEl.classList.add('text-muted');
        }
    }
    
    if (monthlyPriceEl) {
        if (product.rental_price_monthly && product.rental_price_monthly > 0) {
            monthlyPriceEl.textContent = `¥${parseFloat(product.rental_price_monthly).toLocaleString('ja-JP', {minimumFractionDigits: 0, maximumFractionDigits: 0})}/月`;
            monthlyPriceEl.classList.remove('text-muted');
        } else {
            monthlyPriceEl.textContent = '設定されていません';
            monthlyPriceEl.classList.add('text-muted');
        }
    }
    
    // Recalculate price after updating info
    setTimeout(() => calculateRentalPrice(), 100);
}

function setupDateValidation() {
    const startDateInput = document.getElementById('rentalStartDate');
    const endDateInput = document.getElementById('rentalEndDate');
    
    if (startDateInput && endDateInput) {
        // Set min date to today
        const today = new Date();
        today.setHours(0, 0, 0, 0);
        const todayStr = today.toISOString().split('T')[0];
        startDateInput.min = todayStr;
        
        // Set initial min for end date
        endDateInput.min = todayStr;
        
        startDateInput.addEventListener('change', function() {
            const startDate = this.value;
            if (startDate) {
                // Set end date minimum to start date
                endDateInput.min = startDate;
                
                // If end date is before start date, update it
                if (endDateInput.value && endDateInput.value < startDate) {
                    endDateInput.value = startDate;
                }
                
                // Recalculate price
                setTimeout(() => calculateRentalPrice(), 100);
            }
        });
        
        endDateInput.addEventListener('change', function() {
            const startDate = startDateInput.value;
            const endDate = this.value;
            
            // Validate end date is not before start date
            if (startDate && endDate && endDate < startDate) {
                this.setCustomValidity('終了日は開始日より後の日付を選択してください');
                this.reportValidity();
                setTimeout(() => calculateRentalPrice(), 100);
            } else {
                this.setCustomValidity('');
                setTimeout(() => calculateRentalPrice(), 100);
            }
        });
        
        // Also trigger calculation on input (for real-time updates)
        startDateInput.addEventListener('input', function() {
            setTimeout(() => calculateRentalPrice(), 100);
        });
        
        endDateInput.addEventListener('input', function() {
            setTimeout(() => calculateRentalPrice(), 100);
        });
    }
}

function setupFormListeners() {
    const rentalType = document.getElementById('rentalType');
    const quantity = document.getElementById('rentalQuantity');
    
    if (rentalType) {
        rentalType.addEventListener('change', function() {
            // Recalculate when rental type changes
            setTimeout(() => calculateRentalPrice(), 100);
        });
    }
    
    if (quantity) {
        quantity.addEventListener('input', function() {
            // Validate quantity
            const qty = parseInt(this.value) || 1;
            if (qty < 1) {
                this.value = 1;
            }
            // Recalculate price
            setTimeout(() => calculateRentalPrice(), 100);
        });
        
        quantity.addEventListener('change', function() {
            // Ensure quantity is at least 1
            const qty = parseInt(this.value) || 1;
            if (qty < 1) {
                this.value = 1;
            }
            setTimeout(() => calculateRentalPrice(), 100);
        });
    }
}

function calculateRentalPrice() {
    if (!selectedProduct) {
        resetPriceDisplay();
        return;
    }
    
    const startDate = document.getElementById('rentalStartDate').value;
    const endDate = document.getElementById('rentalEndDate').value;
    const rentalType = document.getElementById('rentalType').value;
    const quantity = parseInt(document.getElementById('rentalQuantity').value) || 1;
    
    // Reset if required fields are missing
    if (!startDate || !endDate || !rentalType) {
        resetPriceDisplay(quantity);
        if (!rentalType) {
            document.getElementById('rentalPeriod').textContent = 'レンタルタイプを選択してください';
        }
        return;
    }
    
    // Validate that the selected rental type has a price
    const hasDaily = selectedProduct.rental_price_daily && parseFloat(selectedProduct.rental_price_daily) > 0;
    const hasWeekly = selectedProduct.rental_price_weekly && parseFloat(selectedProduct.rental_price_weekly) > 0;
    const hasMonthly = selectedProduct.rental_price_monthly && parseFloat(selectedProduct.rental_price_monthly) > 0;
    
    if ((rentalType === 'daily' && !hasDaily) ||
        (rentalType === 'weekly' && !hasWeekly) ||
        (rentalType === 'monthly' && !hasMonthly)) {
        resetPriceDisplay(quantity);
        document.getElementById('rentalPeriod').textContent = '選択されたレンタルタイプの価格が設定されていません';
        return;
    }
    
    // Parse dates (set to midnight to avoid timezone issues)
    const start = new Date(startDate + 'T00:00:00');
    const end = new Date(endDate + 'T00:00:00');
    
    // Validate dates
    if (isNaN(start.getTime()) || isNaN(end.getTime())) {
        resetPriceDisplay(quantity);
        return;
    }
    
    // Check if end date is before start date
    if (end < start) {
        resetPriceDisplay(quantity);
        document.getElementById('rentalPeriod').textContent = '日付が無効です';
        document.getElementById('basePrice').textContent = '¥0';
        document.getElementById('totalRentalPrice').textContent = '¥0';
        return;
    }
    
    // Calculate days (inclusive of both start and end date)
    const diffTime = end.getTime() - start.getTime();
    rentalDays = Math.floor(diffTime / (1000 * 60 * 60 * 24)) + 1;
    
    if (rentalDays <= 0) {
        resetPriceDisplay(quantity);
        return;
    }
    
    // Get rental prices
    const dailyPrice = parseFloat(selectedProduct.rental_price_daily) || 0;
    const weeklyPrice = parseFloat(selectedProduct.rental_price_weekly) || 0;
    const monthlyPrice = parseFloat(selectedProduct.rental_price_monthly) || 0;
    
    let basePrice = 0;
    let periodText = '';
    let unitPrice = 0;
    let units = 0;
    
    switch(rentalType) {
        case 'daily':
            if (dailyPrice <= 0) {
                resetPriceDisplay(quantity);
                document.getElementById('rentalPeriod').textContent = '日単位の価格が設定されていません';
                return;
            }
            unitPrice = dailyPrice;
            units = rentalDays;
            basePrice = unitPrice * units;
            periodText = `${rentalDays}日間`;
            break;
            
        case 'weekly':
            if (weeklyPrice <= 0) {
                resetPriceDisplay(quantity);
                document.getElementById('rentalPeriod').textContent = '週単位の価格が設定されていません';
                return;
            }
            unitPrice = weeklyPrice;
            units = Math.ceil(rentalDays / 7);
            basePrice = unitPrice * units;
            periodText = `${units}週間 (${rentalDays}日間)`;
            break;
            
        case 'monthly':
            if (monthlyPrice <= 0) {
                resetPriceDisplay(quantity);
                document.getElementById('rentalPeriod').textContent = '月単位の価格が設定されていません';
                return;
            }
            unitPrice = monthlyPrice;
            units = Math.ceil(rentalDays / 30);
            basePrice = unitPrice * units;
            periodText = `${units}ヶ月 (${rentalDays}日間)`;
            break;
            
        default:
            resetPriceDisplay(quantity);
            return;
    }
    
    // Calculate total with quantity
    const totalPrice = basePrice * quantity;
    
    // Update display
    document.getElementById('basePrice').textContent = KaruruUtils.formatPrice(basePrice);
    document.getElementById('rentalPeriod').textContent = periodText;
    document.getElementById('displayQuantity').textContent = quantity;
    document.getElementById('totalRentalPrice').textContent = KaruruUtils.formatPrice(totalPrice);
    
    // Show detailed breakdown in console for debugging
    console.log('Rental Price Calculation:', {
        rentalType,
        rentalDays,
        unitPrice,
        units,
        basePrice,
        quantity,
        totalPrice
    });
}

function resetPriceDisplay(quantity = 1) {
    document.getElementById('totalRentalPrice').textContent = '¥0';
    document.getElementById('basePrice').textContent = '¥0';
    document.getElementById('rentalPeriod').textContent = '-';
    document.getElementById('displayQuantity').textContent = quantity;
}

async function loadAddresses() {
    try {
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/ProfileServlet?action=getAddresses`);
        const addresses = KaruruUtils.extractData(data, 'addresses') || data || [];
        
        const container = document.getElementById('addressSelection');
        if (!container) return;
        
        if (addresses && addresses.length > 0) {
            container.innerHTML = `
                <div class="row g-3">
                    ${addresses.map((address, index) => `
                        <div class="col-md-6">
                            <div class="rental-address-card ${address.is_default ? 'selected' : ''}" 
                                 data-address-id="${address.address_id}"
                                 onclick="document.getElementById('address${address.address_id}').checked = true; updateAddressSelection();">
                                <div class="form-check">
                                    <input class="form-check-input" type="radio" name="address" 
                                           id="address${address.address_id}" 
                                           value="${address.address_id}"
                                           ${address.is_default ? 'checked' : ''}
                                           onchange="updateAddressSelection()">
                                    <label class="form-check-label w-100" for="address${address.address_id}">
                                        <div class="d-flex justify-content-between align-items-start mb-2">
                                            <strong class="d-flex align-items-center">
                                                <i class="bi bi-geo-alt me-2 text-primary"></i>${escapeHtml(address.address_label || '住所')}
                                            </strong>
                                            ${address.is_default ? '<span class="badge bg-primary">デフォルト</span>' : ''}
                                        </div>
                                        <div class="text-muted small">
                                            <div class="mb-1">
                                                <i class="bi bi-person me-1"></i>${escapeHtml(address.recipient_name || '')}
                                            </div>
                                            <div class="mb-1">
                                                <i class="bi bi-mailbox me-1"></i>〒${escapeHtml(address.postal_code || '')}
                                            </div>
                                            <div class="mb-1">
                                                <i class="bi bi-geo me-1"></i>${escapeHtml(address.prefecture || '')} ${escapeHtml(address.city || '')}
                                            </div>
                                            <div>
                                                <i class="bi bi-house me-1"></i>${escapeHtml(address.address_line1 || '')}
                                            </div>
                                        </div>
                                    </label>
                                </div>
                            </div>
                        </div>
                    `).join('')}
                </div>
                <div class="mt-4">
                    <button class="btn btn-outline-primary" onclick="showAddAddressModal()">
                        <i class="bi bi-plus-circle me-2"></i>新しい住所を追加
                    </button>
                </div>
            `;
            // Initialize address selection visual state
            setTimeout(updateAddressSelection, 100);
        } else {
            container.innerHTML = `
                <div class="alert alert-warning mb-3">
                    <div class="d-flex align-items-start">
                        <i class="bi bi-exclamation-triangle me-2 fs-5"></i>
                        <div>
                            <strong>配送先住所が登録されていません</strong>
                            <p class="mb-0 mt-1 small">レンタルを予約するには、配送先住所を登録する必要があります。</p>
                        </div>
                    </div>
                </div>
                <button class="btn btn-primary btn-lg" onclick="showAddAddressModal()">
                    <i class="bi bi-plus-circle me-2"></i>住所を追加
                </button>
            `;
        }
    } catch (error) {
        console.error('Error loading addresses:', error);
        const container = document.getElementById('addressSelection');
        if (container) {
            container.innerHTML = `
                <div class="alert alert-danger">
                    <i class="bi bi-exclamation-triangle me-2"></i>住所の読み込みに失敗しました
                </div>
            `;
        }
    }
}

function showAddAddressModal() {
    // Redirect to profile or settings page to add address
    window.location.href = `${window.CONTEXT_PATH}/profile.jsp?tab=addresses`;
}

function updateAddressSelection() {
    // Update visual selection state
    document.querySelectorAll('.rental-address-card').forEach(card => {
        const radio = card.querySelector('input[type="radio"]');
        if (radio && radio.checked) {
            card.classList.add('selected');
        } else {
            card.classList.remove('selected');
        }
    });
}

function resetRentalForm() {
    selectedProduct = null;
    cameFromProductDetail = false; // Reset - user chose to go back to list
    document.getElementById('rentalFormSection').style.display = 'none';
    document.getElementById('productSelection').style.display = 'block';
    document.getElementById('rentalForm').reset();
    document.getElementById('selectedProductInfo').innerHTML = '';
    
    // Hide back to product button
    const backBtn = document.getElementById('backToProductBtn');
    if (backBtn) backBtn.classList.add('d-none');
    
    // Reset rental type dropdown
    const rentalTypeSelect = document.getElementById('rentalType');
    if (rentalTypeSelect) {
        rentalTypeSelect.innerHTML = '<option value="">商品を選択してください</option>';
    }
    
    // Clear URL product_id and reload products
    if (window.history && window.history.replaceState) {
        window.history.replaceState({}, '', `${window.CONTEXT_PATH}/rental.jsp`);
    }
    loadRentalProducts();
}

async function confirmRental() {
    if (!selectedProduct) {
        return;
    }
    
    const form = document.getElementById('rentalForm');
    if (!form.checkValidity()) {
        form.reportValidity();
        return;
    }
    
    const startDate = document.getElementById('rentalStartDate').value;
    const endDate = document.getElementById('rentalEndDate').value;
    const rentalType = document.getElementById('rentalType').value;
    const quantity = parseInt(document.getElementById('rentalQuantity').value) || 1;
    
    // Note: Address selection is kept for UX but not sent to server
    // RentalServlet doesn't require address_id in the current implementation
    const selectedAddress = document.querySelector('input[name="address"]:checked');
    // Address validation is optional - can be removed if not needed
    
    if (new Date(endDate) < new Date(startDate)) {
        return;
    }
    
    if (!window.currentUserId) {
        window.location.href = `${window.CONTEXT_PATH}/login.jsp?redirect=${encodeURIComponent(window.location.pathname + window.location.search)}`;
        return;
    }
    
    // Calculate total price first
    calculateRentalPrice();
    
    // Wait a bit for calculation to complete
    await new Promise(resolve => setTimeout(resolve, 200));
    
    // Get total price from the calculated value
    const totalPriceText = document.getElementById('totalRentalPrice').textContent;
    const totalPrice = parseFloat(totalPriceText.replace(/[¥,]/g, '')) || 0;
    
    // Validate total price
    if (totalPrice <= 0 || isNaN(totalPrice)) {
        return;
    }
    
    if (!confirm(`レンタルを予約しますか？\n合計金額: ${KaruruUtils.formatPrice(totalPrice)}`)) {
        return;
    }
    
    const confirmBtn = document.getElementById('confirmRentalBtn');
    const originalText = confirmBtn ? confirmBtn.innerHTML : '';
    
    try {
        // Disable button and show loading state
        if (confirmBtn) {
            confirmBtn.disabled = true;
            confirmBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>処理中...';
        }
        
        // Prepare rental data according to RentalServlet API
        const rentalData = {
            action: 'create',
            product_id: selectedProduct.product_id,
            start_date: startDate,
            end_date: endDate,
            rental_type: rentalType,
            quantity: quantity
        };
        
        // Send request to RentalServlet
        const response = await fetch(`${window.CONTEXT_PATH}/RentalServlet`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            credentials: 'same-origin',
            body: JSON.stringify(rentalData)
        });
        
        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.error || errorData.message || `HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        
        if (data && data.success === true) {
            if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
                KaruruUtils.showNotification('レンタルを予約しました。予約一覧で確認できます。', 'success');
            }
            
            setTimeout(() => {
                window.location.href = `${window.CONTEXT_PATH}/rentals.jsp?status=pending`;
            }, 1200);
        } else {
            throw new Error(data?.error || data?.message || 'レンタル予約に失敗しました');
        }
    } catch (error) {
        console.error('Error confirming rental:', error);
        if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
            KaruruUtils.showNotification(error.message || 'レンタル予約に失敗しました', 'danger');
        }
        
        // Re-enable button
        if (confirmBtn) {
            confirmBtn.disabled = false;
            confirmBtn.innerHTML = originalText;
        }
    }
}

function showRentalInfo() {
    // This function can be enhanced to show a modal with rental details
    if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
        KaruruUtils.showNotification('この商品は現在レンタル中です。返却予定日を確認してください。', 'info');
    }
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

