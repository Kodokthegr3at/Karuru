<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="includes/header.jsp" %>
<%
    if (currentUser == null) {
        response.sendRedirect("login.jsp?redirect=orders");
        return;
    }
%>

<main class="page-main py-4 orders-page">
    <div class="container">
        <h1 class="fw-bold mb-4">
            <i class="bi bi-receipt"></i> 注文履歴
        </h1>
        <div class="card card-light mb-4">
            <div class="card-header card-header-light">
                <div class="d-flex justify-content-between align-items-center">
                    <h5 class="mb-0">フィルター</h5>
                    <select class="form-select form-control-light" id="statusFilter" style="width: 200px;">
                        <option value="">すべての注文</option>
                        <option value="pending">保留中</option>
                        <option value="confirmed">確定済み</option>
                        <option value="processing">処理中</option>
                        <option value="shipped">発送済み</option>
                        <option value="delivered">配送済み</option>
                        <option value="cancelled">キャンセル</option>
                    </select>
                </div>
            </div>
            <div class="card-body">
                <div id="ordersList">
                    <!-- Orders will be loaded via JavaScript -->
                </div>
            </div>
        </div>
    </div>
</main>

<script src="${pageContext.request.contextPath}/js/orders.js"></script>
<%@ include file="includes/footer.jsp" %>
