<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="includes/header.jsp" %>

<main class="auth-page">
    <div class="container">
        <div class="row justify-content-center">
            <div class="col-md-6 col-lg-5">
                <div class="card auth-box shadow-lg">
                    <div class="card-body p-4 p-md-5">
                        <div class="auth-header text-center mb-4">
                            <h2 class="fw-bold mb-2">
                                <i class="bi bi-box-arrow-in-right"></i> ログイン
                            </h2>
                            <p class="text-muted mb-0">アカウントにログインしてください</p>
                        </div>
                        
                        <form id="loginForm" action="${pageContext.request.contextPath}/LoginServlet" method="POST">
                            <div class="mb-3">
                                <label for="emailOrUser" class="form-label">
                                    ユーザー名またはメールアドレス <span class="text-danger">*</span>
                                </label>
                                <div class="input-group">
                                    <span class="input-group-text input-group-text-light">
                                        <i class="bi bi-person"></i>
                                    </span>
                                    <input type="text" class="form-control form-control-light" 
                                           id="emailOrUser" name="emailOrUser" required 
                                           placeholder="ユーザー名またはメールアドレスを入力"
                                           autocomplete="username">
                                </div>
                                <small class="form-text text-muted" id="usernameHelp"></small>
                            </div>
                            
                            <div class="mb-3">
                                <label for="password" class="form-label">
                                    パスワード <span class="text-danger">*</span>
                                </label>
                                <div class="input-group">
                                    <span class="input-group-text input-group-text-light">
                                        <i class="bi bi-lock"></i>
                                    </span>
                                    <input type="password" class="form-control form-control-light" 
                                           id="password" name="password" required 
                                           placeholder="パスワードを入力"
                                           autocomplete="current-password">
                                    <button type="button" class="btn btn-outline-secondary" 
                                            id="togglePassword" aria-label="パスワードを表示">
                                        <i class="bi bi-eye" id="eyeIcon"></i>
                                    </button>
                                </div>
                                <small class="form-text text-muted" id="passwordHelp"></small>
                            </div>
                            
                            <div class="d-flex flex-wrap align-items-center gap-3 mb-4 login-options-row">
                                <div class="form-check flex-shrink-0">
                                    <input class="form-check-input" type="checkbox" name="remember" id="remember">
                                    <label class="form-check-label text-nowrap" for="remember">
                                        ログイン状態を保持
                                    </label>
                                </div>
                                <a href="${pageContext.request.contextPath}/forgot-password.jsp" class="text-decoration-none text-primary flex-shrink-0">
                                    パスワードを忘れた場合
                                </a>
                            </div>
                            
                            <div id="loginError" class="alert alert-danger d-none" role="alert"></div>
                            <div id="loginSuccess" class="alert alert-success d-none" role="alert"></div>
                            
                            <button type="submit" class="btn btn-primary w-100 py-2 mb-3" id="loginSubmitBtn">
                                <span id="loginSubmitText">
                                    <i class="bi bi-box-arrow-in-right"></i> ログイン
                                </span>
                                <span id="loginSubmitLoader" class="spinner-border spinner-border-sm d-none" role="status"></span>
                            </button>
                        </form>
                        
                        <div class="text-center mb-3">
                            <span class="text-muted">または</span>
                        </div>
                        
                        <div class="d-grid gap-2 mb-3">
                            <button type="button" class="btn btn-outline-light" disabled>
                                <i class="bi bi-google"></i> Googleでログイン
                            </button>
                            <button type="button" class="btn btn-outline-light" disabled>
                                <i class="bi bi-facebook"></i> Facebookでログイン
                            </button>
                        </div>
                        
                        <div class="text-center">
                            <p class="text-muted mb-0">
                                アカウントをお持ちでない方は 
                                <a href="${pageContext.request.contextPath}/register.jsp" class="text-primary text-decoration-none">新規登録</a>
                            </p>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</main>

<script src="${pageContext.request.contextPath}/js/auth.js"></script>
<%@ include file="includes/footer.jsp" %>
