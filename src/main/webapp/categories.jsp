<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="includes/header.jsp" %>

<main class="page-main py-4">
    <div class="container">
        <h1 class="fw-bold mb-4">
            <i class="bi bi-tags"></i> カテゴリー
        </h1>
        
        <div class="row g-4" id="categoriesGrid">
            <!-- Categories will be loaded via JavaScript -->
            <div class="col-12 text-center">
                <div class="spinner-border text-primary" role="status">
                    <span class="visually-hidden">読み込み中...</span>
                </div>
            </div>
        </div>
    </div>
</main>

<script src="${pageContext.request.contextPath}/js/categories.js"></script>
<%@ include file="includes/footer.jsp" %>

