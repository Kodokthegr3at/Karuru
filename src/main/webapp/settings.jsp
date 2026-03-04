<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="includes/header.jsp" %>
<%
    if (currentUser == null) {
        response.sendRedirect("login.jsp?redirect=settings");
        return;
    }
%>

<main class="page-main py-4 settings-page">
    <div class="container">
        <h1 class="fw-bold mb-4">
            <i class="bi bi-gear"></i> アカウント設定
        </h1>
        
        <div class="row">
            <div class="col-md-3 mb-4">
                <div class="card card-light">
                    <div class="card-body">
                        <nav class="nav flex-column">
                            <a class="nav-link active" href="#account" data-section="account">
                                <i class="bi bi-person"></i> アカウント
                            </a>
                            <a class="nav-link" href="#password" data-section="password">
                                <i class="bi bi-lock"></i> パスワード
                            </a>
                            <a class="nav-link" href="#notifications" data-section="notifications">
                                <i class="bi bi-bell"></i> 通知設定
                            </a>
                            <a class="nav-link" href="#privacy" data-section="privacy">
                                <i class="bi bi-shield-lock"></i> プライバシー
                            </a>
                        </nav>
                    </div>
                </div>
            </div>

            <div class="col-md-9">
                <!-- Account Settings -->
                <section id="account" class="settings-section">
                    <div class="card card-light mb-4">
                        <div class="card-header card-header-light">
                            <h5 class="mb-0">アカウント情報</h5>
                        </div>
                        <div class="card-body">
                            <form id="accountForm">
                                <div class="mb-3">
                                    <label for="username" class="form-label">ユーザー名</label>
                                    <input type="text" class="form-control form-control-light" 
                                           id="username" name="username" readonly>
                                    <small class="form-text text-muted">ユーザー名は変更できません</small>
                                </div>
                                <div class="mb-3">
                                    <label for="email" class="form-label">メールアドレス</label>
                                    <input type="email" class="form-control form-control-light" 
                                           id="email" name="email">
                                </div>
                                <button type="submit" class="btn btn-primary">
                                    <i class="bi bi-save"></i> 保存
                                </button>
                            </form>
                        </div>
                    </div>
                </section>

                <!-- Password Settings -->
                <section id="password" class="settings-section d-none">
                    <div class="card card-light mb-4">
                        <div class="card-header card-header-light">
                            <h5 class="mb-0">パスワード変更</h5>
                        </div>
                        <div class="card-body">
                            <form id="passwordForm">
                                <div class="mb-3">
                                    <label for="currentPassword" class="form-label">現在のパスワード</label>
                                    <input type="password" class="form-control form-control-light" 
                                           id="currentPassword" name="currentPassword" required>
                                </div>
                                <div class="mb-3">
                                    <label for="newPassword" class="form-label">新しいパスワード</label>
                                    <input type="password" class="form-control form-control-light" 
                                           id="newPassword" name="newPassword" required minlength="6">
                                </div>
                                <div class="mb-3">
                                    <label for="confirmNewPassword" class="form-label">新しいパスワード（確認）</label>
                                    <input type="password" class="form-control form-control-light" 
                                           id="confirmNewPassword" name="confirmNewPassword" required>
                                </div>
                                <button type="submit" class="btn btn-primary">
                                    <i class="bi bi-key"></i> パスワードを変更
                                </button>
                            </form>
                        </div>
                    </div>
                </section>

                <!-- Notification Settings -->
                <section id="notifications" class="settings-section d-none">
                    <div class="card card-light mb-4">
                        <div class="card-header card-header-light">
                            <h5 class="mb-0">通知設定</h5>
                        </div>
                        <div class="card-body">
                            <form id="notificationForm">
                                <div class="form-check mb-3">
                                    <input class="form-check-input" type="checkbox" id="emailNotifications" name="email_notifications" checked>
                                    <label class="form-check-label" for="emailNotifications">
                                        メール通知を受け取る
                                    </label>
                                </div>
                                <div class="form-check mb-3">
                                    <input class="form-check-input" type="checkbox" id="orderNotifications" name="order_notifications" checked>
                                    <label class="form-check-label" for="orderNotifications">
                                        注文通知を受け取る
                                    </label>
                                </div>
                                <div class="form-check mb-3">
                                    <input class="form-check-input" type="checkbox" id="messageNotifications" name="message_notifications" checked>
                                    <label class="form-check-label" for="messageNotifications">
                                        メッセージ通知を受け取る
                                    </label>
                                </div>
                                <button type="submit" class="btn btn-primary">
                                    <i class="bi bi-save"></i> 保存
                                </button>
                            </form>
                        </div>
                    </div>
                </section>

                <!-- Privacy Settings -->
                <section id="privacy" class="settings-section d-none">
                    <div class="card card-light mb-4">
                        <div class="card-header card-header-light">
                            <h5 class="mb-0">プライバシー設定</h5>
                        </div>
                        <div class="card-body">
                            <form id="privacyForm">
                                <div class="form-check mb-3">
                                    <input class="form-check-input" type="checkbox" id="profilePublic" name="profile_public" checked>
                                    <label class="form-check-label" for="profilePublic">
                                        プロフィールを公開する
                                    </label>
                                </div>
                                <div class="form-check mb-3">
                                    <input class="form-check-input" type="checkbox" id="showEmail" name="show_email">
                                    <label class="form-check-label" for="showEmail">
                                        メールアドレスを表示する
                                    </label>
                                </div>
                                <button type="submit" class="btn btn-primary">
                                    <i class="bi bi-save"></i> 保存
                                </button>
                            </form>
                        </div>
                    </div>
                </section>
            </div>
        </div>
    </div>
</main>

<script src="${pageContext.request.contextPath}/js/settings.js"></script>
<%@ include file="includes/footer.jsp" %>

