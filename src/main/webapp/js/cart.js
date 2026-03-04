// Cart page JavaScript

document.addEventListener('DOMContentLoaded', function() {
    // Initially hide cart container and show empty state until cart is loaded
    const cartContainer = document.getElementById('cartContainer');
    const emptyCart = document.getElementById('emptyCart');
    if (cartContainer) cartContainer.style.display = 'none';
    if (emptyCart) emptyCart.style.display = 'flex';
    
    loadCart();
});

async function loadCart() {
    try {
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/CartServlet?action=getCart`);
        
        // Handle both array response (old) and object response (new)
        let items = [];
        let summaryData = {};
        
        if (Array.isArray(data)) {
            // Old format - just array of items
            items = data;
            summaryData = {
                subtotal: items.reduce((sum, item) => sum + ((item.price_snapshot || 0) * (item.quantity || 1)), 0),
                shipping: items.length > 0 ? 500 : 0,
                total: 0
            };
            summaryData.total = summaryData.subtotal + summaryData.shipping;
        } else {
            // New format - object with items and summary
            items = KaruruUtils.extractData(data, 'items') || data.items || [];
            summaryData = {
                subtotal: data.subtotal || 0,
                shipping: data.shipping || (items.length > 0 ? 500 : 0),
                total: data.total || 0
            };
        }
        
        const cartContainer = document.getElementById('cartContainer');
        const emptyCart = document.getElementById('emptyCart');
        
        if (items && items.length > 0) {
            // Debug: Log first item to check stock_quantity
            if (items.length > 0) {
                console.log('Cart items loaded:', items.length);
                console.log('First item data:', items[0]);
                console.log('First item stock_quantity:', items[0].stock_quantity || items[0].stockQuantity);
            }
            renderCartItems(items);
            updateSummary(summaryData);
            if (cartContainer) cartContainer.style.display = 'block';
            if (emptyCart) emptyCart.style.display = 'none';
            
            // Enable checkout button
            const checkoutBtn = document.getElementById('checkoutBtn');
            if (checkoutBtn) {
                checkoutBtn.classList.remove('disabled');
                checkoutBtn.style.pointerEvents = 'auto';
            }
        } else {
            if (cartContainer) cartContainer.style.display = 'none';
            if (emptyCart) emptyCart.style.display = 'flex';
            
            // Disable checkout button
            const checkoutBtn = document.getElementById('checkoutBtn');
            if (checkoutBtn) {
                checkoutBtn.classList.add('disabled');
                checkoutBtn.style.pointerEvents = 'none';
                checkoutBtn.href = '#';
            }
        }
        
        // Always update cart badge after loading cart
        if (typeof window.updateCartCount === 'function') {
            await window.updateCartCount();
        }
    } catch (error) {
        console.error('Error loading cart:', error);
        const emptyCart = document.getElementById('emptyCart');
        if (emptyCart) {
            emptyCart.style.display = 'flex';
            emptyCart.innerHTML = `
                <div class="empty-state-content">
                    <i class="bi bi-cart-x empty-state-icon"></i>
                    <h3 class="empty-state-title">カートは空です</h3>
                    <p class="empty-state-description">商品を追加してカートを満たしましょう</p>
                    <a href="${window.CONTEXT_PATH}/products.jsp" class="btn btn-primary btn-lg empty-state-button">
                        <i class="bi bi-bag me-2"></i>商品を見る
                    </a>
                </div>
            `;
        }
        
        // Update cart badge even on error (to clear stale count)
        if (typeof window.updateCartCount === 'function') {
            await window.updateCartCount();
        }
    }
}

function renderEmptyCart() {
    const emptyCart = document.getElementById('emptyCart');
    if (emptyCart) {
        emptyCart.style.display = 'flex';
        emptyCart.innerHTML = `
                <div class="empty-state-content">
                    <i class="bi bi-cart-x empty-state-icon"></i>
                    <h3 class="empty-state-title">カートは空です</h3>
                    <p class="empty-state-description">商品を追加してカートを満たしましょう</p>
                    <a href="${window.CONTEXT_PATH}/products.jsp" class="btn btn-primary btn-lg empty-state-button">
                        <i class="bi bi-bag me-2"></i>商品を見る
                    </a>
                </div>
            `;
    }
}

// Legacy function for backward compatibility
function showEmptyCart() {
    renderEmptyCart();
}

function renderCartItems(items) {
    const container = document.getElementById('cartItems');
    if (!container) return;
    
    container.innerHTML = items.map(item => {
        const imageUrl = item.image_url || '/img/default-product.png';
        const fullImageUrl = imageUrl.startsWith('/') ? window.CONTEXT_PATH + imageUrl : 
                            (imageUrl.startsWith('http') ? imageUrl : window.CONTEXT_PATH + '/' + imageUrl);
        const cartId = item.cart_id || item.cartId;
        const productId = item.product_id || item.productId;
        const quantity = parseInt(item.quantity) || 1;
        // Get stock quantity - check multiple possible field names
        const stockQuantity = parseInt(item.stock_quantity || item.stockQuantity || item.stock_quantity || 999);
        const maxQuantity = Math.max(1, stockQuantity); // Ensure at least 1
        
        // Debug logging
        console.log(`Cart Item ${cartId}: quantity=${quantity}, stock=${stockQuantity}, max=${maxQuantity}`);
        
        return `
            <div class="card card-light mb-3 cart-item" data-cart-id="${cartId}" data-product-id="${productId}" data-stock="${stockQuantity}">
                <div class="card-body">
                    <div class="row align-items-center">
                        <div class="col-md-2">
                            <img src="${fullImageUrl}" 
                                 alt="${escapeHtml(item.product_name || '商品')}" 
                                 class="img-fluid rounded"
                                 style="max-height: 100px; object-fit: cover;"
                                 onerror="this.src='${window.CONTEXT_PATH}/img/default-product.png'">
                        </div>
                        <div class="col-md-4">
                            <h6 class="mb-1">
                                <a href="${window.CONTEXT_PATH}/product-detail.jsp?id=${productId}" class="text-decoration-none" style="color: var(--text-color);">
                                    ${escapeHtml(item.product_name || '商品名なし')}
                                </a>
                            </h6>
                            <p class="text-primary fw-bold mb-0">${KaruruUtils.formatPrice(item.price_snapshot || 0)}</p>
                            ${stockQuantity < 999 ? `<small class="text-muted d-block mt-1"><i class="bi bi-box-seam me-1"></i>在庫: ${stockQuantity}個</small>` : ''}
                        </div>
                        <div class="col-md-3">
                            <div class="input-group">
                                <button class="btn btn-outline-secondary" onclick="decreaseQuantity(${cartId})">-</button>
                                <input type="number" class="form-control text-center form-control-light" 
                                       value="${quantity}" 
                                       min="1"
                                       max="${maxQuantity}"
                                       data-cart-id="${cartId}"
                                       data-stock="${stockQuantity}"
                                       onchange="handleQuantityChange(${cartId}, this.value)"
                                       oninput="validateQuantityInput(this)">
                                <button class="btn btn-outline-secondary" onclick="increaseQuantity(${cartId})">+</button>
                            </div>
                            ${quantity > stockQuantity ? `<small class="text-danger d-block mt-1"><i class="bi bi-exclamation-triangle me-1"></i>在庫不足</small>` : ''}
                        </div>
                        <div class="col-md-2 text-end">
                            <p class="text-primary fw-bold fs-5 mb-2">${KaruruUtils.formatPrice((item.price_snapshot || 0) * quantity)}</p>
                        </div>
                        <div class="col-md-1 text-end">
                            <button class="btn btn-outline-danger btn-sm" onclick="confirmRemoveFromCart(${cartId})" title="削除">
                                <i class="bi bi-trash"></i>
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }).join('');
}

