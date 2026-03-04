<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="includes/header.jsp" %>

<main class="bg-light py-4 products-page" style="min-height: calc(100vh - 76px);">
    <div class="container">
        <!-- Header -->
        <div class="d-flex flex-column flex-md-row justify-content-between align-items-start align-items-md-center mb-4">
            <div class="d-flex align-items-center gap-3">
                <!-- Reset Button (shown when no search query) -->
                <button class="btn btn-outline-secondary d-none" id="backButton" onclick="resetToAllProducts()" style="display: none;">
                    <i class="bi bi-arrow-counterclockwise me-1"></i>すべての商品を表示
                </button>
                <div>
                    <h1 class="fw-bold mb-1">
                        <i class="bi bi-grid-3x3-gap me-2"></i>商品一覧
                    </h1>
                    <p class="text-muted mb-0 small">様々な商品を探して見つけよう</p>
                </div>
            </div>
        </div>
        
        <!-- Category Title Banner (shown when category filter is applied) -->
        <div class="alert alert-info border-info d-none mb-4" id="categoryTitleBanner" style="background: linear-gradient(135deg, rgba(30, 136, 229, 0.1) 0%, rgba(30, 136, 229, 0.05) 100%); border: 1px solid rgba(30, 136, 229, 0.3) !important;">
            <div class="d-flex align-items-center">
                <i class="bi bi-tag-fill text-info me-2" style="font-size: 1.5rem;"></i>
                <div>
                    <h4 class="mb-0 text-info fw-bold" id="categoryTitleText">カテゴリー</h4>
                </div>
            </div>
        </div>
        
        <!-- Search Bar -->
        <div class="card bg-white border mb-4" style="position: relative; z-index: 10000; isolation: isolate;">
            <div class="card-body p-3" style="position: relative; z-index: 10000;">
                <div class="row g-2">
                    <div class="col-12 col-md-5" style="position: relative; z-index: 10000;">
                        <div class="input-group position-relative" style="z-index: 10000;">
                            <input type="text" class="form-control border" 
                                   id="searchInput" placeholder="商品・カテゴリー・出品者を検索..." autocomplete="off">
                            <button class="btn btn-primary" id="searchBtn" type="button">
                                <i class="bi bi-search"></i>
                            </button>
                            <div id="searchSuggestions" class="search-suggestions dropdown-menu bg-white border" style="display: none; width: 100%; max-height: 400px; overflow-y: auto; z-index: 10000; position: absolute;">
                                <!-- Suggestions will be loaded here -->
                            </div>
                        </div>
                    </div>
                    <div class="col-6 col-md-3">
                        <select class="form-select border" id="categoryFilter">
                            <option value="">すべてのカテゴリー</option>
                        </select>
                    </div>
                    <div class="col-6 col-md-2">
                        <select class="form-select border" id="sortFilter">
                            <option value="newest">新着順</option>
                            <option value="price_low">価格: 安い順</option>
                            <option value="price_high">価格: 高い順</option>
                            <option value="popular">人気順</option>
                        </select>
                    </div>
                    <div class="col-12 col-md-2">
                        <button class="btn btn-outline-primary w-100" type="button" id="filterToggleBtn" data-bs-toggle="collapse" data-bs-target="#filterCollapse" aria-expanded="false" aria-controls="filterCollapse">
                            <i class="bi bi-funnel me-2"></i>フィルター
                        </button>
                    </div>
                </div>
            </div>
        </div>
        
        <!-- Filter Collapse -->
        <div class="collapse mb-4" id="filterCollapse" style="position: relative; z-index: 1 !important; isolation: isolate;">
            <div class="card bg-white border" style="position: relative; z-index: 1 !important;">
                <div class="card-header bg-light border-bottom">
                    <h5 class="mb-0">
                        <i class="bi bi-funnel-fill me-2"></i>フィルター
                    </h5>
                </div>
                <div class="card-body">
                    <div class="row g-3">
                        <!-- Price Range -->
                        <div class="col-md-6">
                            <label class="form-label fw-semibold mb-3">
                                <i class="bi bi-currency-yen me-1"></i>価格帯
                            </label>
                            <div class="price-range-container">
                                <div class="d-flex justify-content-between mb-2">
                                    <span class="badge bg-primary text-white" id="minPriceDisplay">¥0</span>
                                    <span class="badge bg-primary text-white" id="maxPriceDisplay">¥1,000,000</span>
                                </div>
                                <div class="price-range-wrapper position-relative">
                                    <input type="range" class="form-range price-range-slider" 
                                           id="minPrice" min="0" max="1000000" step="1000" value="0">
                                    <input type="range" class="form-range price-range-slider" 
                                           id="maxPrice" min="0" max="1000000" step="1000" value="1000000">
                                </div>
                                <div class="d-flex justify-content-between mt-2">
                                    <small>最低: <span id="minPriceValue" class="fw-bold">¥0</span></small>
                                    <small>最高: <span id="maxPriceValue" class="fw-bold">¥1,000,000</span></small>
                                </div>
                            </div>
                        </div>
                        
                        <!-- Condition Filter -->
                        <div class="col-md-3">
                            <label class="form-label fw-semibold mb-3">
                                <i class="bi bi-check-circle me-1"></i>状態
                            </label>
                            <div class="d-flex flex-column gap-2">
                                <div class="form-check">
                                    <input class="form-check-input" type="checkbox" value="new" id="conditionNew">
                                    <label class="form-check-label" for="conditionNew">
                                        <span class="badge bg-success">新品</span>
                                    </label>
                                </div>
                                <div class="form-check">
                                    <input class="form-check-input" type="checkbox" value="like_new" id="conditionLikeNew">
                                    <label class="form-check-label" for="conditionLikeNew">
                                        <span class="badge bg-info">新品同様</span>
                                    </label>
                                </div>
                                <div class="form-check">
                                    <input class="form-check-input" type="checkbox" value="good" id="conditionGood">
                                    <label class="form-check-label" for="conditionGood">
                                        <span class="badge bg-primary">良好</span>
                                    </label>
                                </div>
                                <div class="form-check">
                                    <input class="form-check-input" type="checkbox" value="fair" id="conditionFair">
                                    <label class="form-check-label" for="conditionFair">
                                        <span class="badge bg-warning text-dark">可</span>
                                    </label>
                                </div>
                            </div>
                        </div>
                        
                        <!-- Rental Filter -->
                        <div class="col-md-3">
                            <label class="form-label fw-semibold mb-3">
                                <i class="bi bi-calendar-check me-1"></i>オプション
                            </label>
                            <div class="form-check form-switch">
                                <input class="form-check-input" type="checkbox" id="rentalFilter" role="switch">
                                <label class="form-check-label" for="rentalFilter">
                                    レンタル可能のみ
                                </label>
                            </div>
                        </div>
                    </div>
                    
                    <hr class="my-3">
                    
                    <!-- Action Buttons -->
                    <div class="d-flex gap-2">
                        <button class="btn btn-primary" id="applyFilters">
                            <i class="bi bi-check-circle me-2"></i>フィルターを適用
                        </button>
                        <button class="btn btn-outline-secondary" id="resetFilters" title="フィルターをリセット">
                            <i class="bi bi-arrow-counterclockwise me-2"></i>リセット
                        </button>
                    </div>
                </div>
            </div>
        </div>
        
        <!-- Results Info -->
        <div class="mb-3">
            <div id="resultsInfo" class="text-muted small">
                <!-- Results count will be shown here -->
            </div>
        </div>
        
        <!-- Products Grid -->
        <div class="row g-4" id="productsGrid" style="position: relative; z-index: 1; margin-top: 0;">
            <!-- Products will be loaded via JavaScript -->
            <div class="col-12 text-center py-5">
                <div class="spinner-border text-primary" role="status">
                    <span class="visually-hidden">読み込み中...</span>
                </div>
                <p class="text-muted mt-3 mb-0">商品を読み込んでいます...</p>
            </div>
        </div>
        
        <!-- Pagination -->
        <nav aria-label="Page navigation" class="mt-4">
            <ul class="pagination justify-content-center" id="pagination">
                <!-- Pagination will be loaded via JavaScript -->
            </ul>
        </nav>
    </div>
