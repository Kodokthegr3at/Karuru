<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="pageTitle" value="新規登録"/>
<%@ include file="includes/header.jsp" %>

<main id="main" class="auth-page">
    <div class="auth-card panel">
        <h1>新規登録</h1>
        <div class="alert alert-danger" id="registerError" role="alert" hidden></div>

        <form id="registerForm" action="${pageContext.request.contextPath}/RegisterServlet" method="post" novalidate>
            <div class="mb-3">
                <label class="form-label" for="username">ユーザー名</label>
                <input class="form-control" id="username" name="username" autocomplete="username" required
                       minlength="3" maxlength="50" pattern="[A-Za-z0-9_]+" aria-describedby="usernameHelp">
                <div class="form-text" id="usernameHelp">半角英数字と _ で3文字以上。プロフィールに表示されます。</div>
            </div>
            <div class="mb-3">
                <label class="form-label" for="email">メールアドレス</label>
                <input class="form-control" type="email" id="email" name="email" autocomplete="email" required
                       aria-describedby="emailHelp">
                <div class="form-text" id="emailHelp">確認メールを送ります。</div>
            </div>
            <div class="mb-3">
                <label class="form-label" for="password">パスワード</label>
                <div class="input-group">
                    <input class="form-control" type="password" id="password" name="password" autocomplete="new-password"
                           required minlength="6" aria-describedby="passwordHelp">
                    <button class="btn btn-outline-secondary" type="button" data-toggle-password="password" aria-label="パスワードを表示">
                        <i class="bi bi-eye"></i>
                    </button>
                </div>
                <div class="form-text" id="passwordHelp">6文字以上</div>
            </div>
            <div class="mb-3">
                <label class="form-label" for="confirmPassword">パスワード（確認）</label>
                <input class="form-control" type="password" id="confirmPassword" name="confirmPassword"
                       autocomplete="new-password" required>
            </div>
            <div class="mb-3">
                <label class="form-label" for="fullName">お名前（任意）</label>
                <input class="form-control" id="fullName" name="fullName" autocomplete="name" maxlength="100">
            </div>
            <div class="form-check mb-4">
                <input class="form-check-input" type="checkbox" id="agreeTerms" required>
                <label class="form-check-label" for="agreeTerms">
                    <a href="${pageContext.request.contextPath}/terms.jsp" target="_blank">利用規約</a>と<a href="${pageContext.request.contextPath}/privacy.jsp" target="_blank">プライバシーポリシー</a>に同意します
                </label>
            </div>
            <button class="btn btn-primary btn-lg w-100" type="submit">登録する</button>
        </form>

        <p class="auth-switch">アカウントをお持ちの方は <a href="${pageContext.request.contextPath}/login.jsp">ログイン</a></p>
    </div>
</main>

<script src="${pageContext.request.contextPath}/js/auth.js"></script>
<%@ include file="includes/footer.jsp" %>
