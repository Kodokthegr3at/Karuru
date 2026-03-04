<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="includes/header.jsp" %>
<%
    if (currentUser == null) {
        response.sendRedirect("login.jsp?redirect=profile");
        return;
    }
%>

<style>
.profile-page {
    background: linear-gradient(135deg, #FFFFFF 0%, #F8F9FA 100%);
    min-height: calc(100vh - 200px);
}

.profile-header-card {
    background: var(--card-bg);
    border: 1px solid var(--border-color);
    border-radius: 16px;
    box-shadow: var(--shadow);
    overflow: hidden;
}

.profile-avatar-wrapper {
    position: relative;
    display: inline-block;
    cursor: pointer;
    transition: transform 0.3s ease;
}

.profile-avatar-wrapper:hover {
    transform: scale(1.05);
}

.profile-avatar-wrapper:hover .avatar-overlay {
    opacity: 1;
}

.avatar-overlay {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: rgba(0, 0, 0, 0.5);
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    opacity: 0;
    transition: opacity 0.3s ease;
}

.profile-stats-card {
    background: var(--bg-secondary);
    border: 1px solid var(--border-color);
    border-radius: 12px;
    padding: 1rem;
    text-align: center;
    transition: all 0.3s ease;
}

.profile-stats-card:hover {
    border-color: var(--primary-color);
    transform: translateY(-2px);
}

.profile-section-card {
    background: var(--card-bg);
    border: 1px solid var(--border-color);
    border-radius: 16px;
    box-shadow: var(--shadow);
    overflow: hidden;
    margin-bottom: 1.5rem;
}

.profile-section-header {
    background: var(--bg-secondary);
    border-bottom: 2px solid var(--border-color);
    padding: 1.25rem 1.5rem;
}

.profile-section-body {
    padding: 1.5rem;
}

.address-card {
    background: var(--bg-secondary);
    border: 2px solid var(--border-color);
    border-radius: 12px;
    padding: 1.25rem;
    margin-bottom: 1rem;
    transition: all 0.3s ease;
}

.address-card:hover {
    border-color: var(--primary-color);
    box-shadow: var(--shadow-hover);
}

.address-card.default {
    border-color: var(--primary-color);
    background: var(--accent-light);
}

.address-badge {
    font-size: 0.75rem;
    padding: 0.25rem 0.5rem;
    border-radius: 4px;
}

.profile-form-group {
    margin-bottom: 1.5rem;
}

.profile-form-group label {
    display: block;
    margin-bottom: 0.5rem;
    color: var(--text-color);
    font-weight: 500;
    font-size: 0.9rem;
}

.profile-form-group label i {
    margin-right: 0.5rem;
    color: var(--text-muted);
}

.profile-form-group .form-control,
.profile-form-group .form-control:focus {
    background-color: var(--card-bg);
    border-color: var(--border-color);
    color: var(--text-color);
}

.profile-form-group .form-control:focus {
    border-color: var(--primary-color);
    box-shadow: 0 0 0 0.2rem rgba(30, 136, 229, 0.15);
}

.profile-form-group .form-control::placeholder {
    color: var(--text-muted);
    opacity: 0.6;
}

@media (max-width: 768px) {
    .profile-section-body {
        padding: 1rem;
    }
}
</style>

<main class="profile-page py-5">
    <div class="container">
        <div class="row mb-4">
            <div class="col-12">
                <h1 class="fw-bold mb-2">
                    <i class="bi bi-person-circle me-2 text-warning"></i>プロフィール
                </h1>
                <p class="text-muted mb-0">プロフィール情報と設定を管理できます</p>
            </div>
        </div>
        
        <div class="row">
            <!-- Left Sidebar: Profile Header -->
            <div class="col-lg-4 col-md-5 mb-4">
                <div class="profile-header-card">
                    <div class="card-body text-center p-4">
                        <div class="profile-avatar-wrapper mb-3" id="avatarWrapper">
                            <img id="avatarImg" 
                                 src="${pageContext.request.contextPath}/img/default-avatar.png" 
                                 alt="Avatar" 
                                 class="rounded-circle" 
                                 style="width: 180px; height: 180px; object-fit: cover; border: 4px solid var(--border-color);">
                            <div class="avatar-overlay">
                                <i class="bi bi-camera-fill fs-3" style="color: var(--text-color);"></i>
                            </div>
                            <div id="avatarPreview" 
                                 class="position-absolute top-0 start-0 w-100 h-100 rounded-circle" 
                                 style="display: none; background-size: cover; background-position: center; border: 4px solid var(--primary-color);"></div>
                        </div>
                        <input type="file" id="avatarInput" name="avatar" accept="image/*" style="display: none;">
                        <button type="button" id="changeAvatarBtn" class="btn btn-outline-primary btn-sm mb-3">
                            <i class="bi bi-camera me-2"></i>アバターを変更
                        </button>
                        <div id="avatarUploadStatus" class="small mb-3"></div>
                        
                        <h4 class="mb-1" id="displayName">-</h4>
                        <p class="text-muted small mb-3" id="displayUsername">@username</p>
                        
                        <div class="row g-2 mt-4">
                            <div class="col-6">
                                <div class="profile-stats-card">
                                    <div class="text-muted small">出品数</div>
                                    <div class="fs-4 fw-bold text-primary" id="productsCount">0</div>
                                </div>
                            </div>
                            <div class="col-6">
                                <div class="profile-stats-card">
                                    <div class="text-muted small">評価</div>
                                    <div class="fs-4 fw-bold text-warning" id="rating">-</div>
                                </div>
                            </div>
                        </div>
                        
                        <div class="row g-2 mt-2">
                            <div class="col-6">
                                <div class="profile-stats-card">
                                    <div class="text-muted small">注文数</div>
                                    <div class="fs-4 fw-bold text-info" id="ordersCount">0</div>
                                </div>
                            </div>
                            <div class="col-6">
                                <div class="profile-stats-card">
                                    <div class="text-muted small">レビュー</div>
                                    <div class="fs-4 fw-bold text-success" id="reviewsCount">0</div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Right Content: Profile Sections -->
            <div class="col-lg-8 col-md-7">
                <!-- Profile Information -->
                <div class="profile-section-card">
                    <div class="profile-section-header">
                        <h5 class="mb-0">
                            <i class="bi bi-person-gear me-2 text-primary"></i>プロフィール情報
                        </h5>
                    </div>
                    <div class="profile-section-body">
                        <form id="profileForm" enctype="multipart/form-data">
                            <div class="row g-3">
                                <div class="col-md-6">
                                    <div class="profile-form-group">
                                        <label for="username">
                                            <i class="bi bi-person"></i>ユーザー名
                                        </label>
                                        <input type="text" 
                                               class="form-control" 
                                               id="username" 
                                               name="username" 
                                               placeholder="ユーザー名"
                                               readonly>
                                    </div>
                                </div>
                                <div class="col-md-6">
                                    <div class="profile-form-group">
                                        <label for="email">
                                            <i class="bi bi-envelope"></i>メールアドレス <span class="text-danger">*</span>
                                        </label>
                                        <input type="email" 
                                               class="form-control" 
                                               id="email" 
                                               name="email" 
                                               placeholder="メールアドレス"
                                               required>
                                    </div>
                                </div>
                            </div>
                            
                            <div class="row g-3">
                                <div class="col-md-6">
                                    <div class="profile-form-group">
                                        <label for="fullName">
                                            <i class="bi bi-card-text"></i>氏名
                                        </label>
                                        <input type="text" 
                                               class="form-control" 
                                               id="fullName" 
                                               name="full_name" 
                                               placeholder="氏名">
                                    </div>
                                </div>
                                <div class="col-md-6">
                                    <div class="profile-form-group">
                                        <label for="phone">
                                            <i class="bi bi-telephone"></i>電話番号
                                        </label>
                                        <input type="tel" 
                                               class="form-control" 
                                               id="phone" 
                                               name="phone" 
                                               placeholder="電話番号">
                                    </div>
                                </div>
                            </div>
                            
                            <div class="profile-form-group">
                                <label for="bio">
                                    <i class="bi bi-pencil"></i>自己紹介
                                </label>
                                <textarea class="form-control" 
                                          id="bio" 
                                          name="bio" 
                                          rows="4" 
                                          placeholder="自己紹介"
                                          style="min-height: 100px;"></textarea>
                            </div>
                            
                            <div class="d-flex gap-2">
                                <button type="submit" class="btn btn-primary" id="saveProfileBtn">
                                    <i class="bi bi-save me-2"></i>保存
                                </button>
                                <button type="button" class="btn btn-outline-secondary" onclick="resetProfileForm()">
                                    <i class="bi bi-arrow-counterclockwise me-2"></i>リセット
                                </button>
                            </div>
                        </form>
                    </div>
                </div>

                <!-- Addresses Section -->
                <div class="profile-section-card">
                    <div class="profile-section-header d-flex justify-content-between align-items-center">
                        <h5 class="mb-0">
                            <i class="bi bi-geo-alt me-2 text-primary"></i>配送先住所
                        </h5>
                        <button type="button" id="addAddressBtn" class="btn btn-primary btn-sm">
                            <i class="bi bi-plus-circle me-2"></i>追加
                        </button>
                    </div>
                    <div class="profile-section-body">
                        <div id="addressesList">
                            <div class="text-center py-4">
                                <div class="spinner-border text-primary" role="status">
                                    <span class="visually-hidden">読み込み中...</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</main>

<!-- Address Modal -->
<div class="modal fade" id="addressModal" tabindex="-1" aria-labelledby="addressModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-lg modal-dialog-centered">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title" id="addressModalLabel">
                    <i class="bi bi-geo-alt me-2"></i>住所を追加
                </h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <div class="modal-body">
                <form id="addressForm">
                    <input type="hidden" id="addressId" name="address_id">
                    
                    <div class="mb-3">
                        <label for="addressLabel" class="form-label">住所ラベル *</label>
                        <input type="text" class="form-control form-control-light" 
                               id="addressLabel" name="address_label" placeholder="例: 自宅、職場" required>
                    </div>
                    
                    <div class="row g-3">
                        <div class="col-md-6">
                            <label for="recipientName" class="form-label">受取人名 *</label>
                            <input type="text" class="form-control form-control-light" 
                                   id="recipientName" name="recipient_name" required>
                        </div>
                        <div class="col-md-6">
                            <label for="addressPhone" class="form-label">電話番号 *</label>
                            <input type="tel" class="form-control form-control-light" 
                                   id="addressPhone" name="phone" required>
                        </div>
                    </div>
                    
                    <div class="row g-3 mt-2">
                        <div class="col-md-4">
                            <label for="postalCode" class="form-label">
                                郵便番号 * 
                                <small class="text-muted ms-2">(7桁の数字を入力すると自動で住所が入力されます)</small>
                            </label>
                            <div class="input-group">
                                <input type="text" class="form-control form-control-light" 
                                       id="postalCode" name="postal_code" 
                                       placeholder="1234567 または 123-4567" 
                                       maxlength="8"
                                       pattern="\d{3}-?\d{4}" 
                                       required>
                                <span class="input-group-text" id="postalCodeStatus" style="display: none;">
                                    <span class="spinner-border spinner-border-sm text-primary" role="status" style="display: none;">
                                        <span class="visually-hidden">読み込み中...</span>
                                    </span>
                                    <i class="bi bi-check-circle text-success" style="display: none;"></i>
                                </span>
                            </div>
                            <small class="text-muted d-block mt-1">
                                <i class="bi bi-info-circle me-1"></i>郵便番号を入力すると、都道府県・市区町村・町域が自動入力されます
                            </small>
                        </div>
                        <div class="col-md-4">
                            <label for="prefecture" class="form-label">都道府県 *</label>
                            <input type="text" class="form-control form-control-light" 
                                   id="prefecture" name="prefecture" required>
                        </div>
                        <div class="col-md-4">
                            <label for="city" class="form-label">市区町村 *</label>
                            <input type="text" class="form-control form-control-light" 
                                   id="city" name="city" required>
                        </div>
                    </div>
                    
                    <div class="mt-3">
                        <label for="addressLine1" class="form-label">番地・建物名 *</label>
                        <input type="text" class="form-control form-control-light" 
                               id="addressLine1" name="address_line1" required>
                    </div>
                    
                    <div class="mt-3">
                        <label for="addressLine2" class="form-label">建物名・部屋番号（任意）</label>
                        <input type="text" class="form-control form-control-light" 
                               id="addressLine2" name="address_line2">
                    </div>
                    
                    <div class="mt-3">
                        <div class="form-check">
                            <input class="form-check-input" type="checkbox" id="isDefault" name="is_default">
                            <label class="form-check-label" for="isDefault">
                                デフォルト住所として設定
                            </label>
                        </div>
                    </div>
                </form>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">キャンセル</button>
                <button type="button" class="btn btn-primary" id="saveAddressBtn">
                    <i class="bi bi-save me-2"></i>保存
                </button>
            </div>
        </div>
    </div>
</div>

<script src="${pageContext.request.contextPath}/js/profile.js"></script>
<%@ include file="includes/footer.jsp" %>
