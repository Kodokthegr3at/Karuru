// One order (order-detail.jsp?id=N), seen by its buyer or by a seller of one of its items.
// The server decides which status changes are allowed; this page only offers those.

const orderId = parseInt(new URLSearchParams(location.search).get('id'), 10);
const STEPS = ['pending', 'confirmed', 'processing', 'shipped', 'delivered'];

document.addEventListener('DOMContentLoaded', () => {
    loadOrder();
    document.getElementById('shipForm').addEventListener('submit', event => {
        event.preventDefault();
        const form = event.target;
        changeStatus('shipped', { tracking_number: form.tracking_number.value, courier: form.courier.value })
            .then(done => { if (done) bootstrap.Modal.getInstance(document.getElementById('shipModal')).hide(); });
    });
});

async function loadOrder() {
    const container = document.getElementById('orderDetail');
    let order;
    try {
        order = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/OrderServlet?action=getOrderDetails&order_id=${orderId}`);
    } catch (error) {
        container.innerHTML = KaruruUtils.emptyState('bi-exclamation-circle', error.message);
        return;
    }
    const isBuyer = Number(order.user_id) === Number(window.currentUserId);
    const isSeller = order.items.some(item => Number(item.seller_id) === Number(window.currentUserId));
    document.getElementById('orderStatus').innerHTML = `
        ${KaruruUtils.statusBadge(KaruruUtils.ORDER_STATUS, order.order_status)}
        ${KaruruUtils.statusBadge(KaruruUtils.PAYMENT_STATUS, order.payment_status)}`;

    const address = order.shipping_address || {};
    container.innerHTML = `
        ${order.order_status !== 'cancelled' ? renderSteps(order.order_status) : ''}
        <div class="checkout-layout">
            <div>${KaruruUtils.orderSummary(order)}</div>
            <aside class="side-panels">
                ${renderActions(order, isBuyer, isSeller)}
                <section class="panel">
                    <h2 class="h6">お届け先</h2>
                    <p class="mb-0">${escapeHtml(address.recipient_name || '')}<br>
                        〒${escapeHtml(address.postal_code || '')} ${escapeHtml(address.prefecture || '')}${escapeHtml(address.city || '')}${escapeHtml(address.address_line1 || '')}
                        ${escapeHtml(address.address_line2 || '')} ${escapeHtml(address.building_name || '')}<br>
                        ${escapeHtml(address.phone || '')}</p>
                </section>
                <section class="panel">
                    <h2 class="h6">配送</h2>
                    <p class="mb-0">${escapeHtml(order.notes || '指定なし')}</p>
                    ${order.tracking_number ? `<p class="mb-0 mt-2">追跡番号：${escapeHtml(order.courier || '')} ${escapeHtml(order.tracking_number)}</p>` : ''}
                </section>
            </aside>
        </div>`;
    container.querySelectorAll('[data-status]').forEach(button =>
        button.addEventListener('click', () => changeStatus(button.dataset.status, {}, button.dataset.confirm)));
    container.querySelector('[data-pay]')?.addEventListener('click', event => pay(order, event.currentTarget));
    container.querySelector('[data-confirm-order]')?.addEventListener('click', event => confirmOrder(event.currentTarget));
}

function renderSteps(status) {
    const current = STEPS.indexOf(status);
    return `
        <ol class="steps" aria-label="注文の進み具合">
            ${STEPS.map((step, index) => `
                <li class="${index < current ? 'done' : index === current ? 'current' : ''}"${index === current ? ' aria-current="step"' : ''}>
                    ${KaruruUtils.ORDER_STATUS[step][0]}
                </li>`).join('')}
        </ol>`;
}

function renderActions(order, isBuyer, isSeller) {
    const buttons = [];
    const status = order.order_status;
    if (isBuyer && status === 'pending' && order.payment_status === 'pending') {
        buttons.push('<button class="btn btn-primary" type="button" data-pay>支払う</button>');
    }
    if (isBuyer && status === 'pending') {
        buttons.push('<button class="btn btn-outline-danger" type="button" data-status="cancelled" data-confirm="この注文をキャンセルしますか？">注文をキャンセル</button>');
    }
    if (isSeller) {
        if (status === 'pending' && order.payment_status === 'paid') {
            buttons.push('<button class="btn btn-primary" type="button" data-confirm-order>注文を受け付ける</button>');
        }
        if (status === 'confirmed') buttons.push('<button class="btn btn-primary" type="button" data-status="processing">発送の準備を始める</button>');
        if (status === 'processing') buttons.push('<button class="btn btn-primary" type="button" data-bs-toggle="modal" data-bs-target="#shipModal">発送した</button>');
        if (status === 'shipped') buttons.push('<button class="btn btn-primary" type="button" data-status="delivered">お届け完了にする</button>');
        if (['pending', 'confirmed', 'processing'].includes(status) && !isBuyer) {
            buttons.push('<button class="btn btn-outline-danger" type="button" data-status="cancelled" data-confirm="この注文をキャンセルしますか？支払い済みの代金は購入者に返金されます。">注文をキャンセル</button>');
        }
    }
    const contact = isBuyer && order.items[0]
        ? `<a class="btn btn-link" href="${window.CONTEXT_PATH}/messages.jsp?user_id=${order.items[0].seller_id}&order_id=${order.order_id}&type=order">出品者に連絡</a>`
        : '';
    if (!buttons.length && !contact) return '';
    return `<section class="panel order-actions">${buttons.join('')}${contact}</section>`;
}

function postForm(url, params) {
    return KaruruUtils.apiFetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams(params)
    });
}

async function run(button, task, successMessage) {
    if (button) button.disabled = true;
    try {
        await task();
        KaruruUtils.showNotification(successMessage, 'success');
        loadOrder();
        return true;
    } catch (error) {
        KaruruUtils.showNotification(error.message, 'danger');
        if (button) button.disabled = false;
        return false;
    }
}

function changeStatus(status, extra, question) {
    if (question && !KaruruUtils.confirmDialog(question)) return Promise.resolve(false);
    return run(null, () => postForm(`${window.CONTEXT_PATH}/OrderServlet`,
        { action: 'updateOrderStatus', order_id: orderId, status, ...extra }), '注文の状態を更新しました');
}

function confirmOrder(button) {
    return run(button, () => postForm(`${window.CONTEXT_PATH}/OrderServlet`,
        { action: 'confirmOrder', order_id: orderId }), '注文を受け付けました');
}

function pay(order, button) {
    return run(button, () => postForm(`${window.CONTEXT_PATH}/Payment`,
        { action: 'processPayment', order_id: orderId, payment_method: order.payment_method }), 'お支払いが完了しました');
}
