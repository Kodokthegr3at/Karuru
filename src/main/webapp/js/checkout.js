// Checkout page JavaScript

let selectedAddressId = null;
let selectedDeliveryMethod = 'standard';
let selectedCourier = 'yamato';

// Delivery method prices
const deliveryPrices = {
    standard: 500,
    express: 1200,
    same_day: 2500
};

// Delivery method names
const deliveryMethodNames = {
    standard: '標準配送',
    express: '速達配送',
    same_day: '当日配送'
};

// Courier names
const courierNames = {
    yamato: 'ヤマト運輸',
    sagawa: '佐川急便',
    japan_post: '日本郵便'
};

document.addEventListener('DOMContentLoaded', function() {
    loadAddresses();
    loadCartSummary();
    initializeAddressModal();
    initializePostalCodeAutoFill();
    initializeDeliveryMethod();
});

// Initialize address modal
function initializeAddressModal() {
    // Save address button
    const saveAddressBtn = document.getElementById('saveAddressBtn');
    if (saveAddressBtn) {
        saveAddressBtn.addEventListener('click', saveAddress);
    }
}

// Initialize delivery method selection
function initializeDeliveryMethod() {
    // Delivery method radio buttons
    const deliveryMethods = document.querySelectorAll('input[name="deliveryMethod"]');
    deliveryMethods.forEach(radio => {
        radio.addEventListener('change', function() {
            selectedDeliveryMethod = this.value;
            updateShippingCost();
            updateDeliveryEstimate();
        });
    });
    
    // Courier select
    const courierSelect = document.getElementById('courierSelect');
    if (courierSelect) {
        courierSelect.addEventListener('change', function() {
            selectedCourier = this.value;
            updateDeliveryEstimate();
        });
    }
    
    // Initialize with default values
    updateShippingCost();
    updateDeliveryEstimate();
}

// Update shipping cost based on selected delivery method
function updateShippingCost() {
    const shippingCost = deliveryPrices[selectedDeliveryMethod] || 500;
    
    // Update summary if cart is already loaded
    const currentSubtotal = parseFloat(document.getElementById('subtotal')?.textContent.replace(/[¥,]/g, '') || 0);
    const currentDiscount = parseFloat(document.getElementById('discount')?.textContent.replace(/[¥,]/g, '') || 0);
    
    if (currentSubtotal > 0) {
        const fee = Math.round(currentSubtotal * 0.03);
        const total = currentSubtotal + shippingCost + fee - currentDiscount;
        
        updateSummary({
            subtotal: currentSubtotal,
            shipping: shippingCost,
            discount: currentDiscount,
            fee: fee,
            total: total
        });
    }
}

// Update delivery estimate date
function updateDeliveryEstimate() {
    const estimateEl = document.getElementById('estimatedDeliveryDate');
    if (!estimateEl) return;
    
    const today = new Date();
    let deliveryDate = new Date(today);
    let estimateText = '';
    
    switch(selectedDeliveryMethod) {
        case 'same_day':
            // Same day delivery - only if ordered before 12 PM
            const currentHour = today.getHours();
            if (currentHour < 12) {
                estimateText = '今日中';
            } else {
                estimateText = '明日';
                deliveryDate.setDate(today.getDate() + 1);
            }
            break;
        case 'express':
            // Express: 1-2 business days
            deliveryDate.setDate(today.getDate() + 2);
            estimateText = formatDeliveryDate(deliveryDate) + ' (1-2営業日)';
            break;
        case 'standard':
        default:
            // Standard: 3-5 business days
            deliveryDate.setDate(today.getDate() + 4);
            estimateText = formatDeliveryDate(deliveryDate) + ' (3-5営業日)';
            break;
    }
    
    estimateEl.textContent = estimateText;
}

// Format delivery date
function formatDeliveryDate(date) {
    const days = ['日', '月', '火', '水', '木', '金', '土'];
    const month = date.getMonth() + 1;
    const day = date.getDate();
    const dayOfWeek = days[date.getDay()];
    return `${month}月${day}日(${dayOfWeek})`;
}

