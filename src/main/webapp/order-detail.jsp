<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="includes/header.jsp" %>
<%
    if (currentUser == null) {
        response.sendRedirect("login.jsp?redirect=order-detail");
        return;
    }
%>

<main class="page-main py-4 order-detail-page">
    <div class="container">
        <div class="d-flex justify-content-between align-items-center mb-4">
            <h1 class="fw-bold mb-0 page-header">
                <i class="bi bi-receipt-cutoff"></i> 注文詳細
            </h1>
            <a href="${pageContext.request.contextPath}/orders.jsp" class="btn btn-outline-primary">
                <i class="bi bi-arrow-left me-2"></i>注文履歴に戻る
            </a>
        </div>
        
        <div id="orderDetail" class="card card-light">
            <div class="card-body p-4">
                <div class="text-center py-5">
                    <div class="spinner-border text-primary" role="status">
                        <span class="visually-hidden">読み込み中...</span>
                    </div>
                </div>
            </div>
        </div>
    </div>
</main>

<script src="${pageContext.request.contextPath}/js/order-detail.js"></script>
<%@ include file="includes/footer.jsp" %>

