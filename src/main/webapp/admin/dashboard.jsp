<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
    // Check admin access
    try {
        if (session == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp?redirect=admin/dashboard.jsp");
            return;
        }
        
        String userRole = (String) session.getAttribute("role");
        if (userRole == null || !"admin".equals(userRole)) {
            response.sendRedirect(request.getContextPath() + "/index.jsp");
            return;
        }
    } catch (Exception e) {
        e.printStackTrace();
        response.sendRedirect(request.getContextPath() + "/login.jsp?redirect=admin/dashboard.jsp");
        return;
    }
%>
<%@ include file="../includes/header.jsp" %>

<main class="admin-dashboard-page py-4">
    <div class="container-fluid px-4">
        <div class="d-flex justify-content-between align-items-center mb-4">
            <h1 class="h2 mb-0 fw-bold">
                <i class="bi bi-speedometer2 me-2 text-primary"></i>管理ダッシュボード
            </h1>
        </div>

        <!-- Statistics Grid - 2x2 on mobile -->
        <div class="row g-2 g-md-4 mb-4 stat-cards-grid">
            <div class="col-6 col-xl-3">
                <div class="stat-card stat-card-primary">
                    <div class="stat-card-icon">
                        <i class="bi bi-people-fill"></i>
                    </div>
                    <div class="stat-card-content">
                        <h6 class="stat-card-label">総ユーザー数</h6>
                        <h2 class="stat-card-value" id="totalUsers">0</h2>
                        <small class="stat-card-change text-success">
                            <i class="bi bi-arrow-up"></i> <span class="text-muted">アクティブ</span>
                        </small>
                    </div>
                </div>
            </div>
            <div class="col-6 col-xl-3">
                <div class="stat-card stat-card-success">
                    <div class="stat-card-icon">
                        <i class="bi bi-box-seam-fill"></i>
                    </div>
                    <div class="stat-card-content">
                        <h6 class="stat-card-label">総商品数</h6>
                        <h2 class="stat-card-value" id="totalProducts">0</h2>
                        <small class="stat-card-change text-info">
                            <i class="bi bi-grid-3x3-gap"></i> <span class="text-muted">在庫あり</span>
                        </small>
                    </div>
                </div>
            </div>
            <div class="col-6 col-xl-3">
                <div class="stat-card stat-card-warning">
                    <div class="stat-card-icon">
                        <i class="bi bi-cart-check-fill"></i>
                    </div>
                    <div class="stat-card-content">
                        <h6 class="stat-card-label">総注文数</h6>
                        <h2 class="stat-card-value" id="totalOrders">0</h2>
                        <small class="stat-card-change text-warning">
                            <i class="bi bi-receipt"></i> <span class="text-muted">処理中</span>
                        </small>
                    </div>
                </div>
            </div>
            <div class="col-6 col-xl-3">
                <div class="stat-card stat-card-info">
                    <div class="stat-card-icon">
                        <i class="bi bi-currency-yen"></i>
                    </div>
                    <div class="stat-card-content">
                        <h6 class="stat-card-label">総売上</h6>
                        <h2 class="stat-card-value" id="totalRevenue">¥0</h2>
                        <small class="stat-card-change text-primary">
                            <i class="bi bi-graph-up"></i> <span class="text-muted">今月</span>
                        </small>
                    </div>
                </div>
            </div>
            <div class="col-6 col-xl-3">
                <div class="stat-card stat-card-offers">
                    <div class="stat-card-icon">
                        <i class="bi bi-hand-thumbs-up"></i>
                    </div>
                    <div class="stat-card-content">
                        <h6 class="stat-card-label">オファー数</h6>
                        <h2 class="stat-card-value" id="totalOffers">0</h2>
                        <small class="stat-card-change">
                            <i class="bi bi-tag"></i> <span class="text-muted">価格交渉</span>
                        </small>
                    </div>
                </div>
            </div>
        </div>

        <!-- Statistics Table -->
        <div class="row mb-4">
            <div class="col-12">
                <div class="card shadow-lg border-0">
                    <div class="card-header card-header-light">
                        <h5 class="mb-0 fw-bold">
                            <i class="bi bi-bar-chart-fill me-2 text-primary"></i>統計サマリー
                        </h5>
                    </div>
                    <div class="card-body p-0">
                        <div class="table-responsive">
                            <table class="table table-light table-hover mb-0" id="statisticsTable">
                                <thead class="table-light">
                                    <tr>
                                        <th class="text-center"><i class="bi bi-info-circle me-1"></i>指標</th>
                                        <th class="text-center"><i class="bi bi-123 me-1"></i>値</th>
                                        <th class="text-center"><i class="bi bi-percent me-1"></i>変化率</th>
                                        <th class="text-center"><i class="bi bi-calendar me-1"></i>期間</th>
                                        <th class="text-center"><i class="bi bi-graph-up me-1"></i>トレンド</th>
                                    </tr>
                                </thead>
                                <tbody id="statisticsTableBody">
                                    <tr>
                                        <td class="text-center">
                                            <i class="bi bi-people text-primary me-2"></i>アクティブユーザー
                                        </td>
                                        <td class="text-center fw-bold" id="statActiveUsers">-</td>
                                        <td class="text-center">
                                            <span class="badge bg-success" id="statActiveUsersChange">+0%</span>
                                        </td>
                                        <td class="text-center text-muted">今月</td>
                                        <td class="text-center">
                                            <i class="bi bi-arrow-up text-success"></i>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="text-center">
                                            <i class="bi bi-box text-success me-2"></i>販売中商品
                                        </td>
                                        <td class="text-center fw-bold" id="statActiveProducts">-</td>
                                        <td class="text-center">
                                            <span class="badge bg-info" id="statActiveProductsChange">+0%</span>
                                        </td>
                                        <td class="text-center text-muted">今月</td>
                                        <td class="text-center">
                                            <i class="bi bi-arrow-up text-success"></i>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="text-center">
                                            <i class="bi bi-cart-check text-warning me-2"></i>完了注文
                                        </td>
                                        <td class="text-center fw-bold" id="statCompletedOrders">-</td>
                                        <td class="text-center">
                                            <span class="badge bg-warning" id="statCompletedOrdersChange">+0%</span>
                                        </td>
                                        <td class="text-center text-muted">今月</td>
                                        <td class="text-center">
                                            <i class="bi bi-arrow-up text-success"></i>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="text-center">
                                            <i class="bi bi-currency-yen text-info me-2"></i>平均注文額
                                        </td>
                                        <td class="text-center fw-bold" id="statAvgOrderValue">-</td>
                                        <td class="text-center">
                                            <span class="badge bg-primary" id="statAvgOrderValueChange">+0%</span>
                                        </td>
                                        <td class="text-center text-muted">今月</td>
                                        <td class="text-center">
                                            <i class="bi bi-arrow-up text-success"></i>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="text-center">
                                            <i class="bi bi-star text-warning me-2"></i>平均評価
                                        </td>
                                        <td class="text-center fw-bold" id="statAvgRating">-</td>
                                        <td class="text-center">
                                            <span class="badge bg-success" id="statAvgRatingChange">+0%</span>
                                        </td>
                                        <td class="text-center text-muted">全体</td>
                                        <td class="text-center">
                                            <i class="bi bi-arrow-up text-success"></i>
                                        </td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <div class="row g-4">
            <div class="col-12">
                <div class="card shadow-lg border-0">
                    <div class="card-header card-header-light">
                        <h5 class="mb-0 fw-bold">
                            <i class="bi bi-people-fill me-2 text-primary"></i>ユーザー管理
                        </h5>
                    </div>
                    <div class="card-body p-0">
                        <div id="usersTable" class="admin-table">
                            <div class="text-center p-4">
                                <div class="spinner-border text-primary" role="status">
                                    <span class="visually-hidden">読み込み中...</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <div class="col-12">
                <div class="card shadow-lg border-0">
                    <div class="card-header card-header-light">
                        <h5 class="mb-0 fw-bold">
                            <i class="bi bi-box-seam-fill me-2 text-success"></i>商品管理
                        </h5>
                    </div>
                    <div class="card-body p-0">
                        <div id="productsTable" class="admin-table">
                            <div class="text-center p-4">
                                <div class="spinner-border text-success" role="status">
                                    <span class="visually-hidden">読み込み中...</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <div class="col-12">
                <div class="card shadow-lg border-0">
                    <div class="card-header card-header-light">
                        <h5 class="mb-0 fw-bold">
                            <i class="bi bi-cart-check-fill me-2 text-warning"></i>注文管理
                        </h5>
                    </div>
                    <div class="card-body p-0">
                        <div id="ordersTable" class="admin-table">
                            <div class="text-center p-4">
                                <div class="spinner-border text-warning" role="status">
                                    <span class="visually-hidden">読み込み中...</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <div class="col-12">
                <div class="card shadow-lg border-0">
                    <div class="card-header card-header-light">
                        <h5 class="mb-0 fw-bold">
                            <i class="bi bi-hand-thumbs-up me-2 text-warning"></i>オファー管理
                        </h5>
                    </div>
                    <div class="card-body p-0">
                        <div id="offersTable" class="admin-table">
                            <div class="text-center p-4">
                                <div class="spinner-border text-warning" role="status">
                                    <span class="visually-hidden">読み込み中...</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <div class="col-12">
                <div class="card shadow-lg border-0">
                    <div class="card-header card-header-light">
                        <h5 class="mb-0 fw-bold">
                            <i class="bi bi-tags-fill me-2 text-info"></i>カテゴリー管理
                        </h5>
                    </div>
                    <div class="card-body p-0">
                        <div id="categoriesTable" class="admin-table">
                            <div class="text-center p-4">
                                <div class="spinner-border text-primary" role="status">
                                    <span class="visually-hidden">読み込み中...</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Banner Management -->
            <div class="col-12">
                <div class="card shadow-lg border-0">
                    <div class="card-header card-header-light border-bottom d-flex justify-content-between align-items-center">
                        <h5 class="mb-0 fw-bold">
                            <i class="bi bi-image me-2 text-primary"></i>バナー管理
                        </h5>
                        <a href="${pageContext.request.contextPath}/admin/banners.jsp" class="btn btn-primary btn-sm">
                            <i class="bi bi-arrow-right me-1"></i>バナー管理ページへ
                        </a>
                    </div>
                    <div class="card-body">
                        <p class="text-muted mb-0">バナーの作成、編集、削除は専用ページで行えます。</p>
                    </div>
                </div>
            </div>

            <div class="col-12">
                <div class="card shadow-lg border-0">
                    <div class="card-header card-header-light">
                        <h5 class="mb-0 fw-bold">
                            <i class="bi bi-clock-history me-2 text-info"></i>アクティビティログ
                        </h5>
                    </div>
                    <div class="card-body p-0">
                        <div id="activityLogs" class="admin-table">
                            <div class="text-center p-4">
                                <div class="spinner-border text-info" role="status">
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

<script src="${pageContext.request.contextPath}/js/admin.js"></script>
<%@ include file="../includes/footer.jsp" %>

