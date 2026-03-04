<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="includes/header.jsp" %>

<style>
.rental-section-card {
    background: var(--card-bg);
    border: 1px solid var(--border-color);
    border-radius: 16px;
    box-shadow: var(--shadow);
    transition: all 0.3s ease;
    overflow: hidden;
}

.rental-section-card:hover {
    box-shadow: var(--shadow-hover);
    border-color: var(--primary-color);
}

.rental-section-header {
    background: var(--bg-secondary);
    border-bottom: 2px solid var(--border-color);
    padding: 1.25rem 1.5rem;
}

.rental-section-header h5 {
    margin: 0;
    font-weight: 600;
    color: var(--text-color);
    display: flex;
    align-items: center;
}

.rental-section-body {
    padding: 1.5rem;
}

.rental-product-card {
    background: var(--bg-secondary);
    border: 1px solid var(--border-color);
    border-radius: 12px;
    overflow: hidden;
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    height: 100%;
    cursor: pointer;
}

.rental-product-card:hover {
    transform: translateY(-8px);
    box-shadow: var(--shadow-hover);
    border-color: var(--primary-color);
}

.rental-product-image {
    width: 100%;
    height: 220px;
    object-fit: cover;
    background: var(--bg-secondary);
}

.rental-product-body {
    padding: 1.25rem;
}

.rental-price-badge {
    font-size: 0.85rem;
    padding: 0.4rem 0.75rem;
    border-radius: 6px;
    font-weight: 600;
}

.rental-form-section {
    background: var(--card-bg);
    border: 1px solid var(--border-color);
    border-radius: 16px;
    box-shadow: var(--shadow);
    overflow: hidden;
}

.rental-selected-product {
    background: var(--accent-light);
    border: 1px solid rgba(30, 136, 229, 0.3);
    border-radius: 12px;
    padding: 1.5rem;
    margin-bottom: 1.5rem;
}

.rental-price-summary {
    background: var(--card-bg);
    border: 1px solid var(--border-color);
    border-radius: 12px;
    padding: 1.5rem;
    box-shadow: var(--shadow);
}

.rental-price-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0.75rem 0;
    border-bottom: 1px solid var(--border-color);
    color: var(--text-color);
}

.rental-price-row:last-child {
    border-bottom: none;
    padding-top: 1rem;
    margin-top: 0.5rem;
    border-top: 2px solid var(--primary-color);
    background: var(--accent-light);
    margin-left: -1.5rem;
    margin-right: -1.5rem;
    padding-left: 1.5rem;
    padding-right: 1.5rem;
    border-radius: 0 0 12px 12px;
}

.rental-price-row span:first-child {
    flex-shrink: 0;
    white-space: nowrap;
}

.rental-price-row span:last-child {
    flex: 1 1 auto;
    min-width: 0;
    margin-left: 0.5rem;
    text-align: right;
    overflow-wrap: break-word;
}

.rental-address-card {
    background: var(--bg-secondary);
    border: 2px solid var(--border-color);
    border-radius: 12px;
    padding: 1.25rem;
    transition: all 0.3s ease;
    cursor: pointer;
}

.rental-address-card:hover {
    border-color: var(--primary-color);
    background: var(--accent-light);
}

.rental-address-card.selected {
    border-color: var(--primary-color);
    background: var(--accent-light);
}

.rental-form-section-wrapper {
    scroll-margin-top: 1rem;
}

.rental-action-buttons {
    position: sticky;
    bottom: 0;
    background: var(--bg-primary);
    padding: 1.5rem;
    border-top: 2px solid var(--border-color);
    margin-top: 2rem;
    border-radius: 16px 16px 0 0;
    box-shadow: 0 -4px 12px rgba(0, 0, 0, 0.1);
}

