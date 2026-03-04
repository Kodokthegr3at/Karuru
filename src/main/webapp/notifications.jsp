<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="includes/header.jsp" %>
<%
    if (currentUser == null) {
        response.sendRedirect("login.jsp?redirect=notifications");
        return;
    }
%>

<main class="page-main py-4 notifications-page">
    <div class="container-fluid px-4">
        <div class="d-flex justify-content-between align-items-center mb-4">
            <h1 class="h2 mb-0 fw-bold">
                <i class="bi bi-bell-fill me-2 text-primary"></i>通知
            </h1>
            <button class="btn btn-outline-primary rounded-pill" onclick="markAllAsRead()">
                <i class="bi bi-check-all me-2"></i>すべて既読にする
            </button>
        </div>
        
        <!-- Filter Buttons -->
        <div class="notifications-filters mb-4">
            <div class="btn-group" role="group">
                <button class="btn btn-outline-secondary filter-btn active" data-filter="all">
                    <i class="bi bi-list-ul me-1"></i>すべて
                </button>
                <button class="btn btn-outline-secondary filter-btn" data-filter="unread">
                    <i class="bi bi-envelope me-1"></i>未読
                </button>
                <button class="btn btn-outline-secondary filter-btn" data-filter="order">
                    <i class="bi bi-box-seam me-1"></i>注文
                </button>
                <button class="btn btn-outline-secondary filter-btn" data-filter="message">
                    <i class="bi bi-chat-dots me-1"></i>メッセージ
                </button>
                <button class="btn btn-outline-secondary filter-btn" data-filter="system">
                    <i class="bi bi-gear me-1"></i>システム
                </button>
            </div>
        </div>
        
        <!-- Notifications List -->
        <div class="card shadow-lg border-0 notifications-card">
            <div class="card-body p-0">
                <div id="notificationsList" class="notifications-list">
                    <div class="text-center p-4">
                        <div class="spinner-border text-primary" role="status">
                            <span class="visually-hidden">読み込み中...</span>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</main>

<script src="${pageContext.request.contextPath}/js/notifications.js"></script>
<%@ include file="includes/footer.jsp" %>
