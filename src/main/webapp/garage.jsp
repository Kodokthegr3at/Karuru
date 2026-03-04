<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="includes/header.jsp" %>
<%
    if (currentUser == null) {
        response.sendRedirect("login.jsp?redirect=garage");
        return;
    }
%>

<main class="page-main py-4">
    <div class="container">
        <div class="d-flex justify-content-between align-items-center mb-4">
            <h1 class="fw-bold mb-0">
                <i class="bi bi-box-seam"></i> マイガレージ
            </h1>
            <a href="${pageContext.request.contextPath}/dashboard.jsp" class="btn btn-outline-primary">
                <i class="bi bi-arrow-left me-2"></i>ダッシュボードに戻る
            </a>
        </div>
        
        <div class="card card-light">
            <div class="card-header card-header-light">
                <h5 class="mb-0">
                    <i class="bi bi-bag-check me-2"></i>購入済み商品
                </h5>
            </div>
            <div class="card-body">
                <div id="garageProducts" class="row g-4">
                    <div class="col-12 text-center py-5">
                        <div class="spinner-border text-primary" role="status">
                            <span class="visually-hidden">読み込み中...</span>
                        </div>
                    </div>
                </div>
                <div id="emptyGarage" class="empty-state-container" style="display: none;">
                    <div class="empty-state-content">
                        <i class="bi bi-box-seam empty-state-icon"></i>
                        <h3 class="empty-state-title">購入済み商品がありません</h3>
                        <p class="empty-state-description">商品を購入すると、ここに表示されます</p>
                        <a href="${pageContext.request.contextPath}/products.jsp" class="btn btn-primary btn-lg empty-state-button">
                            <i class="bi bi-bag me-2"></i>商品を見る
                        </a>
                    </div>
                </div>
            </div>
        </div>
    </div>
</main>

<script src="${pageContext.request.contextPath}/js/garage.js"></script>
<%@ include file="includes/footer.jsp" %>

