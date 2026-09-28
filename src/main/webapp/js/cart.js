// Cart page (cart.jsp).

const MAX_QUANTITY_CHOICES = 10;

document.addEventListener('DOMContentLoaded', () => {
    loadCart();
    const list = document.getElementById('cartItems');
    list.addEventListener('change', event => {
        if (event.target.matches('select[data-cart-id]')) {
            updateQuantity(event.target.dataset.cartId, event.target.value);
        }
    });
    list.addEventListener('click', event => {
        const button = event.target.closest('button[data-remove]');
        if (button) removeItem(button.dataset.remove);
    });
});

async function loadCart() {
    let cart;
    try {
        cart = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/CartServlet?action=getCart`);
    } catch (error) {
        KaruruUtils.showNotification(error.message, 'danger');
        return;
    }
    const hasItems = cart.items.length > 0;
    document.getElementById('cartContainer').hidden = !hasItems;
    document.getElementById('emptyCart').hidden = hasItems;
    document.getElementById('cartItems').innerHTML = cart.items.map(renderItem).join('');
    document.getElementById('subtotal').textContent = KaruruUtils.formatPrice(cart.subtotal);
    document.getElementById('shipping').textContent = KaruruUtils.formatPrice(cart.shipping);
    document.getElementById('total').textContent = KaruruUtils.formatPrice(cart.total);
    window.updateCartCount();
}

function renderItem(item) {
    const choices = Math.max(item.quantity, Math.min(item.stock_quantity, MAX_QUANTITY_CHOICES));
    const unavailable = item.status !== 'available';
    const productUrl = `${window.CONTEXT_PATH}/product-detail.jsp?id=${item.product_id}`;
    return `
        <li class="line-item">
            <a href="${productUrl}" class="line-item-image">
                <img src="${KaruruUtils.resolveProductImageUrl(item.image_url)}" alt="" onerror="KaruruUtils.imageFallback(this)">
            </a>
            <div class="line-item-body">
                <a class="line-item-name" href="${productUrl}">${escapeHtml(item.product_name)}</a>
                <span class="line-item-meta">${escapeHtml(item.seller_name || '')}</span>
                ${unavailable ? '<span class="status status-danger">現在購入できません</span>' : ''}
                <div class="line-item-controls">
                    <label class="visually-hidden" for="qty-${item.cart_id}">数量</label>
                    <select class="form-select form-select-sm" id="qty-${item.cart_id}" data-cart-id="${item.cart_id}">
                        ${Array.from({ length: choices }, (_, i) => i + 1)
                            .map(n => `<option value="${n}"${n === item.quantity ? ' selected' : ''}>${n}</option>`).join('')}
                    </select>
                    <button type="button" class="btn btn-link btn-sm" data-remove="${item.cart_id}">削除</button>
                </div>
            </div>
            <div class="line-item-price price">${KaruruUtils.formatPrice(item.subtotal)}</div>
        </li>`;
}

async function cartAction(params) {
    try {
        await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/CartServlet`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams(params)
        });
    } catch (error) {
        KaruruUtils.showNotification(error.message, 'danger');
    }
    loadCart();
}

function updateQuantity(cartId, quantity) {
    cartAction({ action: 'update', cartId, quantity });
}

function removeItem(cartId) {
    cartAction({ action: 'remove', cartId });
}
