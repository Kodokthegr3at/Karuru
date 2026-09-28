<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="pageTitle" value="新しいパスワード"/>
<%@ include file="includes/header.jsp" %>

<main id="main" class="auth-page">
    <div class="auth-card panel">
        <h1>新しいパスワード</h1>
        <c:choose>
            <c:when test="${empty param.code}">
                <div class="alert alert-danger" role="alert">リンクが正しくありません。メールのリンクをもう一度開いてください。</div>
            </c:when>
            <c:when test="${param.error == 'password_short'}"><div class="alert alert-danger" role="alert">パスワードは6文字以上にしてください。</div></c:when>
            <c:when test="${param.error == 'password_mismatch'}"><div class="alert alert-danger" role="alert">確認用のパスワードが一致しません。</div></c:when>
            <c:when test="${not empty param.error}"><div class="alert alert-danger" role="alert">パスワードを変更できませんでした。もう一度お試しください。</div></c:when>
        </c:choose>
        <c:if test="${not empty param.code}">
        <form action="${pageContext.request.contextPath}/ResetPasswordServlet" method="post">
            <input type="hidden" name="code" value="<c:out value='${param.code}'/>">
            <div class="mb-3">
                <label class="form-label" for="password">新しいパスワード</label>
                <div class="input-group">
                    <input class="form-control" type="password" id="password" name="password" autocomplete="new-password"
                           minlength="6" required autofocus>
                    <button class="btn btn-outline-secondary" type="button" data-toggle-password="password" aria-label="パスワードを表示">
                        <i class="bi bi-eye"></i>
                    </button>
                </div>
                <div class="form-text">6文字以上</div>
            </div>
            <div class="mb-4">
                <label class="form-label" for="confirmPassword">新しいパスワード（確認）</label>
                <input class="form-control" type="password" id="confirmPassword" name="confirmPassword" autocomplete="new-password" required>
            </div>
            <button class="btn btn-primary btn-lg w-100" type="submit">パスワードを変更する</button>
        </form>
        </c:if>
    </div>
</main>

<script src="${pageContext.request.contextPath}/js/auth.js"></script>
<%@ include file="includes/footer.jsp" %>