function updateSummary(data) {
    const subtotalEl = document.getElementById('subtotal');
    const shippingEl = document.getElementById('shipping');
    const totalEl = document.getElementById('total');
    const emptyCartMessage = document.getElementById('emptyCartMessage');
    
    const subtotal = data.subtotal || 0;
    const shipping = data.shipping || 0;
    const total = data.total || (subtotal + shipping);
    
    if (subtotalEl) {
        subtotalEl.textContent = KaruruUtils.formatPrice(subtotal);
    }
    if (shippingEl) {
        shippingEl.textContent = KaruruUtils.formatPrice(shipping);
    }
    if (totalEl) {
        totalEl.textContent = KaruruUtils.formatPrice(total);
    }
    
    // Show/hide empty cart message
    if (emptyCartMessage) {
        if (subtotal === 0) {
            emptyCartMessage.style.display = 'block';
        } else {
            emptyCartMessage.style.display = 'none';
        }
    }
}

// Decrease quantity
function decreaseQuantity(cartId) {
    const cartItem = document.querySelector(`.cart-item[data-cart-id="${cartId}"]`);
    if (!cartItem) return;
    
    const quantityInput = cartItem.querySelector('input[type="number"]');
    if (!quantityInput) return;
    
    const currentQuantity = parseInt(quantityInput.value) || 1;
    const newQuantity = Math.max(1, currentQuantity - 1);
    
    updateQuantity(cartId, newQuantity);
}