// Initialize postal code auto-fill functionality
function initializePostalCodeAutoFill() {
    const postalCodeInput = document.getElementById('postalCode');
    if (!postalCodeInput) return;
    
    let debounceTimer;
    
    postalCodeInput.addEventListener('input', function() {
        const postalCode = this.value.replace(/\s|-/g, ''); // Remove spaces and dashes
        
        // Clear previous timer
        clearTimeout(debounceTimer);
        
        // Only fetch if postal code is 7 digits
        if (postalCode.length === 7 && /^\d{7}$/.test(postalCode)) {
            debounceTimer = setTimeout(() => {
                fetchAddressFromPostalCode(postalCode);
            }, 500); // Wait 500ms after user stops typing
        } else if (postalCode.length === 0) {
            // Clear fields if postal code is empty
            clearAddressFields();
        }
    });
    
    // Also handle paste event
    postalCodeInput.addEventListener('paste', function() {
        setTimeout(() => {
            const postalCode = this.value.replace(/\s|-/g, '');
            if (postalCode.length === 7 && /^\d{7}$/.test(postalCode)) {
                fetchAddressFromPostalCode(postalCode);
            }
        }, 100);
    });
}

// Fetch address information from Japan Postal Code API
async function fetchAddressFromPostalCode(postalCode) {
    if (!postalCode || postalCode.length !== 7) return;
    
    const postalCodeInput = document.getElementById('postalCode');
    const postalCodeStatus = document.getElementById('postalCodeStatus');
    const spinner = postalCodeStatus?.querySelector('.spinner-border');
    const checkIcon = postalCodeStatus?.querySelector('.bi-check-circle');
    
    try {
        // Show loading indicator
        if (postalCodeStatus) {
            postalCodeStatus.style.display = 'flex';
            if (spinner) spinner.style.display = 'inline-block';
            if (checkIcon) checkIcon.style.display = 'none';
        }
        
        // Use zipcloud.ibsnet.co.jp API (free and simple)
        const response = await fetch(`https://zipcloud.ibsnet.co.jp/api/search?zipcode=${postalCode}`);
        const data = await response.json();
        
        if (data.status === 200 && data.results && data.results.length > 0) {
            const result = data.results[0];
            
            // Format postal code with dash (123-4567)
            const formattedPostalCode = postalCode.substring(0, 3) + '-' + postalCode.substring(3);
            
            // Fill form fields
            document.getElementById('postalCode').value = formattedPostalCode;
            document.getElementById('prefecture').value = result.prefcode ? getPrefectureName(result.prefcode) : result.address1 || '';
            document.getElementById('city').value = result.address2 || '';
            document.getElementById('addressLine1').value = result.address3 || '';
            
            // Show success indicator
            if (postalCodeStatus) {
                if (spinner) spinner.style.display = 'none';
                if (checkIcon) checkIcon.style.display = 'inline-block';
                setTimeout(() => {
                    postalCodeStatus.style.display = 'none';
                }, 2000);
            }
            
        } else {
            // Postal code not found
            if (postalCodeStatus) {
                postalCodeStatus.style.display = 'none';
            }
            if (data.message) {
                KaruruUtils.showNotification(data.message, 'warning');
            } else {
                KaruruUtils.showNotification('郵便番号が見つかりませんでした', 'warning');
            }
        }
    } catch (error) {
        console.error('Error fetching address from postal code:', error);
        if (postalCodeStatus) {
            postalCodeStatus.style.display = 'none';
        }
    }
}

// Get prefecture name from prefecture code
function getPrefectureName(prefCode) {
    const prefectures = {
        '01': '北海道', '02': '青森県', '03': '岩手県', '04': '宮城県', '05': '秋田県',
        '06': '山形県', '07': '福島県', '08': '茨城県', '09': '栃木県', '10': '群馬県',
        '11': '埼玉県', '12': '千葉県', '13': '東京都', '14': '神奈川県', '15': '新潟県',
        '16': '富山県', '17': '石川県', '18': '福井県', '19': '山梨県', '20': '長野県',
        '21': '岐阜県', '22': '静岡県', '23': '愛知県', '24': '三重県', '25': '滋賀県',
        '26': '京都府', '27': '大阪府', '28': '兵庫県', '29': '奈良県', '30': '和歌山県',
        '31': '鳥取県', '32': '島根県', '33': '岡山県', '34': '広島県', '35': '山口県',
        '36': '徳島県', '37': '香川県', '38': '愛媛県', '39': '高知県', '40': '福岡県',
        '41': '佐賀県', '42': '長崎県', '43': '熊本県', '44': '大分県', '45': '宮崎県',
        '46': '鹿児島県', '47': '沖縄県'
    };
    
    const code = String(prefCode).padStart(2, '0');
    return prefectures[code] || '';
}

