<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
    // Check admin access
    try {
        if (session == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp?redirect=admin/banners.jsp");
            return;
        }
        
        String userRole = (String) session.getAttribute("role");
        if (userRole == null || !"admin".equals(userRole)) {
            response.sendRedirect(request.getContextPath() + "/index.jsp");
            return;
        }
    } catch (Exception e) {
        e.printStackTrace();
        response.sendRedirect(request.getContextPath() + "/login.jsp?redirect=admin/banners.jsp");
        return;
    }
%>
<%@ include file="../includes/header.jsp" %>

<main class="bg-dark text-light py-4">
    <div class="container">
        <div class="d-flex justify-content-between align-items-center mb-4">
            <h1 class="fw-bold mb-0">
                <i class="bi bi-image me-2"></i>バナー管理
            </h1>
            <button class="btn btn-primary" onclick="openBannerModal(null)">
                <i class="bi bi-plus-circle me-1"></i>新規バナー
            </button>
        </div>

        <!-- Banners List -->
        <div class="card bg-black border-secondary">
            <div class="card-body">
                <div id="bannersList" class="table-responsive">
                    <table class="table table-dark table-hover">
                        <thead>
                            <tr>
                                <th>ID</th>
                                <th>タイトル</th>
                                <th>画像</th>
                                <th>位置</th>
                                <th>表示順</th>
                                <th>状態</th>
                                <th>開始日</th>
                                <th>終了日</th>
                                <th>操作</th>
                            </tr>
                        </thead>
                        <tbody id="bannersTableBody">
                            <tr>
                                <td colspan="9" class="text-center py-5">
                                    <div class="spinner-border text-primary" role="status">
                                        <span class="visually-hidden">読み込み中...</span>
                                    </div>
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    </div>
</main>

<!-- Banner Modal -->
<div class="modal fade" id="bannerModal" tabindex="-1" aria-labelledby="bannerModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-lg">
        <div class="modal-content bg-dark text-light border-secondary">
            <div class="modal-header border-secondary">
                <h5 class="modal-title" id="bannerModalLabel">新規バナー</h5>
                <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <div class="modal-body">
                <form id="bannerForm">
                    <input type="hidden" id="bannerId" name="banner_id">
                    
                    <div class="mb-3">
                        <label for="bannerTitle" class="form-label">タイトル <span class="text-danger">*</span></label>
                        <input type="text" class="form-control bg-black text-light border-secondary" id="bannerTitle" name="title" required>
                    </div>
                    
                    <div class="mb-3">
                        <label for="bannerImageUrl" class="form-label">画像URL <span class="text-danger">*</span></label>
                        <input type="text" class="form-control bg-black text-light border-secondary" id="bannerImageUrl" name="image_url" required>
                        <small class="text-muted">例: /banners/welcome.jpg</small>
                    </div>
                    
                    <div class="mb-3">
                        <label for="bannerLinkUrl" class="form-label">リンクURL</label>
                        <input type="text" class="form-control bg-black text-light border-secondary" id="bannerLinkUrl" name="link_url" placeholder="${pageContext.request.contextPath}/products.jsp">
                        <small class="text-muted d-block mt-1">
                            <strong>例:</strong><br>
                            • コンテキストパスを含む: <code>${pageContext.request.contextPath}/products.jsp</code><br>
                            • 相対パス: <code>/products.jsp</code> または <code>products.jsp</code><br>
                            • 外部URL: <code>https://example.com</code>
                        </small>
                    </div>
                    
                    <div class="row">
                        <div class="col-md-6 mb-3">
                            <label for="bannerPosition" class="form-label">位置</label>
                            <select class="form-select bg-black text-light border-secondary" id="bannerPosition" name="position">
                                <option value="home_top">ホーム上部</option>
                                <option value="home_middle">ホーム中央</option>
                                <option value="category">カテゴリー</option>
                                <option value="product">商品</option>
                            </select>
                        </div>
                        
                        <div class="col-md-6 mb-3">
                            <label for="bannerDisplayOrder" class="form-label">表示順</label>
                            <input type="number" class="form-control bg-black text-light border-secondary" id="bannerDisplayOrder" name="display_order" value="0" min="0">
                        </div>
                    </div>
                    
                    <div class="row">
                        <div class="col-md-6 mb-3">
                            <label for="bannerStartDate" class="form-label">開始日</label>
                            <input type="datetime-local" class="form-control bg-black text-light border-secondary" id="bannerStartDate" name="start_date">
                        </div>
                        
                        <div class="col-md-6 mb-3">
                            <label for="bannerEndDate" class="form-label">終了日</label>
                            <input type="datetime-local" class="form-control bg-black text-light border-secondary" id="bannerEndDate" name="end_date">
                        </div>
                    </div>
                    
                    <div class="mb-3">
                        <div class="form-check form-switch">
                            <input class="form-check-input" type="checkbox" id="bannerIsActive" name="is_active" checked>
                            <label class="form-check-label" for="bannerIsActive">アクティブ</label>
                        </div>
                    </div>
                </form>
            </div>
            <div class="modal-footer border-secondary">
                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">キャンセル</button>
                <button type="button" class="btn btn-primary" onclick="saveBanner()">保存</button>
            </div>
        </div>
    </div>
</div>

<script src="${pageContext.request.contextPath}/js/admin-banners.js"></script>
<%@ include file="../includes/footer.jsp" %>

