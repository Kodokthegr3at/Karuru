<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="includes/header.jsp" %>

<main class="bg-light py-4 product-detail-page" style="min-height: calc(100vh - 76px);">
    <div class="container container-product-detail">
        <div id="productDetail" class="mb-5">
            <!-- Product details will be loaded via JavaScript -->
            <div class="text-center py-5">
                <div class="spinner-border text-primary" role="status">
                    <span class="visually-hidden">読み込み中...</span>
                </div>
            </div>
        </div>

        <!-- Product Reviews Section -->
        <section class="mb-5">
            <div class="card bg-white border">
                <div class="card-header bg-light border-bottom">
                    <h3 class="mb-0">
                        <i class="bi bi-star-fill text-warning me-2"></i>レビュー
                    </h3>
                </div>
                <div class="card-body">
                    <div id="reviewsContainer">
                        <div class="text-center py-4">
                            <div class="spinner-border text-primary" role="status">
                                <span class="visually-hidden">読み込み中...</span>
                            </div>
                        </div>
                    </div>
                    <% if (currentUser != null) { %>
                        <div id="reviewFormContainer" class="mt-4 pt-4 border-top">
                            <h4 class="mb-3">
                                <i class="bi bi-pencil-square me-2"></i>レビューを書く
                            </h4>
                            <form id="reviewForm">
                                <div class="mb-3">
                                    <label for="reviewRating" class="form-label">評価 <span class="text-danger">*</span></label>
                                    <select class="form-select border" id="reviewRating" required>
                                        <option value="">選択してください</option>
                                        <option value="5">5 - 最高</option>
                                        <option value="4">4 - 良い</option>
                                        <option value="3">3 - 普通</option>
                                        <option value="2">2 - 悪い</option>
                                        <option value="1">1 - 最悪</option>
                                    </select>
                                    <small class="text-muted">上記の星をクリックして評価を選択することもできます</small>
                                </div>
                                <div class="mb-3">
                                    <label for="reviewText" class="form-label">レビュー内容 <span class="text-danger">*</span></label>
                                    <textarea class="form-control border" 
                                              id="reviewText" rows="5" 
                                              placeholder="商品の使用感、品質、配送などについてレビューを入力してください" required></textarea>
                                </div>
                                <button type="submit" class="btn btn-primary">
                                    <i class="bi bi-send me-2"></i>レビューを投稿
                                </button>
                            </form>
                        </div>
                    <% } else { %>
                        <div class="alert alert-info">
                            <i class="bi bi-info-circle me-2"></i>レビューを投稿するにはログインが必要です
                        </div>
                    <% } %>
                </div>
            </div>
        </section>

        <!-- Related Products Section -->
        <section>
            <h3 class="mb-4">
                <i class="bi bi-grid"></i> 関連商品
            </h3>
            <div class="row g-4" id="relatedProducts">
                <!-- Related products will be loaded via JavaScript -->
            </div>
        </section>
    </div>
</main>

<script src="${pageContext.request.contextPath}/js/product-detail.js"></script>
<%@ include file="includes/footer.jsp" %>
