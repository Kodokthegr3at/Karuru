<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="includes/header.jsp" %>

<main class="auth-page">
    <div class="container">
        <div class="row justify-content-center">
            <div class="col-md-8 col-lg-7">
                <div class="card auth-box shadow-lg">
                    <div class="card-body p-4 p-md-5">
                        <div class="auth-header text-center mb-4">
                            <h2 class="fw-bold mb-2">
                                <i class="bi bi-person-plus"></i> 新規登録
                            </h2>
                            <p class="text-muted mb-0">新しいアカウントを作成してください</p>
                        </div>
                        
                        <form id="registerForm" action="${pageContext.request.contextPath}/RegisterServlet" method="POST">
                            <div class="row">
                                <div class="col-md-6 mb-3">
                                    <label for="username" class="form-label">
                                        ユーザー名 <span class="text-danger">*</span>
                                    </label>
                                    <div class="input-group">
                                        <span class="input-group-text input-group-text-light">
                                            <i class="bi bi-person"></i>
                                        </span>
                                        <input type="text" class="form-control form-control-light" 
                                               id="username" name="username" required 
                                               placeholder="ユーザー名を入力（3-20文字）"
                                               minlength="3" maxlength="20"
                                               pattern="[a-zA-Z0-9_]+"
                                               autocomplete="username">
                                    </div>
                                    <small class="form-text text-muted" id="usernameHelp">英数字とアンダースコアのみ使用可能（3-20文字）</small>
                                </div>
                                
                                <div class="col-md-6 mb-3">
                                    <label for="email" class="form-label">
                                        メールアドレス <span class="text-danger">*</span>
                                    </label>
                                    <div class="input-group">
                                        <span class="input-group-text input-group-text-light">
                                            <i class="bi bi-envelope"></i>
                                        </span>
                                        <input type="email" class="form-control form-control-light" 
                                               id="email" name="email" required 
                                               placeholder="example@email.com"
                                               autocomplete="email">
                                    </div>
                                    <small class="form-text text-muted" id="emailHelp">有効なメールアドレスを入力してください</small>
                                </div>
                            </div>
                            
                            <div class="row">
                                <div class="col-md-6 mb-3">
                                    <label for="password" class="form-label">
                                        パスワード <span class="text-danger">*</span>
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
                                        <button type="button" class="btn btn-outline-secondary" 
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
                                
                                <div class="col-md-6 mb-3">
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
                                        <button type="button" class="btn btn-outline-secondary" 
                                                id="toggleConfirmPassword" aria-label="パスワードを表示">
                                            <i class="bi bi-eye" id="eyeIconConfirm"></i>
                                        </button>
                                    </div>
                                    <small class="form-text text-muted" id="confirmPasswordHelp"></small>
                                </div>
                            </div>
                            
                            <div class="row">
                                <div class="col-md-6 mb-3">
                                    <label for="fullName" class="form-label">氏名（任意）</label>
                                    <div class="input-group">
                                        <span class="input-group-text input-group-text-light">
                                            <i class="bi bi-person-badge"></i>
                                        </span>
                                        <input type="text" class="form-control form-control-light" 
                                               id="fullName" name="fullName" 
                                               placeholder="氏名を入力"
                                               autocomplete="name">
                                    </div>
                                </div>
                                
                                <div class="col-md-6 mb-3">
                                    <label for="phone" class="form-label">電話番号（任意）</label>
                                    <div class="input-group">
                                        <span class="input-group-text input-group-text-light">
                                            <i class="bi bi-telephone"></i>
                                        </span>
                                        <input type="tel" class="form-control form-control-light" 
                                               id="phone" name="phone" 
                                               placeholder="090-1234-5678"
                                               pattern="[0-9\-]+"
                                               autocomplete="tel">
                                    </div>
                                </div>
                            </div>
                            
                            <div class="mb-4">
                                <div class="form-check">
                                    <input class="form-check-input" type="checkbox" id="agreeTerms" required>
                                    <label class="form-check-label" for="agreeTerms">
                                        <a href="${pageContext.request.contextPath}/terms.jsp" target="_blank" class="text-primary text-decoration-none">利用規約</a>と
                                        <a href="${pageContext.request.contextPath}/privacy.jsp" target="_blank" class="text-primary text-decoration-none">プライバシーポリシー</a>に同意します
                                    </label>
                                </div>
                            </div>
                            
                            <div id="registerError" class="alert alert-danger d-none" role="alert"></div>
                            <div id="registerSuccess" class="alert alert-success d-none" role="alert"></div>
                            
                            <button type="submit" class="btn btn-primary w-100 py-2 mb-3" id="registerSubmitBtn">
                                <span id="registerSubmitText">
                                    <i class="bi bi-person-plus"></i> アカウントを作成
                                </span>
                                <span id="registerSubmitLoader" class="spinner-border spinner-border-sm d-none" role="status"></span>
                            </button>
                        </form>
                        
                        <div class="text-center mb-3">
                            <span class="text-muted">または</span>
                        </div>
                        
                        <div class="d-grid gap-2 mb-3">
                            <button type="button" class="btn btn-outline-light" disabled>
                                <i class="bi bi-google"></i> Googleで登録
                            </button>
                            <button type="button" class="btn btn-outline-light" disabled>
                                <i class="bi bi-facebook"></i> Facebookで登録
                            </button>
                        </div>
                        
                        <div class="text-center">
                            <p class="text-muted mb-0">
                                すでにアカウントをお持ちの方は 
                                <a href="${pageContext.request.contextPath}/login.jsp" class="text-primary text-decoration-none">ログイン</a>
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
