// Order confirmation (orderconfirm.jsp?id=N), shown after checkout.

document.addEventListener('DOMContentLoaded', async () => {
    const orderId = parseInt(new URLSearchParams(location.search).get('id'), 10);
    const summary = document.getElementById('orderSummary');
    if (!orderId) {
        summary.innerHTML = KaruruUtils.emptyState('bi-question-circle', '注文が指定されていません');
        return;
    }
    let order;
    try {
        order = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/OrderServlet?action=getOrderDetails&order_id=${orderId}`);
    } catch (error) {
        summary.innerHTML = KaruruUtils.emptyState('bi-exclamation-circle', error.message);
        return;
    }
    document.getElementById('orderDetailLink').href = `${window.CONTEXT_PATH}/order-detail.jsp?id=${order.order_id}`;
    document.getElementById('confirmNote').textContent = order.payment_status === 'paid'
        ? 'お支払いが完了しました。出品者が発送の準備を始めます。'
        : 'お支払いの確認が取れ次第、出品者が発送の準備を始めます。';
    summary.innerHTML = KaruruUtils.orderSummary(order);
});
