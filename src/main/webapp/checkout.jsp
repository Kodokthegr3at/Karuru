<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="includes/header.jsp" %>
<%
    if (currentUser == null) {
        response.sendRedirect("login.jsp?redirect=checkout");
        return;
    }
%>

<main class="page-main py-4 checkout-page">
    <div class="container">
        <h1 class="fw-bold mb-4">
            <i class="bi bi-credit-card"></i> お支払い情報
        </h1>
        
        <div class="row">
            <div class="col-lg-8 mb-4">
                <!-- Shipping Address -->
                <div class="card card-light mb-4">
                    <div class="card-header card-header-light">
                        <h5 class="mb-0">
                            <i class="bi bi-geo-alt"></i> 配送先住所
                        </h5>
                    </div>
                    <div class="card-body">
                        <div id="addressesList" class="mb-3">
                            <!-- Addresses will be loaded via JavaScript -->
                        </div>
                        <button id="addAddressBtn" class="btn btn-outline-primary" data-bs-toggle="modal" data-bs-target="#addressModal">
                            <i class="bi bi-plus-circle"></i> 新しい住所を追加
                        </button>
                    </div>
                </div>

                <!-- Delivery Method -->
                <div class="card card-light mb-4">
                    <div class="card-header card-header-light">
                        <h5 class="mb-0">
                            <i class="bi bi-truck"></i> 配送方法
                        </h5>
                    </div>
                    <div class="card-body">
                        <div class="mb-3">
                            <label class="form-label fw-semibold">配送オプション</label>
                            <div class="form-check mb-2">
                                <input class="form-check-input" type="radio" name="deliveryMethod" id="deliveryStandard" value="standard" checked>
                                <label class="form-check-label w-100" for="deliveryStandard">
                                    <div class="d-flex justify-content-between align-items-center">
                                        <div>
                                            <i class="bi bi-truck text-primary me-2"></i>
                                            <strong>標準配送</strong>
                                            <small class="text-muted d-block ms-4">3-5営業日でお届け</small>
                                        </div>
                                        <span class="badge bg-secondary">¥500</span>
                                    </div>
                                </label>
                            </div>
                            <div class="form-check mb-2">
                                <input class="form-check-input" type="radio" name="deliveryMethod" id="deliveryExpress" value="express">
                                <label class="form-check-label w-100" for="deliveryExpress">
                                    <div class="d-flex justify-content-between align-items-center">
                                        <div>
                                            <i class="bi bi-lightning-charge text-warning me-2"></i>
                                            <strong>速達配送</strong>
                                            <small class="text-muted d-block ms-4">1-2営業日でお届け</small>
                                        </div>
                                        <span class="badge bg-warning text-dark">¥1,200</span>
                                    </div>
                                </label>
                            </div>
                            <div class="form-check">
                                <input class="form-check-input" type="radio" name="deliveryMethod" id="deliverySameDay" value="same_day">
                                <label class="form-check-label w-100" for="deliverySameDay">
                                    <div class="d-flex justify-content-between align-items-center">
                                        <div>
                                            <i class="bi bi-rocket-takeoff text-danger me-2"></i>
                                            <strong>当日配送</strong>
                                            <small class="text-muted d-block ms-4">当日中にお届け（午前中注文のみ）</small>
                                        </div>
                                        <span class="badge bg-danger">¥2,500</span>
                                    </div>
                                </label>
                            </div>
                        </div>
                        
                        <div class="mb-3">
                            <label class="form-label fw-semibold">配送業者</label>
                            <select class="form-select form-select-light" id="courierSelect" name="courier">
                                <option value="yamato" selected>ヤマト運輸 (宅急便)</option>
                                <option value="sagawa">佐川急便</option>
                                <option value="japan_post">日本郵便 (ゆうパック)</option>
                            </select>
                            <small class="text-muted">
                                <i class="bi bi-info-circle me-1"></i>
                                配送業者は選択した配送方法に応じて自動的に最適なサービスを提供します
                            </small>
                        </div>
                        
                        <div id="deliveryEstimate" class="alert alert-info mb-0">
                            <i class="bi bi-calendar-check me-2"></i>
                            <strong>お届け予定日:</strong> <span id="estimatedDeliveryDate">計算中...</span>
                        </div>
                    </div>
                </div>

                <!-- Payment Method -->
                <div class="card card-light mb-4">
                    <div class="card-header card-header-light">
                        <h5 class="mb-0">
                            <i class="bi bi-wallet2"></i> 支払い方法
                        </h5>
                    </div>
                    <div class="card-body">
                        <div class="form-check mb-3">
                            <input class="form-check-input" type="radio" name="paymentMethod" id="paymentWallet" value="wallet" checked>
                            <label class="form-check-label" for="paymentWallet">
                                <i class="bi bi-wallet2"></i> ウォレット
                            </label>
                        </div>
                        <div class="form-check mb-3">
                            <input class="form-check-input" type="radio" name="paymentMethod" id="paymentCredit" value="credit_card">
                            <label class="form-check-label" for="paymentCredit">
                                <i class="bi bi-credit-card"></i> クレジットカード
                            </label>
                        </div>
                        <div class="form-check mb-3">
                            <input class="form-check-input" type="radio" name="paymentMethod" id="paymentBank" value="bank_transfer">
                            <label class="form-check-label" for="paymentBank">
                                <i class="bi bi-bank"></i> 銀行振込
                            </label>
                        </div>
                        <div class="form-check">
                            <input class="form-check-input" type="radio" name="paymentMethod" id="paymentCOD" value="cod">
                            <label class="form-check-label" for="paymentCOD">
                                <i class="bi bi-cash"></i> 代金引換
                            </label>
                        </div>
                    </div>
                </div>

                <!-- Order Items -->
                <div class="card card-light">
                    <div class="card-header card-header-light">
                        <h5 class="mb-0">
                            <i class="bi bi-list-check"></i> 注文確認
                        </h5>
                    </div>
                    <div class="card-body">
                        <div id="orderItems">
                            <!-- Order items will be loaded via JavaScript -->
                        </div>
                    </div>
                </div>
            </div>

            <!-- Order Summary -->
            <div class="col-lg-4">
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
                        <div class="d-flex justify-content-between mb-2">
                            <span>割引:</span>
                            <span id="discount">¥0</span>
                        </div>
                        <hr class="bg-secondary">
                        <div class="d-flex justify-content-between mb-3">
                            <strong>合計:</strong>
                            <strong class="text-primary fs-5" id="total">¥0</strong>
                        </div>
                        <button id="placeOrderBtn" class="btn btn-primary w-100">
                            <i class="bi bi-check-circle"></i> 注文を確定
                        </button>
                    </div>
                </div>
            </div>
        </div>
    </div>