// Clear address fields
function clearAddressFields() {
    document.getElementById('prefecture').value = '';
    document.getElementById('city').value = '';
    document.getElementById('addressLine1').value = '';
}

async function loadAddresses() {
    const container = document.getElementById('addressesList');
    if (!container) return;
    
    try {
        // Show loading state
        container.innerHTML = `
            <div class="text-center py-3">
                <div class="spinner-border text-primary" role="status">
                    <span class="visually-hidden">読み込み中...</span>
                </div>
            </div>
        `;
        
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/CheckoutServlet?action=getUserAddresses`);
        
        // Handle different response formats
        let addresses = [];
        if (Array.isArray(data)) {
            addresses = data;
        } else if (data && typeof data === 'object') {
            addresses = data.addresses || data.data || (Array.isArray(data) ? data : []);
        }
        
        // Ensure addresses is an array
        if (!Array.isArray(addresses)) {
            addresses = [];
        }
        
        if (addresses.length > 0) {
            container.innerHTML = addresses.map((address, index) => {
                const isSelected = index === 0 || address.is_default;
                if (isSelected && !selectedAddressId) {
                    selectedAddressId = address.address_id;
                }
                
                return `
                    <div class="address-card ${isSelected ? 'selected' : ''}" 
                         data-address-id="${address.address_id}" 
                         onclick="selectAddress(${address.address_id})"
                         style="cursor: pointer; padding: 1rem; margin-bottom: 0.75rem; border: 2px solid ${isSelected ? 'var(--primary-color)' : 'var(--border-color)'}; border-radius: 8px; transition: all 0.3s;">
                        <div class="d-flex justify-content-between align-items-start">
                            <div class="flex-grow-1">
                                <h6 class="mb-2">
                                    <i class="bi bi-geo-alt-fill text-primary me-2"></i>
                                    ${escapeHtml(address.address_label || '住所')}
                                    ${address.is_default ? '<span class="badge bg-primary ms-2">デフォルト</span>' : ''}
                                </h6>
                                <p class="mb-1 fw-semibold">${escapeHtml(address.full_name || address.recipient_name || '')}</p>
                                <p class="text-muted mb-1">
                                    <i class="bi bi-telephone me-1"></i>${escapeHtml(address.phone || '')}
                                </p>
                                <p class="text-muted mb-1">
                                    <i class="bi bi-envelope me-1"></i>
                                    〒${escapeHtml(address.postal_code || '')} 
                                    ${escapeHtml(address.prefecture || '')} 
                                    ${escapeHtml(address.city || '')}
                                </p>
                                <p class="text-muted mb-0">
                                    ${escapeHtml(address.address_line || address.address_line1 || '')} 
                                    ${escapeHtml(address.address_line2 || '')}
                                    ${address.building || address.building_name ? escapeHtml(address.building || address.building_name) : ''}
                                </p>
                            </div>
                            <div class="ms-3">
                                <i class="bi bi-check-circle-fill text-primary" style="font-size: 1.5rem; ${isSelected ? '' : 'display: none;'}"></i>
                            </div>
                        </div>
                    </div>
                `;
            }).join('');
            
            // Enable place order button if address is selected
            const placeOrderBtn = document.getElementById('placeOrderBtn');
            if (placeOrderBtn && selectedAddressId) {
                placeOrderBtn.disabled = false;
                placeOrderBtn.classList.remove('disabled');
            }
        } else {
            // No addresses - show message and enable add button
            container.innerHTML = `
                <div class="alert alert-warning mb-3">
                    <i class="bi bi-exclamation-triangle me-2"></i>
                    <strong>住所が登録されていません</strong><br>
                    <small class="d-block mt-2">注文を完了するには、まず配送先住所を追加する必要があります。</small>
                    <button class="btn btn-primary btn-sm mt-2" data-bs-toggle="modal" data-bs-target="#addressModal">
                        <i class="bi bi-plus-circle me-1"></i>住所を追加
                    </button>
                </div>
            `;
            
            // Reset selected address
            selectedAddressId = null;
            
            // Enable add address button
            const addAddressBtn = document.getElementById('addAddressBtn');
            if (addAddressBtn) {
                addAddressBtn.disabled = false;
                addAddressBtn.classList.remove('disabled');
            }
            
            // Disable place order button if no address
            const placeOrderBtn = document.getElementById('placeOrderBtn');
            if (placeOrderBtn) {
                placeOrderBtn.disabled = true;
                placeOrderBtn.classList.add('disabled');
            }
        }
    } catch (error) {
        console.error('Error loading addresses:', error);
        container.innerHTML = `
            <div class="alert alert-danger mb-3">
                <i class="bi bi-exclamation-triangle me-2"></i>
                <strong>住所の読み込みに失敗しました</strong><br>
                <small>${error.message || 'エラーが発生しました。ページを再読み込みしてください。'}</small>
            </div>
            <button class="btn btn-outline-primary" onclick="loadAddresses()">
                <i class="bi bi-arrow-clockwise me-2"></i>再試行
            </button>
        `;
        
        // Still enable add address button even on error
        const addAddressBtn = document.getElementById('addAddressBtn');
        if (addAddressBtn) {
            addAddressBtn.disabled = false;
            addAddressBtn.classList.remove('disabled');
        }
    }
}

function selectAddress(addressId) {
    selectedAddressId = addressId;
    document.querySelectorAll('.address-card').forEach(card => {
        const cardId = parseInt(card.dataset.addressId);
        card.classList.remove('selected');
        card.style.borderColor = 'var(--border-color)';
        const checkIcon = card.querySelector('.bi-check-circle-fill');
        if (checkIcon) checkIcon.style.display = 'none';
        
        if (cardId === addressId) {
            card.classList.add('selected');
            card.style.borderColor = 'var(--primary-color)';
            if (checkIcon) checkIcon.style.display = 'block';
        }
    });
}

async function loadCartSummary() {
    try {
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/CheckoutServlet?action=getCheckoutData`);
        
        // Handle response format
        let items = [];
        let summaryData = {};
        
        if (data && data.items) {
            items = data.items;
            
            // Calculate discount from items if not provided in response
            let discount = data.discount || 0;
            if (discount === 0 && items && items.length > 0) {
                discount = items.reduce((sum, item) => {
                    const quantity = item.quantity || 1;
                    // If item has discount_percentage, calculate discount
                    if (item.discount_percentage && item.discount_percentage > 0 && item.original_price) {
                        const originalTotal = (item.original_price || item.price || 0) * quantity;
                        const discountedTotal = (item.price || 0) * quantity;
                        return sum + (originalTotal - discountedTotal);
                    }
                    // If item has original_price different from price, calculate difference
                    else if (item.original_price && item.original_price > (item.price || 0)) {
                        const originalTotal = item.original_price * quantity;
                        const discountedTotal = (item.price || 0) * quantity;
                        return sum + (originalTotal - discountedTotal);
                    }
                    return sum;
                }, 0);
            }
            
            // Use selected delivery method shipping cost
            const shippingCost = deliveryPrices[selectedDeliveryMethod] || (data.shipping || 500);
            const fee = Math.round((data.subtotal || 0) * 0.03);
            const total = (data.subtotal || 0) + shippingCost + fee - discount;
            
            summaryData = {
                subtotal: data.subtotal || 0,
                shipping: shippingCost,
                discount: discount,
                fee: fee,
                total: total
            };
        } else if (Array.isArray(data)) {
            items = data;
            const subtotal = items.reduce((sum, item) => sum + ((item.price || 0) * (item.quantity || 1)), 0);
            
            // Calculate discount from items if they have discount_percentage or original_price
            let discount = 0;
            if (items && items.length > 0) {
                discount = items.reduce((sum, item) => {
                    const quantity = item.quantity || 1;
                    // If item has discount_percentage, calculate discount
                    if (item.discount_percentage && item.discount_percentage > 0 && item.original_price) {
                        const originalTotal = (item.original_price || item.price || 0) * quantity;
                        const discountedTotal = (item.price || 0) * quantity;
                        return sum + (originalTotal - discountedTotal);
                    }
                    // If item has original_price different from price, calculate difference
                    else if (item.original_price && item.original_price > (item.price || 0)) {
                        const originalTotal = item.original_price * quantity;
                        const discountedTotal = (item.price || 0) * quantity;
                        return sum + (originalTotal - discountedTotal);
                    }
                    return sum;
                }, 0);
            }
            
            // Use selected delivery method shipping cost
            const shippingCost = items.length > 0 ? (deliveryPrices[selectedDeliveryMethod] || 500) : 0;
            const fee = Math.round(subtotal * 0.03);
            const total = subtotal + shippingCost + fee - discount;
            
            summaryData = {
                subtotal: subtotal,
                shipping: shippingCost,
                discount: discount,
                fee: fee,
                total: total
            };
        }
        
        if (items && items.length > 0) {
            // Debug: log discount calculation
            console.log('Checkout Summary:', summaryData);
            console.log('Items with discount info:', items.map(item => ({
                name: item.product_name,
                price: item.price,
                original_price: item.original_price,
                discount_percentage: item.discount_percentage,
                quantity: item.quantity
            })));
            
            renderOrderItems(items);
            updateSummary(summaryData);
        } else {
            // Redirect to cart if empty
            KaruruUtils.showNotification('カートが空です', 'warning');
            setTimeout(() => {
                window.location.href = `${window.CONTEXT_PATH}/cart.jsp`;
            }, 1500);
        }
    } catch (error) {
        console.error('Error loading cart summary:', error);
    }
}