// Increase quantity
function increaseQuantity(cartId) {
    const cartItem = document.querySelector(`.cart-item[data-cart-id="${cartId}"]`);
    if (!cartItem) return;
    
    const quantityInput = cartItem.querySelector('input[type="number"]');
    if (!quantityInput) return;
    
    const stockQuantity = parseInt(cartItem.dataset.stock || quantityInput.dataset.stock || 999);
    const currentQuantity = parseInt(quantityInput.value) || 1;
    const newQuantity = Math.min(stockQuantity, currentQuantity + 1);
    
    console.log(`Increase quantity: cartId=${cartId}, current=${currentQuantity}, stock=${stockQuantity}, new=${newQuantity}`);
    
    if (newQuantity > stockQuantity) {
        if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
            KaruruUtils.showNotification(`在庫が不足しています。最大${stockQuantity}個まで選択できます。`, 'warning');
        }
        return;
    }
    
    updateQuantity(cartId, newQuantity);
}

// Validate quantity input in real-time
function validateQuantityInput(input) {
    const cartId = parseInt(input.dataset.cartId);
    const cartItem = document.querySelector(`.cart-item[data-cart-id="${cartId}"]`);
    const stockQuantity = parseInt(cartItem?.dataset.stock || input.dataset.stock || 999);
    let value = parseInt(input.value) || 1;
    
    console.log(`Validate input: cartId=${cartId}, value=${value}, stock=${stockQuantity}`);
    
    // Ensure minimum is 1
    if (value < 1) {
        value = 1;
        input.value = 1;
    }
    
    // Ensure maximum is stock quantity
    if (value > stockQuantity) {
        value = stockQuantity;
        input.value = stockQuantity;
        if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
            KaruruUtils.showNotification(`在庫が不足しています。最大${stockQuantity}個まで選択できます。`, 'warning');
        }
    }
}

// Handle quantity change from input field
function handleQuantityChange(cartId, value) {
    const quantity = parseInt(value) || 1;
    
    // If quantity is 0 or less, set to 1 (don't auto-delete)
    if (quantity < 1) {
        const cartItem = document.querySelector(`.cart-item[data-cart-id="${cartId}"]`);
        const quantityInput = cartItem?.querySelector('input[type="number"]');
        if (quantityInput) {
            quantityInput.value = 1;
        }
        if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
            KaruruUtils.showNotification('数量は1個以上である必要があります。削除する場合は削除ボタンを使用してください。', 'info');
        }
        return;
    }
    
    // Update quantity
    updateQuantity(cartId, quantity);
}

