<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%
    String currentUser = (String) session.getAttribute("username");
    Integer userId = (Integer) session.getAttribute("userId");
    if (userId == null) {
        userId = (Integer) session.getAttribute("user_id"); // Fallback to user_id
    }
    String userRole = (String) session.getAttribute("role");
%>
<!DOCTYPE html>
<html lang="ja" data-bs-theme="light">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
    <title>カルル</title>
    <!-- Favicon -->
    <link rel="icon" type="image/png" href="${pageContext.request.contextPath}/img/logo.png">
    <link rel="apple-touch-icon" href="${pageContext.request.contextPath}/img/logo.png">
    <!-- Bootstrap 5.3 CSS -->
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet" integrity="sha384-T3c6CoIi6uLrA9TneNEoa7RxnatzjcDSCmG1MXxSR1GAsXEV/Dwwykc2MPK8M2HN" crossorigin="anonymous">
    <!-- Bootstrap Icons -->
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.1/font/bootstrap-icons.css">
    <!-- Custom CSS -->
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <script type="text/javascript">
        // Set context path for JavaScript files
        window.CONTEXT_PATH = '<%= request.getContextPath().replace("'", "\\'") %>';
        <% if (userId != null) { %>
        window.currentUserId = <%= userId %>;
        sessionStorage.setItem('userId', '<%= userId %>');
        <% } %>
    </script>
    <% if (userId != null) { %>
    <meta name="user-id" content="<%= userId %>">
    <% } %>
    <script src="${pageContext.request.contextPath}/js/config.js"></script>
    <script src="${pageContext.request.contextPath}/js/main.js"></script>
    <% if (currentUser != null) { %>
    <script src="${pageContext.request.contextPath}/js/websocket.js"></script>
    <script>
        // Initialize favorite icon and notification badge after page load
        (function() {
            function initFavoriteIcon() {
                if (window.updateFavoriteIcon && window.KaruruUtils) {
                    window.updateFavoriteIcon();
                } else {
                    // Retry if not ready (max 10 retries = 1 second)
                    if (!window._favoriteInitRetry) window._favoriteInitRetry = 0;
                    if (window._favoriteInitRetry < 10) {
                        window._favoriteInitRetry++;
                        setTimeout(initFavoriteIcon, 100);
                    }
                }
            }
            
            function initNotificationBadge() {
                if (window.updateNotificationBadgeCount) {
                    window.updateNotificationBadgeCount();
                } else {
                    // Retry if not ready (max 10 retries = 1 second)
                    if (!window._notificationInitRetry) window._notificationInitRetry = 0;
                    if (window._notificationInitRetry < 10) {
                        window._notificationInitRetry++;
                        setTimeout(initNotificationBadge, 100);
                    }
                }
            }
            
            if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', function() {
                    setTimeout(initFavoriteIcon, 500);
                    setTimeout(initNotificationBadge, 500);
                });
            } else {
                setTimeout(initFavoriteIcon, 500);
                setTimeout(initNotificationBadge, 500);
            }
        })();
    </script>
    <% } %>
    <!-- Bootstrap 5.3 JS Bundle -->
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js" integrity="sha384-C6RzsynM9kWDrMNeT87bh95OGNyZPhcTNXj1NW7RuBCsyN/o0jlpcV8Qyq46cDfL" crossorigin="anonymous"></script>
</head>
<body class="bg-light text-dark">
    <!-- Desktop Navbar (hidden on mobile) -->
    <nav class="navbar navbar-expand-lg navbar-light bg-white border-bottom sticky-top shadow-sm d-none d-lg-flex" style="background: #FFFFFF !important;">
        <div class="container">
            <a class="navbar-brand fw-bold fs-4 text-primary d-flex align-items-center" href="${pageContext.request.contextPath}/index.jsp">
                <img src="${pageContext.request.contextPath}/img/logo.png" alt="カルル" class="navbar-logo me-2" style="height: 40px; width: auto; object-fit: contain;">
                <span>カルル</span>
            </a>
            
            <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#navbarNav" aria-controls="navbarNav" aria-expanded="false" aria-label="Toggle navigation">
                <span class="navbar-toggler-icon"></span>
            </button>
            
            <div class="collapse navbar-collapse" id="navbarNav">
                <ul class="navbar-nav mx-auto">
                    <li class="nav-item">
                        <a class="nav-link d-flex align-items-center" href="${pageContext.request.contextPath}/index.jsp">
                            <i class="bi bi-house me-1 me-md-2"></i>
                            <span class="d-none d-md-inline">ホーム</span>
                        </a>
                    </li>
                    <li class="nav-item">
                        <a class="nav-link d-flex align-items-center" href="${pageContext.request.contextPath}/products.jsp">
                            <i class="bi bi-grid me-1 me-md-2"></i>
                            <span class="d-none d-md-inline">商品一覧</span>
                        </a>
                    </li>
                    <li class="nav-item">
                        <a class="nav-link d-flex align-items-center" href="${pageContext.request.contextPath}/categories.jsp">
                            <i class="bi bi-tags me-1 me-md-2"></i>
                            <span class="d-none d-md-inline">カテゴリー</span>
                        </a>
                    </li>
                    <% if (currentUser != null) { %>
                        <li class="nav-item">
                            <a class="nav-link d-flex align-items-center position-relative" href="${pageContext.request.contextPath}/cart.jsp">
                                <i class="bi bi-cart me-1 me-md-2"></i>
                                <span class="d-none d-md-inline">カート</span>
                                <span class="position-absolute top-0 start-100 translate-middle badge rounded-pill bg-danger" id="cartBadge" style="display: none; font-size: 0.65rem;" data-count="0" data-count-zero="true"></span>
                            </a>
                        </li>
                        <li class="nav-item">
                            <a class="nav-link d-flex align-items-center" href="${pageContext.request.contextPath}/favorites.jsp">
                                <i class="bi bi-heart me-1 me-md-2"></i>
                                <span class="d-none d-md-inline">お気に入り</span>
                            </a>
                        </li>
                        <li class="nav-item dropdown">
                            <a class="nav-link dropdown-toggle d-flex align-items-center" href="#" id="rentalNavDropdown" role="button" data-bs-toggle="dropdown" aria-expanded="false">
                                <i class="bi bi-calendar-check me-1 me-md-2"></i>
                                <span class="d-none d-md-inline">レンタル</span>
                            </a>
                            <ul class="dropdown-menu dropdown-menu-end dropdown-menu-light bg-white border" aria-labelledby="rentalNavDropdown" style="min-width: 200px; box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15); background-color: #FFFFFF !important;">
                                <li><a class="dropdown-item d-flex align-items-center" href="${pageContext.request.contextPath}/rental.jsp" style="padding: 0.625rem 1rem;"><i class="bi bi-calendar-plus me-2" style="width: 20px; text-align: center;"></i>レンタル予約</a></li>
                                <li><a class="dropdown-item d-flex align-items-center" href="${pageContext.request.contextPath}/rentals.jsp" style="padding: 0.625rem 1rem;"><i class="bi bi-clock-history me-2" style="width: 20px; text-align: center;"></i>レンタル履歴</a></li>
                            </ul>
                        </li>
                        <li class="nav-item dropdown" style="position: relative; z-index: 1051; overflow: visible;">
                            <a class="nav-link dropdown-toggle d-flex align-items-center position-relative" href="#" id="moreMenuDropdown" role="button" data-bs-toggle="dropdown" aria-expanded="false" data-bs-auto-close="true" style="overflow: visible;">
                                <i class="bi bi-three-dots me-1 me-md-2"></i>
                                <span class="d-none d-md-inline">その他</span>
                                <span class="position-absolute top-0 start-100 translate-middle badge rounded-pill bg-danger" id="combinedBadge" style="display: none; font-size: 0.65rem; z-index: 1;" data-count="0" data-count-zero="true"></span>
                            </a>
                            <ul class="dropdown-menu dropdown-menu-light bg-white border dropdown-menu-end" aria-labelledby="moreMenuDropdown" style="min-width: 220px !important; max-width: min(280px, calc(100vw - 1rem)) !important; overflow: visible !important; z-index: 10002 !important; position: absolute !important; top: 100% !important; right: 0 !important; left: auto !important; margin-top: 0.5rem !important; box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15) !important; transform: none !important; background-color: #FFFFFF !important; background: #FFFFFF !important; opacity: 1 !important;">
                                <li>
                                    <a class="dropdown-item d-flex align-items-center position-relative" href="${pageContext.request.contextPath}/messages.jsp" style="overflow: visible; padding: 0.625rem 1rem;">
                                        <i class="bi bi-chat-dots me-2" style="width: 20px; text-align: center; flex-shrink: 0;"></i>
                                        <span style="flex: 1; white-space: normal; word-wrap: break-word; overflow: visible; line-height: 1.5;">メッセージ</span>
                                        <span class="position-absolute end-0 me-2 badge rounded-pill bg-danger" id="messageBadgeDropdown" style="display: none; font-size: 0.65rem; z-index: 1;" data-count="0" data-count-zero="true"></span>
                                    </a>
                                </li>
                                <li>
                                    <a class="dropdown-item d-flex align-items-center position-relative" href="${pageContext.request.contextPath}/notifications.jsp" style="overflow: visible; padding: 0.625rem 1rem;">
                                        <i class="bi bi-bell me-2" style="width: 20px; text-align: center; flex-shrink: 0;"></i>
                                        <span style="flex: 1; white-space: normal; word-wrap: break-word; overflow: visible; line-height: 1.5;">通知</span>
                                        <span class="position-absolute end-0 me-2 badge rounded-pill bg-danger" id="notificationBadgeDropdown" style="display: none; font-size: 0.65rem; z-index: 1;" data-count="0" data-count-zero="true"></span>
                                    </a>
                                </li>
                                <li>
                                    <a class="dropdown-item d-flex align-items-center" href="${pageContext.request.contextPath}/offers.jsp" style="overflow: visible; padding: 0.625rem 1rem;">
                                        <i class="bi bi-hand-thumbs-up me-2 text-warning" style="width: 20px; text-align: center; flex-shrink: 0;"></i>
                                        <span style="flex: 1; white-space: normal; word-wrap: break-word; overflow: visible; line-height: 1.5;">オファー管理</span>
                                    </a>
                                </li>
                                <li>
                                    <a class="dropdown-item d-flex align-items-center" href="${pageContext.request.contextPath}/rentals.jsp" style="overflow: visible; padding: 0.625rem 1rem;">
                                        <i class="bi bi-clock-history me-2" style="width: 20px; text-align: center; flex-shrink: 0;"></i>
                                        <span style="flex: 1; white-space: normal; word-wrap: break-word; overflow: visible; line-height: 1.5;">レンタル履歴</span>
                                    </a>
                                </li>
                                <li><hr class="dropdown-divider bg-secondary" style="margin: 0.5rem 0;"></li>
                                <li>
                                    <a class="dropdown-item d-flex align-items-center" href="${pageContext.request.contextPath}/dashboard.jsp" style="overflow: visible; padding: 0.625rem 1rem;">
                                        <i class="bi bi-speedometer2 me-2" style="width: 20px; text-align: center; flex-shrink: 0;"></i>
                                        <span style="flex: 1; white-space: normal; word-wrap: break-word; overflow: visible; line-height: 1.5;">ダッシュボード</span>
                                    </a>
                                </li>
                                <li>
                                    <a class="dropdown-item d-flex align-items-center" href="${pageContext.request.contextPath}/wallet.jsp" style="overflow: visible; padding: 0.625rem 1rem;">
                                        <i class="bi bi-wallet2 me-2" style="width: 20px; text-align: center; flex-shrink: 0;"></i>
                                        <span style="flex: 1; white-space: normal; word-wrap: break-word; overflow: visible; line-height: 1.5;">ウォレット</span>
                                    </a>
                                </li>
                                <li>
                                    <a class="dropdown-item d-flex align-items-center" href="${pageContext.request.contextPath}/garage.jsp" style="overflow: visible; padding: 0.625rem 1rem;">
                                        <i class="bi bi-box-seam me-2" style="width: 20px; text-align: center; flex-shrink: 0;"></i>
                                        <span style="flex: 1; white-space: normal; word-wrap: break-word; overflow: visible; line-height: 1.5;">マイガレージ</span>
                                    </a>
                                </li>
                                <li>
                                    <a class="dropdown-item d-flex align-items-center" href="${pageContext.request.contextPath}/recently-viewed.jsp" style="overflow: visible; padding: 0.625rem 1rem;">
                                        <i class="bi bi-clock-history me-2" style="width: 20px; text-align: center; flex-shrink: 0;"></i>
                                        <span style="flex: 1; white-space: normal; word-wrap: break-word; overflow: visible; line-height: 1.5;">最近閲覧</span>
                                    </a>
                                </li>
                                <% if ("admin".equals(userRole)) { %>
                                <li><hr class="dropdown-divider bg-secondary" style="margin: 0.5rem 0;"></li>
                                <li>
                                    <a class="dropdown-item d-flex align-items-center text-warning" href="${pageContext.request.contextPath}/admin/dashboard.jsp" style="overflow: visible; padding: 0.625rem 1rem;">
                                        <i class="bi bi-shield-check me-2" style="width: 20px; text-align: center; flex-shrink: 0;"></i>
                                        <span style="flex: 1; white-space: normal; word-wrap: break-word; overflow: visible; line-height: 1.5;">管理画面</span>
                                    </a>
                                </li>
                                <% } %>
                            </ul>
                        </li>
                    <% } %>
                </ul>
                
                <div class="d-flex align-items-center gap-2 ms-auto" style="overflow: visible !important; position: relative !important;">
                    <% if (currentUser == null) { %>
                        <a href="${pageContext.request.contextPath}/login.jsp" class="btn btn-outline-primary d-flex align-items-center">
                            <i class="bi bi-box-arrow-in-right me-1 d-md-none"></i>
                            <span class="d-none d-md-inline">ログイン</span>
                            <span class="d-inline d-md-none">ログイン</span>
                        </a>
                        <a href="${pageContext.request.contextPath}/register.jsp" class="btn btn-primary d-flex align-items-center">
                            <i class="bi bi-person-plus me-1 d-md-none"></i>
                            <span class="d-none d-md-inline">新規登録</span>
                            <span class="d-inline d-md-none">登録</span>
                        </a>
                    <% } else { %>
                        <div class="dropdown" style="position: relative; z-index: 1051; overflow: visible !important;">
                            <button class="btn btn-outline-primary dropdown-toggle d-flex align-items-center" type="button" id="userMenuDropdown" data-bs-toggle="dropdown" aria-expanded="false" style="overflow: visible; position: relative;">
                                <i class="bi bi-person-circle me-1 me-md-2"></i>
                                <span class="d-none d-md-inline"><%= currentUser %></span>
                                <span class="d-inline d-md-none">アカウント</span>
                            </button>
                            <ul class="dropdown-menu dropdown-menu-end bg-white border" aria-labelledby="userMenuDropdown" style="overflow: visible !important; z-index: 10002 !important; position: absolute !important; min-width: 220px !important; max-width: min(280px, calc(100vw - 1rem)) !important; box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15) !important; right: -0.5rem !important; left: auto !important; background-color: #FFFFFF !important; background: #FFFFFF !important; opacity: 1 !important;">
                                <li><a class="dropdown-item d-flex align-items-center" href="${pageContext.request.contextPath}/profile.jsp" style="overflow: visible; padding: 0.625rem 1rem;"><i class="bi bi-person me-2" style="width: 20px; text-align: center; flex-shrink: 0;"></i><span style="flex: 1; white-space: normal; word-wrap: break-word; overflow: visible; line-height: 1.5;">プロフィール</span></a></li>
                                <li><a class="dropdown-item d-flex align-items-center" href="${pageContext.request.contextPath}/create-listing.jsp" style="overflow: visible; padding: 0.625rem 1rem;"><i class="bi bi-plus-circle me-2" style="width: 20px; text-align: center; flex-shrink: 0;"></i><span style="flex: 1; white-space: normal; word-wrap: break-word; overflow: visible; line-height: 1.5;">商品を出品</span></a></li>
                                <li><a class="dropdown-item d-flex align-items-center" href="${pageContext.request.contextPath}/orders.jsp" style="overflow: visible; padding: 0.625rem 1rem;"><i class="bi bi-receipt me-2" style="width: 20px; text-align: center; flex-shrink: 0;"></i><span style="flex: 1; white-space: normal; word-wrap: break-word; overflow: visible; line-height: 1.5;">注文履歴</span></a></li>
                                <li><a class="dropdown-item d-flex align-items-center" href="${pageContext.request.contextPath}/wallet.jsp" style="overflow: visible; padding: 0.625rem 1rem;"><i class="bi bi-wallet2 me-2" style="width: 20px; text-align: center; flex-shrink: 0;"></i><span style="flex: 1; white-space: normal; word-wrap: break-word; overflow: visible; line-height: 1.5;">ウォレット</span></a></li>
                                <li><a class="dropdown-item d-flex align-items-center" href="${pageContext.request.contextPath}/settings.jsp" style="overflow: visible; padding: 0.625rem 1rem;"><i class="bi bi-gear me-2" style="width: 20px; text-align: center; flex-shrink: 0;"></i><span style="flex: 1; white-space: normal; word-wrap: break-word; overflow: visible; line-height: 1.5;">設定</span></a></li>
                                <li><hr class="dropdown-divider" style="margin: 0.5rem 0;"></li>
                                <li><a class="dropdown-item text-danger d-flex align-items-center" href="${pageContext.request.contextPath}/LogoutServlet" style="overflow: visible; padding: 0.625rem 1rem;"><i class="bi bi-box-arrow-right me-2" style="width: 20px; text-align: center; flex-shrink: 0;"></i><span style="flex: 1; white-space: normal; word-wrap: break-word; overflow: visible; line-height: 1.5;">ログアウト</span></a></li>
                            </ul>
                        </div>
                    <% } %>
                </div>
            </div>
        </div>
    </nav>
    
    <!-- Mobile Top Bar (only logo and user menu) -->
    <nav class="navbar navbar-light bg-white border-bottom d-lg-none sticky-top" style="background: #FFFFFF !important; z-index: 1030;">
        <div class="container-fluid px-3">
            <a class="navbar-brand fw-bold fs-5 text-primary d-flex align-items-center" href="${pageContext.request.contextPath}/index.jsp">
                <img src="${pageContext.request.contextPath}/img/logo.png" alt="カルル" class="navbar-logo me-2" style="height: 32px; width: auto; object-fit: contain;">
                <span>カルル</span>
            </a>
            
            <div class="d-flex align-items-center">
                <% if (currentUser == null) { %>
                    <a href="${pageContext.request.contextPath}/login.jsp" class="btn btn-sm btn-outline-primary">
                        <i class="bi bi-box-arrow-in-right me-1"></i>ログイン
                    </a>
                <% } else { %>
                    <div class="dropdown">
                        <button class="btn btn-sm btn-outline-primary dropdown-toggle d-flex align-items-center" type="button" id="mobileUserMenuDropdown" data-bs-toggle="dropdown" aria-expanded="false">
                            <i class="bi bi-person-circle me-1"></i>
                            <span class="d-none d-sm-inline"><%= currentUser %></span>
                        </button>
                        <ul class="dropdown-menu dropdown-menu-end bg-white border" aria-labelledby="mobileUserMenuDropdown" style="min-width: 220px; box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);">
                            <li><a class="dropdown-item d-flex align-items-center" href="${pageContext.request.contextPath}/profile.jsp"><i class="bi bi-person me-2" style="width: 20px;"></i><span>プロフィール</span></a></li>
                            <li><a class="dropdown-item d-flex align-items-center" href="${pageContext.request.contextPath}/create-listing.jsp"><i class="bi bi-plus-circle me-2" style="width: 20px;"></i><span>商品を出品</span></a></li>
                            <li><a class="dropdown-item d-flex align-items-center" href="${pageContext.request.contextPath}/orders.jsp"><i class="bi bi-receipt me-2" style="width: 20px;"></i><span>注文履歴</span></a></li>
                            <li><a class="dropdown-item d-flex align-items-center" href="${pageContext.request.contextPath}/wallet.jsp"><i class="bi bi-wallet2 me-2" style="width: 20px;"></i><span>ウォレット</span></a></li>
                            <li><a class="dropdown-item d-flex align-items-center" href="${pageContext.request.contextPath}/settings.jsp"><i class="bi bi-gear me-2" style="width: 20px;"></i><span>設定</span></a></li>
                            <li><hr class="dropdown-divider"></li>
                            <li><a class="dropdown-item text-danger d-flex align-items-center" href="${pageContext.request.contextPath}/LogoutServlet"><i class="bi bi-box-arrow-right me-2" style="width: 20px;"></i><span>ログアウト</span></a></li>
                        </ul>
                    </div>
                <% } %>
            </div>
        </div>
    </nav>
    
    <!-- Mobile Bottom Navigation Bar -->
    <nav class="mobile-bottom-nav d-lg-none fixed-bottom bg-white border-top shadow-lg" id="mobileBottomNav" style="z-index: 1030;">
        <div class="container-fluid px-0">
            <div class="row g-0">
                <div class="col">
                    <a href="${pageContext.request.contextPath}/index.jsp" class="bottom-nav-item" data-page="index">
                        <i class="bi bi-house"></i>
                        <span>ホーム</span>
                    </a>
                </div>
                <div class="col">
                    <a href="${pageContext.request.contextPath}/products.jsp" class="bottom-nav-item" data-page="products">
                        <i class="bi bi-grid"></i>
                        <span>商品</span>
                    </a>
                </div>
                <div class="col">
                    <a href="${pageContext.request.contextPath}/categories.jsp" class="bottom-nav-item" data-page="categories">
                        <i class="bi bi-tags"></i>
                        <span>カテゴリー</span>
                    </a>
                </div>
                <% if (currentUser != null) { %>
                    <div class="col">
                        <a href="${pageContext.request.contextPath}/cart.jsp" class="bottom-nav-item position-relative" data-page="cart">
                            <i class="bi bi-cart"></i>
                            <span>カート</span>
                            <span class="position-absolute top-0 start-100 translate-middle badge rounded-pill bg-danger" id="mobileCartBadge" style="display: none; font-size: 0.6rem; padding: 0.2em 0.4em;" data-count="0" data-count-zero="true"></span>
                        </a>
                    </div>
                    <div class="col">
                        <a href="${pageContext.request.contextPath}/favorites.jsp" class="bottom-nav-item" data-page="favorites">
                            <i class="bi bi-heart"></i>
                            <span>お気に入り</span>
                        </a>
                    </div>
                    <div class="col">
                        <button type="button" class="bottom-nav-item border-0 bg-transparent w-100 h-100" data-bs-toggle="modal" data-bs-target="#mobileMoreMenuModal" style="color: inherit; padding: 0.5rem 0.25rem;">
                            <i class="bi bi-three-dots"></i>
                            <span>その他</span>
                            <span class="position-absolute top-0 start-100 translate-middle badge rounded-pill bg-danger" id="mobileCombinedBadge" style="display: none; font-size: 0.6rem; padding: 0.2em 0.4em;" data-count="0" data-count-zero="true"></span>
                        </button>
                    </div>
                <% } else { %>
                    <div class="col">
                        <a href="${pageContext.request.contextPath}/login.jsp" class="bottom-nav-item" data-page="login">
                            <i class="bi bi-box-arrow-in-right"></i>
                            <span>ログイン</span>
                        </a>
                    </div>
                <% } %>
            </div>
        </div>
    </nav>
    
    <!-- Mobile More Menu Modal -->
    <% if (currentUser != null) { %>
    <div class="modal fade mobile-bottom-sheet-modal" id="mobileMoreMenuModal" tabindex="-1" aria-labelledby="mobileMoreMenuModalLabel" aria-hidden="true">
        <div class="modal-dialog modal-dialog-scrollable">
            <div class="modal-content card-light">
                <div class="modal-header card-header-light border-bottom">
                    <h5 class="modal-title" id="mobileMoreMenuModalLabel">
                        <i class="bi bi-three-dots me-2"></i>その他
                    </h5>
                    <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                </div>
                <div class="modal-body p-0">
                    <div class="list-group list-group-flush">
                        <a href="${pageContext.request.contextPath}/messages.jsp" class="list-group-item list-group-item-action d-flex align-items-center position-relative mobile-menu-item" style="padding: 0.75rem 1rem; border-color: var(--border-color); font-size: 0.95rem;">
                            <i class="bi bi-chat-dots me-3" style="width: 24px; text-align: center; color: var(--primary-color);"></i>
                            <span style="flex: 1;">メッセージ</span>
                            <span class="badge rounded-pill bg-danger" id="mobileMessageBadge" style="display: none; font-size: 0.7rem;" data-count="0" data-count-zero="true"></span>
                        </a>
                        <a href="${pageContext.request.contextPath}/notifications.jsp" class="list-group-item list-group-item-action d-flex align-items-center position-relative mobile-menu-item" style="padding: 0.75rem 1rem; border-color: var(--border-color); font-size: 0.95rem;">
                            <i class="bi bi-bell me-3" style="width: 24px; text-align: center; color: var(--primary-color);"></i>
                            <span style="flex: 1;">通知</span>
                            <span class="badge rounded-pill bg-danger" id="mobileNotificationBadge" style="display: none; font-size: 0.7rem;" data-count="0" data-count-zero="true"></span>
                        </a>
                        <a href="${pageContext.request.contextPath}/offers.jsp" class="list-group-item list-group-item-action d-flex align-items-center mobile-menu-item" style="padding: 0.75rem 1rem; border-color: var(--border-color); font-size: 0.95rem;">
                            <i class="bi bi-hand-thumbs-up me-3 text-warning" style="width: 24px; text-align: center;"></i>
                            <span style="flex: 1;">オファー管理</span>
                        </a>
                        <a href="${pageContext.request.contextPath}/rental.jsp" class="list-group-item list-group-item-action d-flex align-items-center mobile-menu-item" style="padding: 0.75rem 1rem; border-color: var(--border-color); font-size: 0.95rem;">
                            <i class="bi bi-calendar-check me-3" style="width: 24px; text-align: center; color: var(--primary-color);"></i>
                            <span style="flex: 1;">レンタル</span>
                        </a>
                        <a href="${pageContext.request.contextPath}/rentals.jsp" class="list-group-item list-group-item-action d-flex align-items-center mobile-menu-item" style="padding: 0.75rem 1rem; border-color: var(--border-color); font-size: 0.95rem;">
                            <i class="bi bi-clock-history me-3" style="width: 24px; text-align: center; color: var(--primary-color);"></i>
                            <span style="flex: 1;">レンタル履歴</span>
                        </a>
                        <div class="list-group-item" style="padding: 0.5rem 1.25rem; background-color: var(--bg-secondary); border-color: var(--border-color);"></div>
                        <a href="${pageContext.request.contextPath}/dashboard.jsp" class="list-group-item list-group-item-action d-flex align-items-center mobile-menu-item" style="padding: 0.75rem 1rem; border-color: var(--border-color); font-size: 0.95rem;">
                            <i class="bi bi-speedometer2 me-3" style="width: 24px; text-align: center; color: var(--primary-color);"></i>
                            <span style="flex: 1;">ダッシュボード</span>
                        </a>
                        <a href="${pageContext.request.contextPath}/wallet.jsp" class="list-group-item list-group-item-action d-flex align-items-center mobile-menu-item" style="padding: 0.75rem 1rem; border-color: var(--border-color); font-size: 0.95rem;">
                            <i class="bi bi-wallet2 me-3" style="width: 24px; text-align: center; color: var(--primary-color);"></i>
                            <span style="flex: 1;">ウォレット</span>
                        </a>
                        <a href="${pageContext.request.contextPath}/garage.jsp" class="list-group-item list-group-item-action d-flex align-items-center mobile-menu-item" style="padding: 0.75rem 1rem; border-color: var(--border-color); font-size: 0.95rem;">
                            <i class="bi bi-box-seam me-3" style="width: 24px; text-align: center; color: var(--primary-color);"></i>
                            <span style="flex: 1;">マイガレージ</span>
                        </a>
                        <a href="${pageContext.request.contextPath}/recently-viewed.jsp" class="list-group-item list-group-item-action d-flex align-items-center mobile-menu-item" style="padding: 0.75rem 1rem; border-color: var(--border-color); font-size: 0.95rem;">
                            <i class="bi bi-clock-history me-3" style="width: 24px; text-align: center; color: var(--primary-color);"></i>
                            <span style="flex: 1;">最近閲覧</span>
                        </a>
                        <% if ("admin".equals(userRole)) { %>
                        <div class="list-group-item" style="padding: 0.5rem 1.25rem; background-color: var(--bg-secondary); border-color: var(--border-color);"></div>
                        <a href="${pageContext.request.contextPath}/admin/dashboard.jsp" class="list-group-item list-group-item-action d-flex align-items-center mobile-menu-item" style="padding: 0.75rem 1rem; border-color: var(--border-color); font-size: 0.95rem;">
                            <i class="bi bi-shield-check me-3 text-warning" style="width: 24px; text-align: center;"></i>
                            <span style="flex: 1;">管理画面</span>
                        </a>
                        <% } %>
                    </div>
                </div>
            </div>
        </div>
    </div>
    <% } %>

