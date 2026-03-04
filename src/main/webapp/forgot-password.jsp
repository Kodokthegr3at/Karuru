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
                                <i class="bi bi-key"></i> パスワードを忘れた場合
                            </h2>
                            <p class="text-muted mb-0">パスワードリセット用のリンクをメールで送信します</p>
                        </div>
                        
                        <div class="alert alert-info d-flex align-items-center mb-4" role="alert">
                            <i class="bi bi-info-circle me-2"></i>
                            <div>登録済みのメールアドレスを入力してください。パスワードリセット用のリンクを送信します。</div>
                        </div>
                        
                        <form id="forgotPasswordForm" action="${pageContext.request.contextPath}/ForgotPasswordServlet" method="POST">
                            <div class="mb-3">
                                <label for="email" class="form-label">
                                    メールアドレス <span class="text-danger">*</span>
                                </label>
                                <div class="input-group">
                                    <span class="input-group-text input-group-text-light">
                                        <i class="bi bi-envelope"></i>
                                    </span>
                                    <input type="email" class="form-control form-control-light" 
                                           id="email" name="email" required 
                                           placeholder="登録時のメールアドレスを入力"
                                           autocomplete="email">
                                </div>
                                <small class="form-text text-muted" id="emailHelp">登録時に使用したメールアドレスを入力してください</small>
                            </div>
                            
                            <div id="forgotPasswordError" class="alert alert-danger d-none" role="alert"></div>
                            <div id="forgotPasswordSuccess" class="alert alert-success d-none" role="alert"></div>
                            
                            <button type="submit" class="btn btn-primary w-100 py-2 mb-3" id="submitBtn">
                                <span id="submitText">
                                    <i class="bi bi-send"></i> リセットリンクを送信
                                </span>
                                <span id="submitLoader" class="spinner-border spinner-border-sm d-none" role="status"></span>
                            </button>
                        </form>
                        
                        <div class="text-center">
                            <a href="${pageContext.request.contextPath}/login.jsp" class="text-primary text-decoration-none">
                                <i class="bi bi-arrow-left"></i> ログインページに戻る
                            </a>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</main>

<script src="${pageContext.request.contextPath}/js/forgot-password.js"></script>
<%@ include file="includes/footer.jsp" %>