async function updateQuantity(cartId, quantity) {
    // Ensure minimum quantity is 1
    if (quantity < 1) {
        quantity = 1;
    }
    
    // Find the cart item
    const cartItem = document.querySelector(`.cart-item[data-cart-id="${cartId}"]`);
    if (!cartItem) {
        console.error('Cart item not found for cartId:', cartId);
        return;
    }
    
    const quantityInput = cartItem.querySelector('input[type="number"]');
    const stockQuantity = parseInt(cartItem.dataset.stock || quantityInput?.dataset.stock || 999);
    
    console.log(`Update quantity: cartId=${cartId}, requested=${quantity}, stock=${stockQuantity}`);
    
    // Validate against stock
    if (quantity > stockQuantity) {
        console.warn(`Quantity ${quantity} exceeds stock ${stockQuantity}, limiting to ${stockQuantity}`);
        quantity = stockQuantity;
        if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
            KaruruUtils.showNotification(`在庫が不足しています。最大${stockQuantity}個まで選択できます。`, 'warning');
        }
    }
    
    // Show loading state
    if (quantityInput) {
        quantityInput.disabled = true;
    }
    
    try {
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/CartServlet`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: new URLSearchParams({
                action: 'update',
                cartId: cartId.toString(),
                quantity: quantity.toString()
            })
        });
        
        if (data.success !== false) {
            // Update quantity display
            if (cartItem && quantityInput) {
                quantityInput.value = quantity;
                quantityInput.disabled = false;
                
                // Update max attribute and data-stock if stock changed
                if (stockQuantity < 999) {
                    quantityInput.max = stockQuantity;
                    cartItem.dataset.stock = stockQuantity;
                    quantityInput.dataset.stock = stockQuantity;
                }
                
                // Get unit price
                const priceText = cartItem.querySelector('.text-primary.fw-bold.mb-0')?.textContent || '¥0';
                const unitPrice = parseFloat(priceText.replace(/[¥,]/g, '')) || 0;
                const totalPrice = unitPrice * quantity;
                
                // Update total price for this item
                const totalPriceEl = cartItem.querySelector('.text-primary.fw-bold.fs-5');
                if (totalPriceEl) {
                    totalPriceEl.textContent = KaruruUtils.formatPrice(totalPrice);
                }
                
                // Recalculate summary
                recalculateSummary();
                
                // Update cart badge
                if (typeof window.updateCartCount === 'function') {
                    await window.updateCartCount();
                }
            } else {
                // Fallback: reload cart if element not found
                loadCart();
            }
        } else {
            const errorMessage = data.error || data.message || 'Failed to update quantity';
            console.error('Server error updating quantity:', errorMessage);
            throw new Error(errorMessage);
        }
    } catch (error) {
        console.error('Error updating quantity:', error);
        
        // Restore original value on error
        if (quantityInput) {
            quantityInput.disabled = false;
        }
        
        // Reload cart to get latest stock and quantity
        await loadCart();
        
        if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
            const errorMessage = error.message || '数量の更新に失敗しました';
            // Check if error is about stock
            if (errorMessage.includes('在庫') || errorMessage.includes('stock')) {
                KaruruUtils.showNotification(errorMessage, 'warning');
            }
        }
    }
}

// Confirm before removing from cart
function confirmRemoveFromCart(cartId) {
    const cartItem = document.querySelector(`.cart-item[data-cart-id="${cartId}"]`);
    if (!cartItem) return;
    
    const productName = cartItem.querySelector('h6 a')?.textContent || 'この商品';
    
    if (confirm(`${productName}をカートから削除しますか？`)) {
        removeFromCart(cartId);
    }
}

async function removeFromCart(cartId) {
    // Find the cart item element
    const cartItem = document.querySelector(`.cart-item[data-cart-id="${cartId}"]`);
    
    // Show loading state on button
    if (cartItem) {
        const deleteBtn = cartItem.querySelector('button[onclick*="confirmRemoveFromCart"]');
        if (deleteBtn) {
            deleteBtn.disabled = true;
            deleteBtn.innerHTML = '<i class="bi bi-hourglass-split"></i>';
        }
    }
    
    try {
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/CartServlet`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: new URLSearchParams({
                action: 'remove',
                cartId: cartId.toString()
            })
        });
        
        if (data.success !== false) {
            // Animate removal
            if (cartItem) {
                cartItem.style.transition = 'opacity 0.3s ease-out, transform 0.3s ease-out';
                cartItem.style.opacity = '0';
                cartItem.style.transform = 'translateX(-20px)';
                
                setTimeout(() => {
                    cartItem.remove();
                    
                    // Check if cart is empty
                    const remainingItems = document.querySelectorAll('.cart-item');
                    if (remainingItems.length === 0) {
                        // Show empty cart
                        const cartContainer = document.getElementById('cartContainer');
                        const emptyCart = document.getElementById('emptyCart');
                        if (cartContainer) cartContainer.style.display = 'none';
                        if (emptyCart) emptyCart.style.display = 'flex';
                        
                        // Disable checkout button
                        const checkoutBtn = document.getElementById('checkoutBtn');
                        if (checkoutBtn) {
                            checkoutBtn.classList.add('disabled');
                            checkoutBtn.style.pointerEvents = 'none';
                            checkoutBtn.href = '#';
                        }
                        
                        // Reset summary
                        updateSummary({ subtotal: 0, shipping: 0, total: 0 });
                    } else {
                        // Recalculate summary
                        recalculateSummary();
                    }
                }, 300);
            } else {
                // Fallback: reload cart if element not found
                loadCart();
            }
            
            // Update cart badge counter
            if (typeof window.updateCartCount === 'function') {
                await window.updateCartCount();
            }
            
            if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
                KaruruUtils.showNotification('カートから削除しました', 'success');
            }
        } else {
            throw new Error(data.error || data.message || 'Failed to remove from cart');
        }
    } catch (error) {
        console.error('Error removing from cart:', error);
        if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
            KaruruUtils.showNotification(error.message || 'カートから削除できませんでした', 'danger');
        }
        
        // Restore button if error
        if (cartItem) {
            const deleteBtn = cartItem.querySelector('button[onclick*="confirmRemoveFromCart"]');
            if (deleteBtn) {
                deleteBtn.disabled = false;
                deleteBtn.innerHTML = '<i class="bi bi-trash"></i>';
            }
        }
        
    }
}

// Recalculate summary from current cart items
function recalculateSummary() {
    const cartItems = document.querySelectorAll('.cart-item');
    let subtotal = 0;
    
    cartItems.forEach(item => {
        // Get unit price
        const unitPriceText = item.querySelector('.text-primary.fw-bold.mb-0')?.textContent || '¥0';
        const unitPrice = parseFloat(unitPriceText.replace(/[¥,]/g, '')) || 0;
        
        // Get quantity
        const quantityInput = item.querySelector('input[type="number"]');
        const quantity = parseInt(quantityInput?.value || 1);
        
        // Calculate item total
        const itemTotal = unitPrice * quantity;
        subtotal += itemTotal;
    });
    
    const shipping = cartItems.length > 0 ? 500 : 0;
    const total = subtotal + shipping;
    
    updateSummary({ subtotal, shipping, total });
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
