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
                                <i class="bi bi-key-fill"></i> パスワードをリセット
                            </h2>
                            <p class="text-muted mb-0">新しいパスワードを設定してください</p>
                        </div>
                        
                        <form id="resetPasswordForm" action="${pageContext.request.contextPath}/ResetPasswordServlet" method="POST">
                            <input type="hidden" id="code" name="code" value="<%= request.getParameter("code") != null ? request.getParameter("code") : "" %>">
                            
                            <div class="mb-3">
                                <label for="password" class="form-label">
                                    新しいパスワード <span class="text-danger">*</span>
                                </label>
                                <div class="input-group">
                                    <span class="input-group-text input-group-text-light">
                                        <i class="bi bi-lock"></i>
                                    </span>
                                    <input type="password" class="form-control form-control-light" 
                                           id="password" name="password" required 
                                           placeholder="パスワードを入力（6文字以上）"
                                           minlength="6"
                                           autocomplete="new-password">
                                    <button type="button" class="btn btn-outline-secondary border-secondary" 
                                            id="togglePassword" aria-label="パスワードを表示">
                                        <i class="bi bi-eye" id="eyeIcon"></i>
                                    </button>
                                </div>
                                <div class="password-strength mt-2">
                                    <div class="strength-bar">
                                        <div class="strength-fill" id="strengthFill"></div>
                                    </div>
                                    <span class="strength-text" id="strengthText">パスワード強度</span>
                                </div>
                                <small class="form-text text-muted" id="passwordHelp">6文字以上、英数字を含むことを推奨</small>
                            </div>
                            
                            <div class="mb-4">
                                <label for="confirmPassword" class="form-label">
                                    パスワード（確認） <span class="text-danger">*</span>
                                </label>
                                <div class="input-group">
                                    <span class="input-group-text input-group-text-light">
                                        <i class="bi bi-lock-fill"></i>
                                    </span>
                                    <input type="password" class="form-control form-control-light" 
                                           id="confirmPassword" name="confirmPassword" required 
                                           placeholder="パスワードを再入力"
                                           autocomplete="new-password">
                                    <button type="button" class="btn btn-outline-secondary border-secondary" 
                                            id="toggleConfirmPassword" aria-label="パスワードを表示">
                                        <i class="bi bi-eye" id="eyeIconConfirm"></i>
                                    </button>
                                </div>
                                <small class="form-text text-muted" id="confirmPasswordHelp"></small>
                            </div>
                            
                            <div id="resetPasswordError" class="alert alert-danger d-none" role="alert"></div>
                            <div id="resetPasswordSuccess" class="alert alert-success d-none" role="alert"></div>
                            
                            <button type="submit" class="btn btn-primary w-100 py-2 mb-3" id="submitBtn">
                                <span id="submitText">
                                    <i class="bi bi-key-fill"></i> パスワードをリセット
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

<script src="${pageContext.request.contextPath}/js/reset-password.js"></script>
<%@ include file="includes/footer.jsp" %>