</main>

<style>
.search-suggestions {
    position: absolute !important;
    top: 100% !important;
    left: 0 !important;
    right: 0 !important;
    margin-top: 0.25rem;
    border-radius: 8px;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    z-index: 10000 !important;
    background-color: #FFFFFF !important;
}

.search-suggestions .dropdown-item {
    padding: 0.75rem 1rem;
    transition: all 0.2s;
}

.search-suggestions .dropdown-item:hover,
.search-suggestions .dropdown-item.active {
    background-color: rgba(30, 136, 229, 0.1);
    color: var(--primary-color);
}

.price-range-container {
    padding: 1rem;
    background: rgba(30, 136, 229, 0.05);
    border-radius: 8px;
    border: 1px solid rgba(30, 136, 229, 0.2);
}

.price-range-slider {
    width: 100%;
    margin: 0.5rem 0;
}

.price-range-slider::-webkit-slider-thumb {
    appearance: none;
    width: 18px;
    height: 18px;
    border-radius: 50%;
    background: var(--primary-color);
    cursor: pointer;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
}

.price-range-slider::-moz-range-thumb {
    width: 18px;
    height: 18px;
    border-radius: 50%;
    background: var(--primary-color);
    cursor: pointer;
    border: none;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
}

/* Ensure filter doesn't overlap search results */
#filterCollapse {
    margin-bottom: 1.5rem;
}