@media (max-width: 768px) {
    #rentalFormSection {
        overflow-x: hidden;
    }
    
    .rental-price-summary {
        overflow-x: hidden;
    }
    
    .rental-price-row:last-child {
        margin-left: 0;
        margin-right: 0;
        padding-left: 1rem;
        padding-right: 1rem;
        border-radius: 0 0 12px 12px;
    }
    
    .rental-price-row span:first-child {
        font-size: 0.875rem;
    }
    
    .rental-section-card {
        min-width: 0;
        overflow-x: hidden;
    }
    
    .rental-address-card {
        overflow: hidden;
        min-width: 0;
    }
    
    .rental-address-card .form-check-label,
    .rental-address-card .text-muted {
        overflow-wrap: break-word;
        word-break: break-word;
    }
    
    #addressSelection {
        min-width: 0;
        overflow-x: hidden;
    }
    
    .rental-action-buttons {
        position: fixed;
        left: 0;
        right: 0;
        bottom: calc(52px + env(safe-area-inset-bottom, 0));
        margin: 0;
        margin-top: 0;
        padding: 1rem;
        padding-bottom: calc(1rem + env(safe-area-inset-bottom, 0));
        z-index: 1020;
        border-radius: 0;
        box-shadow: 0 -4px 16px rgba(0, 0, 0, 0.12);
    }
    
    .rental-action-buttons .d-grid {
        max-width: 100%;
        padding-left: 0.5rem;
        padding-right: 0.5rem;
    }
    
    #rentalFormSection {
        padding-bottom: 160px;
    }
    
    .rental-section-body {
        padding: 1rem;
    }
    
    .rental-product-card:hover {
        transform: translateY(-4px);
    }
    
    .rental-product-image {
        height: 160px;
    }
    
    .rental-product-body {
        padding: 0.75rem 1rem;
    }
    
    .rental-product-body h6 {
        font-size: 0.95rem;
        min-height: 2.25rem !important;
    }
    
    .rental-product-body p.text-muted {
        min-height: 2.5rem !important;
        -webkit-line-clamp: 2;
        font-size: 0.8rem;
    }
    
    .rental-price-badge {
        font-size: 0.75rem;
        padding: 0.3rem 0.5rem;
    }
    
    .rental-product-body .btn {
        padding: 0.4rem 0.5rem;
        font-size: 0.8rem;
        white-space: normal;
        line-height: 1.25;
        min-width: 0;
    }
    
    .rental-product-body .btn .bi {
        font-size: 0.9em;
    }
}

@media (max-width: 576px) {
    .rental-product-image {
        height: 150px;
    }
    
    .rental-section-body {
        padding: 0.75rem;
    }
    
    .rental-product-body {
        padding: 0.6rem 0.75rem;
    }
    
    .rental-product-body .btn {
        font-size: 0.75rem;
        padding: 0.35rem 0.4rem;
    }
    
    #productSelection {
        --bs-gutter-x: 0.75rem;
        --bs-gutter-y: 0.75rem;
    }
    
    /* Form rental - compact layout */
    #rentalFormSection .rental-section-body {
        padding: 0.75rem 1rem;
    }
    
    #rentalFormSection .rental-selected-product {
        padding: 1rem;
    }
    
    #rentalFormSection .rental-price-summary {
        padding: 1rem;
    }
    
    #rentalFormSection .rental-address-card {
        padding: 1rem;
    }
    
    .rental-action-buttons {
        padding: 0.75rem 1rem;
        padding-bottom: calc(0.75rem + env(safe-area-inset-bottom, 0));
    }
    
    #rentalFormSection {
        padding-bottom: 150px;
    }
    
    .rental-action-buttons .btn-lg {
        padding: 0.75rem 1rem;
        font-size: 1rem;
    }
}

/* Rental page - prevent horizontal scroll */
@media (max-width: 991.98px) {
    body:has(main.rental-page) {
        overflow-x: hidden;
    }
    
    .rental-page .container {
        overflow-x: hidden;
        max-width: 100%;
    }
    
    body:has(main.rental-page) .footer-mobile {
        padding-top: 1rem;
        padding-bottom: 1rem;
        margin-top: 1rem;
    }
}
</style>

