<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="includes/header.jsp" %>
<%
    if (currentUser == null) {
        response.sendRedirect("login.jsp?redirect=create-listing");
        return;
    }
%>

<main class="page-main py-4 create-listing-page">
    <div class="container">
        <h1 class="fw-bold mb-4">
            <i class="bi bi-plus-circle"></i> 商品を出品
        </h1>
        
        <form id="createListingForm" action="${pageContext.request.contextPath}/CreateListingServlet" method="POST" enctype="multipart/form-data">
            <div class="row">
                <!-- Left Column: Basic Info -->
                <div class="col-md-6 mb-4">
                    <div class="card card-light mb-4">
                        <div class="card-header card-header-light">
                            <h5 class="mb-0">基本情報</h5>
                        </div>
                        <div class="card-body">
                            <div class="mb-3">
                                <label for="productName" class="form-label">
                                    商品名 <span class="text-danger">*</span>
                                </label>
                                <input type="text" class="form-control form-control-light" 
                                       id="productName" name="product_name" required maxlength="100" 
                                       placeholder="例: iPhone 14 Pro 256GB">
                                <small class="form-text text-muted">100文字以内で入力してください</small>
                            </div>
                            
                            <div class="mb-3">
                                <label for="category" class="form-label">
                                    カテゴリー <span class="text-danger">*</span>
                                </label>
                                <select class="form-select form-control-light" 
                                        id="category" name="category_id" required>
                                    <option value="">選択してください</option>
                                </select>
                            </div>
                            
                            <div class="mb-3">
                                <label for="description" class="form-label">
                                    商品説明 <span class="text-danger">*</span>
                                </label>
                                <textarea class="form-control form-control-light" 
                                          id="description" name="description" rows="6" required 
                                          placeholder="商品の詳細を入力してください..."></textarea>
                                <small class="form-text text-muted">商品の状態、特徴、注意事項などを詳しく記入してください</small>
                            </div>
                        </div>
                    </div>

                    <div class="card card-light mb-4">
                        <div class="card-header card-header-light">
                            <h5 class="mb-0">価格設定</h5>
                        </div>
                        <div class="card-body">
                            <div class="mb-3">
                                <label for="price" class="form-label">
                                    販売価格 <span class="text-danger">*</span>
                                </label>
                                <div class="input-group">
                                    <span class="input-group-text input-group-text-light">¥</span>
                                    <input type="number" class="form-control form-control-light" 
                                           id="price" name="price" required min="1" step="1" placeholder="10000">
                                </div>
                            </div>
                            
                            <div class="mb-3">
                                <label for="originalPrice" class="form-label">元の価格（任意）</label>
                                <div class="input-group">
                                    <span class="input-group-text input-group-text-light">¥</span>
                                    <input type="number" class="form-control form-control-light" 
                                           id="originalPrice" name="original_price" min="0" step="1" placeholder="15000">
                                </div>
                                <small class="form-text text-muted">新品時の価格を入力すると割引率が自動計算されます</small>
                            </div>
                            
                            <div class="form-check">
                                <input class="form-check-input" type="checkbox" id="isNegotiable" name="is_negotiable" value="1">
                                <label class="form-check-label" for="isNegotiable">価格交渉可能</label>
                            </div>
                        </div>
                    </div>

                    <div class="card card-light">
                        <div class="card-header card-header-light">
                            <h5 class="mb-0">在庫・数量</h5>
                        </div>
                        <div class="card-body">
                            <div class="row">
                                <div class="col-md-6 mb-3">
                                    <label for="stockQuantity" class="form-label">
                                        在庫数 <span class="text-danger">*</span>
                                    </label>
                                    <input type="number" class="form-control form-control-light" 
                                           id="stockQuantity" name="stock_quantity" required min="1" value="1">
                                </div>
                                <div class="col-md-6 mb-3">
                                    <label for="minOrder" class="form-label">最低注文数</label>
                                    <input type="number" class="form-control form-control-light" 
                                           id="minOrder" name="min_order" min="1" value="1">
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- Right Column: Details & Images -->
                <div class="col-md-6 mb-4">
                    <div class="card card-light mb-4">
                        <div class="card-header card-header-light">
                            <h5 class="mb-0">商品画像</h5>
                        </div>
                        <div class="card-body">
                            <div class="image-upload-area border rounded p-4 text-center mb-3" 
                                 id="imageUploadArea" style="cursor: pointer; transition: all 0.3s ease; background: var(--bg-secondary);">
                                <input type="file" id="productImages" name="images" multiple accept="image/*" style="display: none;">
                                <div class="upload-placeholder">
                                    <i class="bi bi-cloud-upload fs-1 text-muted"></i>
                                    <p class="mt-2 mb-1">画像をドラッグ&ドロップまたはクリックしてアップロード</p>
                                    <small class="text-muted">最大10枚、各10MBまで（JPEG, PNG, GIF対応）</small>
                                </div>
                            </div>
                            
                            <div id="imagePreview" class="row g-2">
                                <!-- Image previews will be shown here -->
                            </div>
                        </div>
                    </div>

                    <div class="card card-light mb-4">
                        <div class="card-header card-header-light">
                            <h5 class="mb-0">商品状態</h5>
                        </div>
                        <div class="card-body">
                            <div class="mb-3">
                                <label for="condition" class="form-label">
                                    状態 <span class="text-danger">*</span>
                                </label>
                                <select class="form-select form-control-light" 
                                        id="condition" name="condition" required>
                                    <option value="">選択してください</option>
                                    <option value="new">新品</option>
                                    <option value="like_new">新品同様</option>
                                    <option value="good">良好</option>
                                    <option value="fair">可</option>
                                </select>
                            </div>
                            
                            <div class="mb-3">
                                <label for="weight" class="form-label">重量</label>
                                <div class="input-group">
                                <input type="number" class="form-control form-control-light" 
                                           id="weight" name="weight" min="0" step="0.01" placeholder="例: 1.5">
                                    <select class="form-select form-control-light" id="weightUnit" name="weight_unit" style="max-width: 80px;">
                                        <option value="kg" selected>kg</option>
                                        <option value="g">g</option>
                                    </select>
                                </div>
                            </div>
                        </div>
                    </div>

                    <div class="card card-light mb-4">
                        <div class="card-header card-header-light">
                            <h5 class="mb-0">レンタル設定（任意）</h5>
                        </div>
                        <div class="card-body">
                            <div class="form-check mb-3">
                                <input class="form-check-input" type="checkbox" id="isRental" name="is_rental" value="1">
                                <label class="form-check-label" for="isRental">レンタル可能にする</label>
                            </div>
                            
                            <div id="rentalSettings" style="display: none;">
                                <p class="text-muted small mb-3">レンタル期間を選択してください</p>
                                
                                <!-- Daily Rental -->
                                <div class="mb-3">
                                    <div class="form-check mb-2">
                                        <input class="form-check-input" type="checkbox" id="enableRentalDaily" name="enable_rental_daily">
                                        <label class="form-check-label" for="enableRentalDaily">
                                            <i class="bi bi-calendar-day me-1"></i>日単位レンタル
                                        </label>
                                    </div>
                                    <div id="rentalDailyFields" style="display: none;" class="ms-4">
                                        <label for="rentalPriceDaily" class="form-label small">1日あたりの価格</label>
                                        <div class="input-group">
                                            <span class="input-group-text input-group-text-light">¥</span>
                                            <input type="number" class="form-control form-control-light" 
                                                   id="rentalPriceDaily" name="rental_price_daily" min="0" step="100" placeholder="1000">
                                        </div>
                                    </div>
                                </div>
                                
                                <!-- Weekly Rental -->
                                <div class="mb-3">
                                    <div class="form-check mb-2">
                                        <input class="form-check-input" type="checkbox" id="enableRentalWeekly" name="enable_rental_weekly">
                                        <label class="form-check-label" for="enableRentalWeekly">
                                            <i class="bi bi-calendar-week me-1"></i>週単位レンタル
                                        </label>
                                    </div>
                                    <div id="rentalWeeklyFields" style="display: none;" class="ms-4">
                                        <label for="rentalPriceWeekly" class="form-label small">1週間あたりの価格</label>
                                        <div class="input-group">
                                            <span class="input-group-text input-group-text-light">¥</span>
                                            <input type="number" class="form-control form-control-light" 
                                                   id="rentalPriceWeekly" name="rental_price_weekly" min="0" step="100" placeholder="6000">
                                        </div>
                                    </div>
                                </div>
                                
                                <!-- Monthly Rental -->
                                <div class="mb-3">
                                    <div class="form-check mb-2">
                                        <input class="form-check-input" type="checkbox" id="enableRentalMonthly" name="enable_rental_monthly">
                                        <label class="form-check-label" for="enableRentalMonthly">
                                            <i class="bi bi-calendar-month me-1"></i>月単位レンタル
                                        </label>
                                    </div>
                                    <div id="rentalMonthlyFields" style="display: none;" class="ms-4">
                                        <label for="rentalPriceMonthly" class="form-label small">1ヶ月あたりの価格</label>
                                        <div class="input-group">
                                            <span class="input-group-text input-group-text-light">¥</span>
                                            <input type="number" class="form-control form-control-light" 
                                                   id="rentalPriceMonthly" name="rental_price_monthly" min="0" step="1000" placeholder="20000">
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <div class="card card-light">
                        <div class="card-header card-header-light">
                            <h5 class="mb-0">商品仕様（任意）</h5>
                        </div>
                        <div class="card-body">
                            <div id="specificationsContainer">
                                <div class="specification-item d-flex gap-2 mb-2">
                                    <input type="text" class="form-control form-control-light spec-name" 
                                           placeholder="仕様名（例: ブランド）">
                                    <input type="text" class="form-control form-control-light spec-value" 
                                           placeholder="値（例: Apple）">
                                    <button type="button" class="btn btn-outline-danger" onclick="removeSpecification(this)">×</button>
                                </div>
                            </div>
                            <button type="button" class="btn btn-outline-primary btn-sm" onclick="addSpecification()">
                                <i class="bi bi-plus"></i> 仕様を追加
                            </button>
                        </div>
                    </div>
                </div>
            </div>

            <div class="d-flex justify-content-between align-items-center mt-4">
                <button type="button" class="btn btn-outline-secondary" onclick="saveDraft()">
                    <i class="bi bi-save"></i> 下書き保存
                </button>
                <button type="submit" class="btn btn-primary" id="submitBtn">
                    <span id="submitText">
                        <i class="bi bi-check-circle"></i> 商品を出品
                    </span>
                    <span id="submitLoader" class="spinner-border spinner-border-sm d-none" role="status"></span>
                </button>
            </div>
            
            <div id="formError" class="alert alert-danger d-none mt-3" role="alert"></div>
            <div id="formSuccess" class="alert alert-success d-none mt-3" role="alert"></div>
        </form>
    </div>
</main>

<script src="${pageContext.request.contextPath}/js/create-listing.js"></script>
<%@ include file="includes/footer.jsp" %>
