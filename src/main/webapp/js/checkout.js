// Checkout (checkout.jsp). All amounts come from CheckoutServlet, so the page shows exactly what
// placeOrder will charge.

const PREFECTURES = ['北海道', '青森県', '岩手県', '宮城県', '秋田県', '山形県', '福島県', '茨城県', '栃木県', '群馬県',
    '埼玉県', '千葉県', '東京都', '神奈川県', '新潟県', '富山県', '石川県', '福井県', '山梨県', '長野県', '岐阜県',
    '静岡県', '愛知県', '三重県', '滋賀県', '京都府', '大阪府', '兵庫県', '奈良県', '和歌山県', '鳥取県', '島根県',
    '岡山県', '広島県', '山口県', '徳島県', '香川県', '愛媛県', '高知県', '福岡県', '佐賀県', '長崎県', '熊本県',
    '大分県', '宮崎県', '鹿児島県', '沖縄県'];

const form = document.getElementById('checkoutForm');

document.addEventListener('DOMContentLoaded', () => {
    loadAddresses();
    loadQuote();
    form.addEventListener('change', event => {
        if (event.target.name === 'delivery_method') loadQuote();
    });
    form.addEventListener('submit', placeOrder);
    document.getElementById('addressForm').addEventListener('submit', saveAddress);
    document.getElementById('postalCode').addEventListener('input', event => {
        const digits = event.target.value.replace(/\D/g, '');
        if (digits.length === 7) fillAddressFromPostalCode(digits);
    });
});

function postForm(url, params) {
    return KaruruUtils.apiFetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams(params)
    });
}

async function loadAddresses(selectId) {
    const list = document.getElementById('addressesList');
    let addresses;
    try {
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/CheckoutServlet?action=getUserAddresses`);
        addresses = data.addresses;
    } catch (error) {
        list.innerHTML = `<p class="text-danger">${escapeHtml(error.message)}</p>`;
        return;
    }
    if (!addresses.length) {
        list.innerHTML = '<p class="text-muted mb-0">お届け先が登録されていません。「住所を追加」から登録してください。</p>';
        return;
    }
    const selected = selectId || (addresses.find(a => a.is_default) || addresses[0]).address_id;
    list.innerHTML = addresses.map(address => `
        <label class="choice">
            <input class="form-check-input" type="radio" name="address_id" value="${address.address_id}"
                   ${address.address_id === selected ? 'checked' : ''} required>
            <span class="choice-body">
                <strong>${escapeHtml(address.full_name)}${address.address_label ? `<span class="text-muted fw-normal">（${escapeHtml(address.address_label)}）</span>` : ''}</strong>
                <small>〒${escapeHtml(address.postal_code)} ${escapeHtml(address.prefecture)}${escapeHtml(address.city)}${escapeHtml(address.address_line)} ${escapeHtml(address.building || '')}</small>
                <small>${escapeHtml(address.phone)}</small>
            </span>
        </label>`).join('');
}

async function loadQuote() {
    const method = form.delivery_method.value;
    let quote;
    try {
        quote = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/CheckoutServlet?action=getCheckoutData&delivery_method=${method}`);
    } catch (error) {
        KaruruUtils.showNotification(error.message, 'danger');
        return;
    }
    if (!quote.items.length) {
        window.location.href = `${window.CONTEXT_PATH}/cart.jsp`;
        return;
    }
    document.getElementById('orderItems').innerHTML = quote.items.map(item => `
        <li class="line-item">
            <span class="line-item-image">
                <img src="${KaruruUtils.resolveProductImageUrl(item.image_url)}" alt="" onerror="KaruruUtils.imageFallback(this)">
            </span>
            <span class="line-item-body">
                <span class="line-item-name">${escapeHtml(item.product_name)}</span>
                <span class="line-item-meta">数量 ${item.quantity}</span>
            </span>
            <span class="line-item-price price">${KaruruUtils.formatPrice(item.subtotal)}</span>
        </li>`).join('');
    for (const key of ['subtotal', 'shipping', 'fee', 'total']) {
        document.getElementById(key).textContent = KaruruUtils.formatPrice(quote[key]);
    }
    document.getElementById('savings').textContent =
        quote.discount > 0 ? `${KaruruUtils.formatPrice(quote.discount)}の割引が適用されています` : '';
    document.getElementById('placeOrderBtn').disabled = false;
    if (quote.warning) KaruruUtils.showNotification(quote.warning, 'warning');
}

async function placeOrder(event) {
    event.preventDefault();
    if (!form.querySelector('input[name="address_id"]:checked')) {
        KaruruUtils.showNotification('お届け先を選んでください', 'warning');
        document.getElementById('addressTitle').scrollIntoView({ behavior: 'smooth' });
        return;
    }
    const button = document.getElementById('placeOrderBtn');
    button.disabled = true;
    const params = Object.fromEntries(new FormData(form));
    let orderId;
    try {
        const order = await postForm(`${window.CONTEXT_PATH}/CheckoutServlet`, { action: 'placeOrder', ...params });
        orderId = order.order_id;
    } catch (error) {
        KaruruUtils.showNotification(error.message, 'danger');
        button.disabled = false;
        return;
    }
    // The order exists from here on; a failed payment can be retried from the order page.
    try {
        await postForm(`${window.CONTEXT_PATH}/Payment`, {
            action: 'processPayment', order_id: orderId, payment_method: params.payment_method
        });
        window.location.href = `${window.CONTEXT_PATH}/orderconfirm.jsp?id=${orderId}`;
    } catch (error) {
        KaruruUtils.showNotification(`注文は作成されましたが、支払いに失敗しました：${error.message}`, 'danger');
        setTimeout(() => { window.location.href = `${window.CONTEXT_PATH}/order-detail.jsp?id=${orderId}`; }, 2500);
    }
}

async function saveAddress(event) {
    event.preventDefault();
    const addressForm = event.target;
    if (!addressForm.reportValidity()) return;
    const params = Object.fromEntries(new FormData(addressForm));
    params.is_default = addressForm.is_default.checked ? 'true' : 'false';
    try {
        const data = await postForm(`${window.CONTEXT_PATH}/CheckoutServlet`, { action: 'saveAddress', ...params });
        bootstrap.Modal.getInstance(document.getElementById('addressModal')).hide();
        addressForm.reset();
        loadAddresses(data.address_id);
    } catch (error) {
        KaruruUtils.showNotification(error.message, 'danger');
    }
}

/** Fills prefecture/city/town from the free zipcloud postal code API. */
async function fillAddressFromPostalCode(digits) {
    const status = document.getElementById('postalStatus');
    status.textContent = '住所を検索しています…';
    try {
        const response = await fetch(`https://zipcloud.ibsnet.co.jp/api/search?zipcode=${digits}`);
        const data = await response.json();
        const result = data.results && data.results[0];
        if (!result) {
            status.textContent = 'この郵便番号の住所は見つかりませんでした';
            return;
        }
        document.getElementById('postalCode').value = `${digits.slice(0, 3)}-${digits.slice(3)}`;
        document.getElementById('prefecture').value = PREFECTURES[Number(result.prefcode) - 1] || result.address1;
        document.getElementById('city').value = result.address2;
        document.getElementById('addressLine1').value = result.address3;
        document.getElementById('addressLine1').focus();
        status.textContent = '住所を補完しました。番地を入力してください';
    } catch (error) {
        status.textContent = '住所を自動で補完できませんでした。手入力してください';
    }
}
