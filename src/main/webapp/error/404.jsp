<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="../includes/header.jsp" %>

<main class="error-page-main page-main py-5">
    <div class="container text-center">
        <div class="row justify-content-center">
            <div class="col-md-6">
                <h1 class="display-1 fw-bold text-primary">404</h1>
                <h2 class="mb-4">ページが見つかりません</h2>
                <p class="text-muted mb-4">お探しのページは存在しないか、移動または削除された可能性があります。</p>
                <a href="${pageContext.request.contextPath}/index.jsp" class="btn btn-primary">
                    <i class="bi bi-house"></i> ホームに戻る
                </a>
            </div>
        </div>
    </div>
</main>

<%@ include file="../includes/footer.jsp" %>

