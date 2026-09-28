<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="pageTitle" value="パスワードの再設定"/>
<%@ include file="includes/header.jsp" %>

<main id="main" class="auth-page">
    <div class="auth-card panel">
        <h1>パスワードの再設定</h1>
        <c:choose>
            <c:when test="${not empty param.success and param.warning == 'email_failed'}">
                <div class="alert alert-warning" role="alert">メールを送信できませんでした。時間をおいてもう一度お試しください。</div>
            </c:when>
            <c:when test="${not empty param.success}">
                <div class="alert alert-success" role="status">登録されているメールアドレスであれば、再設定用のリンクを送信しました。メールをご確認ください。</div>
            </c:when>
            <c:when test="${param.error == 'empty_email' or param.error == 'invalid_email'}">
                <div class="alert alert-danger" role="alert">メールアドレスを正しく入力してください。</div>
            </c:when>
            <c:when test="${not empty param.error}">
                <div class="alert alert-danger" role="alert">エラーが発生しました。時間をおいてもう一度お試しください。</div>
            </c:when>
        </c:choose>
        <p class="text-muted">登録したメールアドレスに、パスワードを再設定するためのリンクを送ります。</p>
        <form action="${pageContext.request.contextPath}/ForgotPasswordServlet" method="post">
            <div class="mb-4">
                <label class="form-label" for="email">メールアドレス</label>
                <input class="form-control" type="email" id="email" name="email" autocomplete="email" required autofocus>
            </div>
            <button class="btn btn-primary btn-lg w-100" type="submit">再設定リンクを送る</button>
        </form>
        <p class="auth-switch"><a href="${pageContext.request.contextPath}/login.jsp">ログインに戻る</a></p>
    </div>
</main>

<%@ include file="includes/footer.jsp" %>