#productsGrid {
    clear: both;
}

/* Ensure price display text is readable */
.price-range-container .badge {
    color: #FFFFFF !important;
}

.price-range-container small {
    color: var(--text-color) !important;
}

/* Ensure search suggestions appear above everything */
.card:has(#searchInput) {
    position: relative !important;
    z-index: 10000 !important;
    isolation: isolate !important;
}

.card:has(#searchInput) .card-body {
    position: relative !important;
    z-index: 10000 !important;
}

.input-group.position-relative {
    z-index: 10000 !important;
    position: relative !important;
}

#searchSuggestions {
    z-index: 10000 !important;
    position: absolute !important;
    top: 100% !important;
    left: 0 !important;
    right: 0 !important;
}

/* Ensure products grid doesn't interfere */
#productsGrid {
    position: relative;
    z-index: 1;
}

/* Ensure filter doesn't interfere with search suggestions */
#filterCollapse {
    position: relative !important;
    z-index: 1 !important;
    isolation: isolate !important;
}

#filterCollapse .card {
    position: relative !important;
    z-index: 1 !important;
}

#filterCollapse .card-header,
#filterCollapse .card-body {
    position: relative !important;
    z-index: 1 !important;
}

/* Ensure search container is always on top */
.col-12.col-md-6:has(#searchInput) {
    position: relative !important;
    z-index: 10000 !important;
}

/* Ensure search card and body are on top */
.card:has(#searchInput),
.card:has(#searchInput) .card-body {
    position: relative !important;
    z-index: 10000 !important;
    isolation: isolate !important;
}

/* Ensure search suggestions are always visible above everything */
.search-suggestions,
#searchSuggestions {
    z-index: 10000 !important;
    position: absolute !important;
}

/* Additional override to ensure suggestions are above filter */
#searchSuggestions.dropdown-menu {
    z-index: 10000 !important;
}

/* Force filter to stay below search */
#filterCollapse,
#filterCollapse * {
    z-index: 1 !important;
}

/* Ensure navbar dropdowns are always above search card */
.navbar {
    position: relative !important;
    z-index: 10001 !important;
}

.navbar .dropdown-menu {
    z-index: 10002 !important;
    position: absolute !important;
}

/* Ensure dropdowns maintain correct positioning on products page */
.navbar .dropdown-menu:not(.navbar-collapse .dropdown-menu) {
    right: 0 !important;
    left: auto !important;
    max-width: min(280px, calc(100vw - 1rem)) !important;
}

#moreMenuDropdown + .dropdown-menu {
    right: 0 !important;
    left: auto !important;
    max-width: min(280px, calc(100vw - 1rem)) !important;
}

#userMenuDropdown + .dropdown-menu {
    right: -0.5rem !important;
    left: auto !important;
    max-width: min(280px, calc(100vw - 1rem)) !important;
}

.navbar .dropdown-toggle {
    z-index: 10001 !important;
    position: relative !important;
}

/* Ensure navbar dropdown doesn't get covered by search card */
.navbar .nav-item.dropdown {
    position: relative !important;
    z-index: 10001 !important;
}

/* Ensure navbar container doesn't interfere */
.navbar .container {
    position: relative !important;
    z-index: 10001 !important;
}

.navbar .navbar-collapse {
    position: relative !important;
    z-index: 10001 !important;
}

.navbar .navbar-nav {
    position: relative !important;
    z-index: 10001 !important;
}

/* Prevent category select text truncation - ensure "すべてのカテゴリー" fits */
.products-page #categoryFilter {
    min-width: 10.5em;
    padding-right: 2rem;
}
.products-page #sortFilter {
    padding-right: 2rem;
}
</style>

<script src="${pageContext.request.contextPath}/js/products.js"></script>
<%@ include file="includes/footer.jsp" %>