function renderOrderItems(items) {
    const container = document.getElementById('orderItems');
    if (!container) return;
    
    container.innerHTML = items.map(item => {
        const imageUrl = item.image_url || '/img/default-product.png';
        const fullImageUrl = imageUrl.startsWith('/') ? window.CONTEXT_PATH + imageUrl : 
                            (imageUrl.startsWith('http') ? imageUrl : window.CONTEXT_PATH + '/' + imageUrl);
        
        return `
            <div class="d-flex align-items-center mb-3 pb-3 border-bottom" style="border-color: var(--border-color) !important;">
                <img src="${fullImageUrl}" 
                     alt="${escapeHtml(item.product_name || '商品')}" 
                     class="rounded me-3"
                     style="width: 80px; height: 80px; object-fit: cover;"
                     onerror="this.src='${window.CONTEXT_PATH}/img/default-product.png'">
                <div class="flex-grow-1">
                    <h6 class="mb-1">${escapeHtml(item.product_name || '商品名なし')}</h6>
                    <p class="text-muted mb-0 small">数量: ${item.quantity || 1}</p>
                </div>
                <div class="text-end">
                    <p class="text-primary fw-bold mb-0">${KaruruUtils.formatPrice((item.price || item.price_snapshot || 0) * (item.quantity || 1))}</p>
                </div>
            </div>
        `;
    }).join('');
}

