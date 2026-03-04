<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="includes/header.jsp" %>
<%
    if (currentUser == null) {
        response.sendRedirect("login.jsp?redirect=favorites");
        return;
    }
%>

<main class="page-main py-4 favorites-page">
    <div class="container">
        <h1 class="fw-bold mb-4">
            <i class="bi bi-heart"></i> お気に入り
        </h1>
        <div id="favoritesContainer">
            <div class="row g-4" id="favoritesGrid" style="display: flex;">
                <!-- Favorite products will be loaded via JavaScript -->
                <div class="col-12 text-center py-5">
                    <div class="spinner-border text-primary" role="status">
                        <span class="visually-hidden">読み込み中...</span>
                    </div>
                </div>
            </div>
            <div id="emptyFavorites" class="empty-state-container" style="display: none;">
                <div class="empty-state-content">
                    <i class="bi bi-heart empty-state-icon"></i>
                    <h3 class="empty-state-title">お気に入りに商品がありません</h3>
                    <p class="empty-state-description">気に入った商品をお気に入りに追加しましょう</p>
                    <a href="${pageContext.request.contextPath}/products.jsp" class="btn btn-primary btn-lg empty-state-button">
                        <i class="bi bi-bag me-2"></i>商品を見る
                    </a>
                </div>
            </div>
        </div>
    </div>
</main>

<script src="${pageContext.request.contextPath}/js/favorites.js"></script>
<%@ include file="includes/footer.jsp" %>
