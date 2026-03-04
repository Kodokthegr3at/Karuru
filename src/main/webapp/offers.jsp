<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="includes/header.jsp" %>
<%
    if (currentUser == null) {
        response.sendRedirect("login.jsp?redirect=offers");
        return;
    }
%>

<main class="page-main py-4 offers-page">
    <div class="container">
        <div class="d-flex justify-content-between align-items-center mb-4">
            <h1 class="fw-bold mb-0">
                <i class="bi bi-hand-thumbs-up me-2 text-warning"></i>オファー管理
            </h1>
            <div class="btn-group" role="group">
                <input type="radio" class="btn-check" name="offerView" id="viewReceived" value="received" checked>
                <label class="btn btn-outline-primary" for="viewReceived">
                    <i class="bi bi-inbox me-2"></i>受信したオファー
                </label>
                <input type="radio" class="btn-check" name="offerView" id="viewSent" value="sent">
                <label class="btn btn-outline-primary" for="viewSent">
                    <i class="bi bi-send me-2"></i>送信したオファー
                </label>
            </div>
        </div>

        <!-- Filter Tabs -->
        <ul class="nav nav-tabs mb-4 border-secondary" role="tablist">
            <li class="nav-item" role="presentation">
                <button class="nav-link active" id="all-tab" data-bs-toggle="tab" data-bs-target="#all" type="button" role="tab" data-status="">
                    すべて
                </button>
            </li>
            <li class="nav-item" role="presentation">
                <button class="nav-link" id="pending-tab" data-bs-toggle="tab" data-bs-target="#pending" type="button" role="tab" data-status="pending">
                    保留中
                </button>
            </li>
            <li class="nav-item" role="presentation">
                <button class="nav-link" id="accepted-tab" data-bs-toggle="tab" data-bs-target="#accepted" type="button" role="tab" data-status="accepted">
                    承認済み
                </button>
            </li>
            <li class="nav-item" role="presentation">
                <button class="nav-link" id="rejected-tab" data-bs-toggle="tab" data-bs-target="#rejected" type="button" role="tab" data-status="rejected">
                    拒否済み
                </button>
            </li>
        </ul>

        <!-- Offers Container -->
        <div id="offersContainer">
            <div class="text-center py-5">
                <div class="spinner-border text-primary" role="status">
                    <span class="visually-hidden">読み込み中...</span>
                </div>
            </div>
        </div>
    </div>
</main>

<script src="${pageContext.request.contextPath}/js/offers.js"></script>
<%@ include file="includes/footer.jsp" %>

