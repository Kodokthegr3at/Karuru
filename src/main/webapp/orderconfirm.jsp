<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="includes/header.jsp" %>
<%
    if (currentUser == null) {
        response.sendRedirect("login.jsp?redirect=orderconfirm");
        return;
    }
    
    // Get order_id from parameter
    String orderIdParam = request.getParameter("id");
    Integer orderId = null;
    if (orderIdParam != null && !orderIdParam.isEmpty()) {
        try {
            orderId = Integer.parseInt(orderIdParam);
        } catch (NumberFormatException e) {
            // Invalid order ID, will be handled by JavaScript
        }
    }
%>

<main class="page-main py-4">
    <div class="container">
        <div class="row justify-content-center">
            <div class="col-lg-8">
                <!-- Success Message -->
                <div class="card card-light border-success border-2 mb-4 shadow-lg">
                    <div class="card-body text-center p-5">
                        <div class="mb-4">
                            <i class="bi bi-check-circle-fill text-success" style="font-size: 5rem;"></i>
                        </div>
                        <h2 class="fw-bold text-success mb-3">注文が確定しました</h2>
                        <p class="text-muted mb-0">ご注文ありがとうございます。注文詳細は下記をご確認ください。</p>
                    </div>
                </div>
                
                <!-- Order Details -->
                <div class="card card-light mb-4">
                    <div class="card-header card-header-light">
                        <h5 class="mb-0">
                            <i class="bi bi-receipt-cutoff me-2"></i>注文詳細
                        </h5>
                    </div>
                    <div class="card-body p-4">
                        <div id="orderConfirmContent">
                            <div class="text-center py-5">
                                <div class="spinner-border text-primary" role="status">
                                    <span class="visually-hidden">読み込み中...</span>
                                </div>
                                <p class="mt-3 text-muted">注文情報を読み込み中...</p>
                            </div>
                        </div>
                    </div>
                </div>
                
                <!-- Action Buttons -->
                <div class="d-flex justify-content-between gap-3 mb-4">
                    <a href="${pageContext.request.contextPath}/index.jsp" class="btn btn-outline-secondary flex-fill">
                        <i class="bi bi-house me-2"></i>ホームに戻る
                    </a>
                    <a href="${pageContext.request.contextPath}/orders.jsp" class="btn btn-outline-primary flex-fill">
                        <i class="bi bi-list-ul me-2"></i>注文履歴を見る
                    </a>
                    <button id="viewOrderDetailBtn" class="btn btn-primary flex-fill" style="display: none;">
                        <i class="bi bi-eye me-2"></i>詳細を見る
                    </button>
                </div>
                
                <!-- Additional Information -->
                <div class="card card-light">
                    <div class="card-body">
                        <h6 class="fw-bold mb-3">
                            <i class="bi bi-info-circle me-2"></i>次のステップ
                        </h6>
                        <ul class="list-unstyled mb-0">
                            <li class="mb-2">
                                <i class="bi bi-check2 text-primary me-2"></i>
                                注文確認メールを送信しました
                            </li>
                            <li class="mb-2">
                                <i class="bi bi-check2 text-primary me-2"></i>
                                支払い方法に応じて、決済処理が行われます
                            </li>
                            <li class="mb-2">
                                <i class="bi bi-check2 text-primary me-2"></i>
                                発送準備が整い次第、発送通知をお送りします
                            </li>
                            <li>
                                <i class="bi bi-check2 text-primary me-2"></i>
                                ご不明な点がございましたら、お問い合わせください
                            </li>
                        </ul>
                    </div>
                </div>
            </div>
        </div>
    </div>
</main>

<script>
    // Set context path and order ID for JavaScript
    window.CONTEXT_PATH = '${pageContext.request.contextPath}';
    <% if (orderId != null) { %>
    window.orderId = <%= orderId %>;
    <% } else { %>
    // Try to get order ID from URL parameter
    const urlParams = new URLSearchParams(window.location.search);
    const orderIdParam = urlParams.get('id');
    if (orderIdParam) {
        window.orderId = parseInt(orderIdParam);
    }
    <% } %>
</script>
<script src="${pageContext.request.contextPath}/js/orderconfirm.js"></script>
<%@ include file="includes/footer.jsp" %>