</main>

<!-- Address Modal with Google Maps -->
<div class="modal fade" id="addressModal" tabindex="-1" aria-labelledby="addressModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-lg">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title" id="addressModalLabel">
                    <i class="bi bi-geo-alt me-2"></i>新しい住所を追加
                </h5>
                <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <div class="modal-body">
                <form id="addressForm">
                    <div class="mb-3">
                        <label for="addressLabel" class="form-label">住所ラベル</label>
                        <input type="text" class="form-control form-control-light" id="addressLabel" 
                               placeholder="例: 自宅、職場" value="自宅">
                    </div>
                    
                    <div class="mb-3">
                        <label for="recipientName" class="form-label">受取人名 <span class="text-danger">*</span></label>
                        <input type="text" class="form-control form-control-light" id="recipientName" 
                               placeholder="受取人の名前" required>
                    </div>
                    
                    <div class="mb-3">
                        <label for="phone" class="form-label">電話番号 <span class="text-danger">*</span></label>
                        <input type="tel" class="form-control form-control-light" id="phone" 
                               placeholder="090-1234-5678" required>
                    </div>
                    
                    
                    <div class="row">
                        <div class="col-md-6 mb-3">
                            <label for="postalCode" class="form-label">
                                郵便番号 <span class="text-danger">*</span>
                                <small class="text-muted ms-2">(7桁の数字を入力すると自動で住所が入力されます)</small>
                            </label>
                            <div class="input-group">
                                <input type="text" class="form-control form-control-light" id="postalCode" 
                                       placeholder="1234567 または 123-4567" 
                                       maxlength="8"
                                       pattern="\d{3}-?\d{4}"
                                       required>
                                <span class="input-group-text" id="postalCodeStatus" style="display: none;">
                                    <span class="spinner-border spinner-border-sm text-primary" role="status" style="display: none;">
                                        <span class="visually-hidden">読み込み中...</span>
                                    </span>
                                    <i class="bi bi-check-circle text-success" style="display: none;"></i>
                                </span>
                            </div>
                            <small class="text-muted d-block mt-1">
                                <i class="bi bi-info-circle me-1"></i>郵便番号を入力すると、都道府県・市区町村・町域が自動入力されます
                            </small>
                        </div>
                        <div class="col-md-6 mb-3">
                            <label for="prefecture" class="form-label">都道府県 <span class="text-danger">*</span></label>
                            <input type="text" class="form-control form-control-light" id="prefecture" 
                                   placeholder="東京都" required>
                        </div>
                    </div>
                    
                    <div class="mb-3">
                        <label for="city" class="form-label">市区町村 <span class="text-danger">*</span></label>
                        <input type="text" class="form-control form-control-light" id="city" 
                               placeholder="渋谷区" required>
                    </div>
                    
                    <div class="mb-3">
                        <label for="addressLine1" class="form-label">番地・建物名 <span class="text-danger">*</span></label>
                        <input type="text" class="form-control form-control-light" id="addressLine1" 
                               placeholder="1-2-3" required>
                    </div>
                    
                    <div class="mb-3">
                        <label for="addressLine2" class="form-label">建物名・部屋番号</label>
                        <input type="text" class="form-control form-control-light" id="addressLine2" 
                               placeholder="マンション名 101号室">
                    </div>
                    
                    <div class="mb-3">
                        <div class="form-check">
                            <input class="form-check-input" type="checkbox" id="isDefault" checked>
                            <label class="form-check-label" for="isDefault">
                                デフォルトの配送先として設定
                            </label>
                        </div>
                    </div>
                    
                </form>
            </div>
            <div class="modal-footer" style="border-top: 1px solid var(--border-color);">
                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">キャンセル</button>
                <button type="button" class="btn btn-primary" id="saveAddressBtn">
                    <i class="bi bi-check-circle me-2"></i>保存
                </button>
            </div>
        </div>
    </div>
</div>

<script src="${pageContext.request.contextPath}/js/checkout.js"></script>
<%@ include file="includes/footer.jsp" %>
