<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="pageTitle" value="ログイン"/>
<%@ include file="includes/header.jsp" %>

<main id="main" class="auth-page">
    <div class="auth-card panel">
        <h1>ログイン</h1>

        <%-- Outcome of LoginServlet, VerifyServlet and ResetPasswordServlet, passed back in the query string. --%>
        <c:choose>
            <c:when test="${param.error == 'invalid'}"><c:set var="error" value="ユーザー名（メールアドレス）またはパスワードが正しくありません。"/></c:when>
            <c:when test="${param.error == 'empty'}"><c:set var="error" value="ユーザー名とパスワードを入力してください。"/></c:when>
            <c:when test="${param.error == 'account_locked' and not empty param.minutes}"><c:set var="error" value="ログインに続けて失敗したため、アカウントを一時的にロックしています。約${param.minutes + 1}分後にお試しください。"/></c:when>
            <c:when test="${param.error == 'account_locked'}"><c:set var="error" value="ログインに続けて失敗したため、アカウントを30分間ロックしました。"/></c:when>
            <c:when test="${param.error == 'not_verified'}"><c:set var="error" value="メールアドレスの確認が済んでいません。登録時に届いたメールのリンクを開いてください。"/></c:when>
            <c:when test="${param.error == 'invalid_verification_code' or param.error == 'verification_failed'}"><c:set var="error" value="確認リンクが正しくありません。"/></c:when>
            <c:when test="${param.error == 'verification_expired'}"><c:set var="error" value="確認リンクの有効期限が切れています。"/></c:when>
            <c:when test="${param.error == 'invalid_reset_code' or param.error == 'reset_token_expired'}"><c:set var="error" value="パスワード再設定のリンクが無効か、有効期限が切れています。もう一度お試しください。"/></c:when>
            <c:when test="${not empty param.error}"><c:set var="error" value="エラーが発生しました。時間をおいてもう一度お試しください。"/></c:when>
        </c:choose>
        <c:choose>
            <c:when test="${param.success == 'verified'}"><c:set var="notice" value="メールアドレスを確認しました。ログインしてください。"/></c:when>
            <c:when test="${param.success == 'password_reset'}"><c:set var="notice" value="パスワードを変更しました。新しいパスワードでログインしてください。"/></c:when>
            <c:when test="${param.info == 'already_verified'}"><c:set var="notice" value="このメールアドレスは確認済みです。"/></c:when>
            <c:when test="${param.info == 'check_email'}"><c:set var="notice" value="登録ありがとうございます。確認メールのリンクを開くとログインできるようになります。"/></c:when>
        </c:choose>
        <c:if test="${not empty error}"><div class="alert alert-danger" role="alert"><c:out value="${error}"/></div></c:if>
        <c:if test="${not empty notice}"><div class="alert alert-success" role="status"><c:out value="${notice}"/></div></c:if>

        <form action="${pageContext.request.contextPath}/LoginServlet" method="post">
            <input type="hidden" name="redirect" value="<c:out value='${param.redirect}'/>">
            <div class="mb-3">
                <label class="form-label" for="emailOrUser">ユーザー名またはメールアドレス</label>
                <input class="form-control" id="emailOrUser" name="emailOrUser" autocomplete="username" required autofocus>
            </div>
            <div class="mb-3">
                <div class="d-flex justify-content-between">
                    <label class="form-label" for="password">パスワード</label>
                    <a class="small" href="${pageContext.request.contextPath}/forgot-password.jsp">パスワードを忘れた場合</a>
                </div>
                <div class="input-group">
                    <input class="form-control" type="password" id="password" name="password" autocomplete="current-password" required>
                    <button class="btn btn-outline-secondary" type="button" data-toggle-password="password" aria-label="パスワードを表示">
                        <i class="bi bi-eye"></i>
                    </button>
                </div>
            </div>
            <div class="form-check mb-4">
                <input class="form-check-input" type="checkbox" id="remember" name="remember">
                <label class="form-check-label" for="remember">ログインしたままにする</label>
            </div>
            <button class="btn btn-primary btn-lg w-100" type="submit">ログイン</button>
        </form>

        <p class="auth-switch">アカウントをお持ちでない方は <a href="${pageContext.request.contextPath}/register.jsp">新規登録</a></p>
    </div>
</main>

<script src="${pageContext.request.contextPath}/js/auth.js"></script>
<%@ include file="includes/footer.jsp" %>