<main class="page-main py-5 rental-page">
    <div class="container">
        <div class="row mb-4">
            <div class="col-12">
                <h1 class="fw-bold mb-2">
                    <i class="bi bi-calendar-check me-2 text-warning"></i>レンタル予約
                </h1>
                <p class="text-muted mb-0">レンタル可能な商品を選択して予約してください</p>
            </div>
        </div>
        
        <div id="rentalContainer">
            <!-- Product selection section -->
            <div class="rental-section-card mb-4">
                <div class="rental-section-header">
                    <h5>
                        <i class="bi bi-box-seam me-2 text-primary"></i>レンタル商品を選択
                    </h5>
                </div>
                <div class="rental-section-body">
                    <div class="mb-4">
                        <label for="productSearch" class="form-label fw-semibold">
                            <i class="bi bi-search me-2"></i>商品を検索
                        </label>
                        <input type="text" class="form-control form-control-light" 
                               id="productSearch" placeholder="商品名で検索...">
                    </div>
                    <div id="productSelection" class="row g-4">
                        <!-- Products will be loaded here -->
                        <div class="col-12 text-center py-5">
                            <div class="spinner-border text-primary" role="status" style="width: 3rem; height: 3rem;">
                                <span class="visually-hidden">読み込み中...</span>
                            </div>
                            <p class="text-muted mt-3">商品を読み込んでいます...</p>
                        </div>
                    </div>
                </div>
            </div>
            
            <!-- Selected product and rental form -->
            <div id="rentalFormSection" class="rental-form-section-wrapper" style="display: none;">
                <div class="rental-form-section mb-4">
                    <div class="rental-section-header">
                        <h5>
                            <i class="bi bi-calendar3 me-2 text-primary"></i>レンタル詳細
                        </h5>
                    </div>
                    <div class="rental-section-body">
                        <div id="selectedProductInfo" class="rental-selected-product">
                            <!-- Selected product info will be shown here -->
                        </div>
                        
                        <form id="rentalForm">
                            <input type="hidden" id="rentalProductId" name="product_id">
                            
                            <div class="row g-3 mb-4">
                                <div class="col-md-6">
                                    <label for="rentalStartDate" class="form-label fw-semibold">
                                        <i class="bi bi-calendar-event me-2"></i>レンタル開始日 *
                                    </label>
                                    <input type="date" class="form-control form-control-light" 
                                           id="rentalStartDate" name="start_date" required>
                                    <small class="text-muted d-block mt-1">
                                        <i class="bi bi-info-circle me-1"></i>今日以降の日付を選択してください
                                    </small>
                                </div>
                                <div class="col-md-6">
                                    <label for="rentalEndDate" class="form-label fw-semibold">
                                        <i class="bi bi-calendar-x me-2"></i>レンタル終了日 *
                                    </label>
                                    <input type="date" class="form-control form-control-light" 
                                           id="rentalEndDate" name="end_date" required>
                                    <small class="text-muted d-block mt-1">
                                        <i class="bi bi-info-circle me-1"></i>開始日より後の日付を選択してください
                                    </small>
                                </div>
                            </div>
                            
                            <div class="row g-3 mb-4">
                                <div class="col-md-6">
                                    <label for="rentalType" class="form-label fw-semibold">
                                        <i class="bi bi-clock-history me-2"></i>レンタル期間タイプ *
                                    </label>
                                    <select class="form-select form-control-light" id="rentalType" name="rental_type" required>
                                        <option value="">商品を選択してください</option>
                                        <!-- Options will be populated dynamically based on available rental prices -->
                                    </select>
                                    <small class="text-muted d-block mt-1">
                                        <i class="bi bi-info-circle me-1"></i>商品に設定されているレンタルタイプのみ表示されます
                                    </small>
                                </div>
                                <div class="col-md-6">
                                    <label for="rentalQuantity" class="form-label fw-semibold">
                                        <i class="bi bi-123 me-2"></i>数量 *
                                    </label>
                                    <input type="number" class="form-control form-control-light" 
                                           id="rentalQuantity" name="quantity" value="1" min="1" required>
                                </div>
                            </div>
                            
                            <div class="alert alert-info mb-4">
                                <div class="d-flex align-items-start">
                                    <i class="bi bi-info-circle me-2 fs-5"></i>
                                    <div>
                                        <strong class="d-block mb-2">レンタル料金情報</strong>
                                        <div id="rentalPriceInfo" class="small">
                                            <div class="mb-1">日単位: <span id="dailyPrice" class="fw-semibold">-</span></div>
                                            <div class="mb-1">週単位: <span id="weeklyPrice" class="fw-semibold">-</span></div>
                                            <div>月単位: <span id="monthlyPrice" class="fw-semibold">-</span></div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                            
                            <div class="rental-price-summary">
                                <h6 class="mb-3 fw-semibold text-primary">
                                    <i class="bi bi-calculator me-2"></i>レンタル料金計算
                                </h6>
                                <div class="rental-price-row">
                                    <span class="text-muted">
                                        <i class="bi bi-calendar-range me-1"></i>レンタル期間:
                                    </span>
                                    <span class="fw-semibold text-primary" id="rentalPeriod">-</span>
                                </div>
                                <div class="rental-price-row">
                                    <span class="text-muted">
                                        <i class="bi bi-currency-yen me-1"></i>基本料金 (1個あたり):
                                    </span>
                                    <span class="fw-semibold text-primary" id="basePrice">¥0</span>
                                </div>
                                <div class="rental-price-row">
                                    <span class="text-muted">
                                        <i class="bi bi-123 me-1"></i>数量:
                                    </span>
                                    <span class="fw-semibold" id="displayQuantity">1</span>
                                </div>
                                <div class="rental-price-row">
                                    <strong class="fs-5 text-primary">
                                        <i class="bi bi-cash-stack me-2"></i>合計:
                                    </strong>
                                    <strong class="fs-4 text-warning fw-bold" id="totalRentalPrice">¥0</strong>
                                </div>
                            </div>
                        </form>
                    </div>
                </div>
                
                <!-- Delivery address section -->
                <div class="rental-section-card mb-4">
                    <div class="rental-section-header">
                        <h5>
                            <i class="bi bi-geo-alt me-2 text-primary"></i>配送先住所
                        </h5>
                    </div>
                    <div class="rental-section-body">
                        <div id="addressSelection">
                            <div class="text-center py-4">
                                <div class="spinner-border text-primary" role="status">
                                    <span class="visually-hidden">読み込み中...</span>
                                </div>
                                <p class="text-muted mt-3">住所を読み込んでいます...</p>
                            </div>
                        </div>
                    </div>
                </div>
                
                <!-- Action buttons -->
                <div class="rental-action-buttons">
                    <div class="d-grid gap-2">
                        <button class="btn btn-warning btn-lg py-3 fw-semibold" onclick="confirmRental()" id="confirmRentalBtn">
                            <i class="bi bi-check-circle me-2"></i>レンタルを予約
                        </button>
                        <button class="btn btn-outline-secondary" onclick="resetRentalForm()">
                            <i class="bi bi-arrow-counterclockwise me-2"></i>商品選択に戻る
                        </button>
                        <button class="btn btn-outline-primary d-none" id="backToProductBtn" onclick="backToProductDetail()">
                            <i class="bi bi-arrow-left me-2"></i>商品詳細に戻る
                        </button>
                    </div>
                </div>
            </div>
            
            <!-- Empty state -->
            <div id="emptyRentalState" class="empty-state-container" style="display: none;">
                <div class="empty-state-content">
                    <i class="bi bi-calendar-x empty-state-icon"></i>
                    <h3 class="empty-state-title">レンタル可能な商品が見つかりません</h3>
                    <p class="empty-state-description">レンタル可能な商品を探してみましょう</p>
                    <a href="${pageContext.request.contextPath}/products.jsp" class="btn btn-primary btn-lg empty-state-button">
                        <i class="bi bi-bag me-2"></i>商品を見る
                    </a>
                </div>
            </div>
        </div>
    </div>
</main>

<script src="${pageContext.request.contextPath}/js/rental.js"></script>
<%@ include file="includes/footer.jsp" %>