function updateSummary(data) {
    const subtotal = data.subtotal || 0;
    const shipping = data.shipping || (subtotal > 0 ? 500 : 0);
    const discount = data.discount || 0;
    const total = data.total || (subtotal + shipping - discount);
    
    // Debug: log summary data
    console.log('Updating summary:', { subtotal, shipping, discount, total });
    
    const subtotalEl = document.getElementById('subtotal');
    const shippingEl = document.getElementById('shipping');
    const discountEl = document.getElementById('discount');
    const totalEl = document.getElementById('total');
    
    if (subtotalEl) subtotalEl.textContent = KaruruUtils.formatPrice(subtotal);
    if (shippingEl) shippingEl.textContent = KaruruUtils.formatPrice(shipping);
    if (discountEl) {
        discountEl.textContent = KaruruUtils.formatPrice(discount);
        // Always show discount row, but highlight if discount > 0
        const discountRow = discountEl.closest('.d-flex');
        if (discountRow && discount > 0) {
            discountRow.style.color = 'var(--success-color, #28a745)';
            discountEl.style.color = 'var(--success-color, #28a745)';
        }
    }
    if (totalEl) totalEl.textContent = KaruruUtils.formatPrice(total);
    
    // Disable place order button if no items
    const placeOrderBtn = document.getElementById('placeOrderBtn');
    if (placeOrderBtn) {
        if (subtotal === 0) {
            placeOrderBtn.disabled = true;
            placeOrderBtn.classList.add('disabled');
        } else {
            placeOrderBtn.disabled = false;
            placeOrderBtn.classList.remove('disabled');
        }
    }
}

