<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="includes/header.jsp" %>
<%
    if (currentUser == null) {
        response.sendRedirect("login.jsp?redirect=rental-detail");
        return;
    }
%>

<style>
.rental-detail-page {
    min-height: calc(100vh - 200px);
}
.rental-detail-card {
    background: var(--card-bg);
    border: 1px solid var(--border-color);
    border-radius: 16px;
    box-shadow: var(--shadow);
    overflow: hidden;
}
.rental-detail-image {
    width: 100%;
    height: 100%;
    min-height: 280px;
    object-fit: cover;
    border-radius: 12px;
    background: var(--bg-secondary);
    box-shadow: var(--shadow);
}
.rental-detail-info-card {
    background: var(--bg-secondary);
    border-radius: 12px;
    padding: 1.25rem;
    border: 1px solid var(--border-color);
}
.rental-detail-info-row {
    display: flex;
    justify-content: space-between;
    padding: 0.5rem 0;
    border-bottom: 1px solid var(--border-light);
}
.rental-detail-info-row:last-child {
    border-bottom: none;
}
.rental-detail-price {
    font-size: 1.75rem;
    font-weight: 700;
    color: var(--bs-warning);
}
.rental-detail-actions {
    padding: 1.5rem 0;
    border-top: 1px solid var(--border-color);
    margin-top: 1.5rem;
}
@media (max-width: 768px) {
    .rental-detail-image {
        min-height: 220px;
    }
}
</style>

<main class="page-main py-4 rental-detail-page">
    <div class="container">
        <div class="d-flex justify-content-between align-items-center mb-4 flex-wrap gap-2">
            <h1 class="fw-bold mb-0 page-header">
                <i class="bi bi-calendar-check me-2 text-warning"></i>レンタル詳細
            </h1>
            <a href="${pageContext.request.contextPath}/rentals.jsp" class="btn btn-outline-primary">
                <i class="bi bi-arrow-left me-2"></i>レンタル履歴に戻る
            </a>
        </div>
        
        <div id="rentalDetail" class="rental-detail-card">
            <div class="card-body p-4">
                <div class="text-center py-5">
                    <div class="spinner-border text-primary" role="status" style="width: 3rem; height: 3rem;">
                        <span class="visually-hidden">読み込み中...</span>
                    </div>
                    <p class="text-muted mt-3 mb-0">レンタル詳細を読み込んでいます...</p>
                </div>
            </div>
        </div>
    </div>
</main>

<script src="${pageContext.request.contextPath}/js/rental-detail.js"></script>
<%@ include file="includes/footer.jsp" %>
