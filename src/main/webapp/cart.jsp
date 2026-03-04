<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="includes/header.jsp" %>

<main class="page-main py-4 cart-page">
    <div class="container">
        <h1 class="fw-bold mb-4">
            <i class="bi bi-cart"></i> ショッピングカート
        </h1>
        
        <div id="cartContainer" style="display: none;">
            <div class="row">
                <div class="col-md-8">
                    <div class="card card-light mb-4">
                        <div class="card-header card-header-light">
                            <h5 class="mb-0">カート内の商品</h5>
                        </div>
                        <div class="card-body" id="cartItems">
                            <!-- Cart items will be loaded via JavaScript -->
                        </div>
                    </div>
                </div>
                
                <div class="col-md-4">
                    <div class="card card-light sticky-top" style="top: 80px;">
                        <div class="card-header card-header-light">
                            <h5 class="mb-0">注文概要</h5>
                        </div>
                        <div class="card-body">
                            <div class="d-flex justify-content-between mb-2">
                                <span>小計:</span>
                                <span id="subtotal">¥0</span>
                            </div>
                            <div class="d-flex justify-content-between mb-2">
                                <span>送料:</span>
                                <span id="shipping">¥0</span>
                            </div>
                            <hr>
                            <div class="d-flex justify-content-between mb-3">
                                <strong>合計:</strong>
                                <strong class="text-primary fs-5" id="total">¥0</strong>
                            </div>
                            <a href="${pageContext.request.contextPath}/checkout.jsp" 
                               class="btn btn-primary w-100" id="checkoutBtn">
                                <i class="bi bi-credit-card"></i> レジに進む
                            </a>
                            <div id="emptyCartMessage" class="alert alert-warning mt-3" style="display: none;">
                                <i class="bi bi-exclamation-triangle me-2"></i>カートに商品を追加してください
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        
        <div id="emptyCart" class="empty-state-container" style="display: flex;">
            <div class="empty-state-content">
                <i class="bi bi-cart-x empty-state-icon"></i>
                <h3 class="empty-state-title">カートは空です</h3>
                <p class="empty-state-description">商品を追加してカートを満たしましょう</p>
                <a href="${pageContext.request.contextPath}/products.jsp" class="btn btn-primary btn-lg empty-state-button">
                    <i class="bi bi-bag me-2"></i>商品を見る
                </a>
            </div>
        </div>
    </div>
</main>

<script src="${pageContext.request.contextPath}/js/cart.js"></script>
<%@ include file="includes/footer.jsp" %>