async function saveAddress() {
    const form = document.getElementById('addressForm');
    if (!form || !form.checkValidity()) {
        form.reportValidity();
        return;
    }
    
    const addressData = {
        address_label: document.getElementById('addressLabel').value || '自宅',
        full_name: document.getElementById('recipientName').value,
        phone: document.getElementById('phone').value,
        postal_code: document.getElementById('postalCode').value,
        prefecture: document.getElementById('prefecture').value,
        city: document.getElementById('city').value,
        address_line: document.getElementById('addressLine1').value,
        address_line2: document.getElementById('addressLine2').value || '',
        building: document.getElementById('addressLine2').value || '',
        is_default: document.getElementById('isDefault').checked
    };
    
    try {
        KaruruUtils.showNotification('住所を保存しています...', 'info');
        
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/CheckoutServlet`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: new URLSearchParams({
                action: 'saveAddress',
                ...addressData
            })
        });
        
        if (data && data.success !== false) {
            // Close modal
            const modal = bootstrap.Modal.getInstance(document.getElementById('addressModal'));
            if (modal) modal.hide();
            
            // Reset form
            form.reset();
            
            // Reload addresses
            await loadAddresses();
        } else {
            throw new Error(data?.error || data?.message || 'Failed to save address');
        }
    } catch (error) {
        console.error('Error saving address:', error);
    }
}

const placeOrderBtn = document.getElementById('placeOrderBtn');
if (placeOrderBtn) {
    placeOrderBtn.addEventListener('click', async function() {
        const paymentMethod = document.querySelector('input[name="paymentMethod"]:checked');
        
        if (!paymentMethod) {
            return;
        }
        
        if (!selectedAddressId) {
            // Scroll to address section
            const addressSection = document.getElementById('addressesList');
            if (addressSection) {
                addressSection.scrollIntoView({ behavior: 'smooth', block: 'center' });
            }
            return;
        }
        
        if (!KaruruUtils.confirmDialog('注文を確定しますか？')) {
            return;
        }
        
        // Disable button and show loading
        placeOrderBtn.disabled = true;
        const originalText = placeOrderBtn.innerHTML;
        placeOrderBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>処理中...';
        
        try {
            // Step 1: Create order
            const orderData = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/CheckoutServlet`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded'
                },
                body: new URLSearchParams({
                    action: 'placeOrder',
                    address_id: selectedAddressId.toString(),
                    payment_method: paymentMethod.value,
                    delivery_method: selectedDeliveryMethod,
                    courier: selectedCourier
                })
            });
            
            if (!orderData || orderData.success === false) {
                throw new Error(orderData?.error || orderData?.message || '注文の作成に失敗しました');
            }
            
            const orderId = orderData.order_id || orderData.orderId;
            if (!orderId) {
                throw new Error('注文IDが取得できませんでした');
            }
            
            KaruruUtils.showNotification('注文を作成しました。支払いを処理しています...', 'info');
            
            // Step 2: Process payment
            try {
                const paymentData = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/Payment`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/x-www-form-urlencoded'
                    },
                    body: new URLSearchParams({
                        action: 'processPayment',
                        order_id: orderId.toString(),
                        payment_method: paymentMethod.value
                    })
                });
                
                if (paymentData && paymentData.success !== false) {
                    const paymentStatus = paymentData.payment_status || 'pending';
                    
                    if (paymentStatus === 'paid') {
                        KaruruUtils.showNotification('支払いが完了しました', 'success');
                        setTimeout(() => {
                            window.location.href = `${window.CONTEXT_PATH}/orderconfirm.jsp?id=${orderId}`;
                        }, 1200);
                    } else {
                        KaruruUtils.showNotification('注文が確定しました。支払い確認後、注文が確定されます。', 'info');
                        setTimeout(() => {
                            window.location.href = `${window.CONTEXT_PATH}/orderconfirm.jsp?id=${orderId}`;
                        }, 2000);
                    }
                } else {
                    const errMsg = paymentData?.error || paymentData?.message || '支払い処理に失敗しました。注文詳細ページから再度お支払いをお願いします。';
                    KaruruUtils.showNotification(errMsg, 'danger');
                    setTimeout(() => {
                        window.location.href = `${window.CONTEXT_PATH}/order-detail.jsp?id=${orderId}`;
                    }, 2500);
                }
            } catch (paymentError) {
                console.error('Payment processing error:', paymentError);
                const errMsg = paymentError?.message || '支払い処理に失敗しました。注文詳細ページから再度お支払いをお願いします。';
                KaruruUtils.showNotification(errMsg, 'danger');
                setTimeout(() => {
                    window.location.href = `${window.CONTEXT_PATH}/order-detail.jsp?id=${orderId}`;
                }, 2000);
            }
            
        } catch (error) {
            console.error('Error placing order:', error);
            
            // Restore button
            placeOrderBtn.disabled = false;
            placeOrderBtn.innerHTML = originalText;
        }
    });
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
