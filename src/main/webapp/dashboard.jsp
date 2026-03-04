<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true"%>
<%@ include file="includes/header.jsp" %>
<%
    if (currentUser == null) {
        response.sendRedirect("login.jsp?redirect=dashboard");
        return;
    }
%>

<main class="page-main py-4 dashboard-page">
    <div class="container-fluid px-4">
        <div class="d-flex justify-content-between align-items-center mb-4">
            <h1 class="h2 mb-0 fw-bold page-header">
                <i class="bi bi-speedometer2 me-2 text-primary"></i>ダッシュボード
            </h1>
        </div>
        
        <!-- Statistics Grid - 4 cols (2x2) on mobile, 4 cols on desktop -->
        <div class="row g-2 g-md-4 mb-4 stat-cards-grid">
            <div class="col-6 col-xl-3">
                <div class="stat-card stat-card-primary">
                    <div class="stat-card-icon">
                        <i class="bi bi-box-seam-fill"></i>
                    </div>
                    <div class="stat-card-content">
                        <h6 class="stat-card-label">出品中</h6>
                        <h2 class="stat-card-value" id="activeListings">0</h2>
                        <small class="stat-card-change text-info">
                            <i class="bi bi-grid-3x3-gap"></i> <span class="text-muted">アクティブ</span>
                        </small>
                    </div>
                </div>
            </div>
            <div class="col-6 col-xl-3">
                <div class="stat-card stat-card-success">
                    <div class="stat-card-icon">
                        <i class="bi bi-currency-yen"></i>
                    </div>
                    <div class="stat-card-content">
                        <h6 class="stat-card-label">売上</h6>
                        <h2 class="stat-card-value" id="totalSales">¥0</h2>
                        <small class="stat-card-change text-success">
                            <i class="bi bi-graph-up"></i> <span class="text-muted">今月</span>
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
                        <h6 class="stat-card-label">注文数</h6>
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
                        <i class="bi bi-envelope-fill"></i>
                    </div>
                    <div class="stat-card-content">
                        <h6 class="stat-card-label">未読メッセージ</h6>
                        <h2 class="stat-card-value" id="unreadMessages">0</h2>
                        <small class="stat-card-change text-primary">
                            <i class="bi bi-chat-dots"></i> <span class="text-muted">新着</span>
                        </small>
                    </div>
                </div>
            </div>
        </div>

        <!-- Main Content -->
        <div class="row g-4">
            <!-- Left Sidebar Navigation -->
            <div class="col-lg-3">
                <div class="card shadow-lg border-0 dashboard-sidebar">
                    <div class="card-header card-header-light border-0 p-3">
                        <h5 class="mb-0 fw-bold">
                            <i class="bi bi-list-ul me-2"></i>メニュー
                        </h5>
                    </div>
                    <div class="card-body p-0">
                        <nav class="nav flex-column dashboard-nav">
                            <a class="nav-link dashboard-nav-link active" href="#overview" data-section="overview">
                                <i class="bi bi-house-door-fill me-2"></i>概要
                            </a>
                            <a class="nav-link dashboard-nav-link" href="#products" data-section="products">
                                <i class="bi bi-box-seam me-2"></i>出品商品
                            </a>
                            <a class="nav-link dashboard-nav-link" href="#orders" data-section="orders">
                                <i class="bi bi-receipt me-2"></i>注文
                            </a>
                            <a class="nav-link dashboard-nav-link" href="#sales" data-section="sales">
                                <i class="bi bi-graph-up me-2"></i>売上
                            </a>
                            <a class="nav-link dashboard-nav-link" href="#messages" data-section="messages">
                                <i class="bi bi-chat-dots me-2"></i>メッセージ
                            </a>
                            <a class="nav-link dashboard-nav-link" href="#settings" data-section="settings">
                                <i class="bi bi-gear me-2"></i>設定
                            </a>
                            <a class="nav-link dashboard-nav-link" href="${pageContext.request.contextPath}/garage.jsp">
                                <i class="bi bi-box-seam me-2"></i>マイガレージ
                            </a>
                            <a class="nav-link dashboard-nav-link" href="${pageContext.request.contextPath}/recently-viewed.jsp">
                                <i class="bi bi-clock-history me-2"></i>最近閲覧した商品
                            </a>
                        </nav>
                    </div>
                </div>
            </div>

            <!-- Main Content Area -->
            <div class="col-lg-9">
                <!-- Overview Section -->
                <section id="overview" class="dashboard-section">
                    <div class="card shadow-lg border-0 mb-4">
                        <div class="card-header card-header-light border-bottom">
                            <h5 class="mb-0 fw-bold">
                                <i class="bi bi-house-door-fill me-2 text-primary"></i>概要
                            </h5>
                        </div>
                        <div class="card-body">
                            <p class="text-muted mb-0">上記の統計カードで主要な情報を確認できます。</p>
                        </div>
                    </div>
                </section>

                <!-- Products Section -->
                <section id="products" class="dashboard-section d-none">
                    <div class="card shadow-lg border-0 mb-4">
                        <div class="card-header card-header-light border-bottom d-flex justify-content-between align-items-center flex-wrap gap-2">
                            <h5 class="mb-0 fw-bold">
                                <i class="bi bi-box-seam me-2 text-primary"></i>出品商品
                            </h5>
                            <a href="${pageContext.request.contextPath}/create-listing.jsp" class="btn btn-primary btn-sm">
                                <i class="bi bi-plus-circle me-1"></i>新しい商品を出品
                            </a>
                        </div>
                        <div class="card-body">
                            <!-- Search and Filter -->
                            <div class="row mb-3">
                                <div class="col-md-6">
                                    <input type="text" id="productSearchInput" class="form-control form-control-light" 
                                           placeholder="商品名で検索..." onkeyup="filterProducts()">
                                </div>
                                <div class="col-md-3">
                                    <select id="productStatusFilter" class="form-select form-control-light" onchange="filterProducts()">
                                        <option value="">すべての状態</option>
                                        <option value="available">販売中</option>
                                        <option value="sold">売却済み</option>
                                        <option value="reserved">予約中</option>
                                        <option value="draft">下書き</option>
                                    </select>
                                </div>
                                <div class="col-md-3">
                                    <select id="productSortFilter" class="form-select form-control-light" onchange="filterProducts()">
                                        <option value="newest">新着順</option>
                                        <option value="oldest">古い順</option>
                                        <option value="price_high">価格：高い順</option>
                                        <option value="price_low">価格：安い順</option>
                                        <option value="views">閲覧数順</option>
                                    </select>
                                </div>
                            </div>
                            <div id="userProducts" class="row g-3">
                                <div class="col-12 text-center py-4">
                                    <div class="spinner-border text-primary" role="status">
                                        <span class="visually-hidden">読み込み中...</span>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </section>

                <!-- Orders Section -->
                <section id="orders" class="dashboard-section d-none">
                    <div class="card shadow-lg border-0">
                        <div class="card-header card-header-light border-bottom">
                            <h5 class="mb-0 fw-bold">
                                <i class="bi bi-receipt me-2 text-primary"></i>注文
                            </h5>
                        </div>
                        <div class="card-body">
                            <div id="userOrders">
                                <div class="text-center py-4">
                                    <div class="spinner-border text-primary" role="status">
                                        <span class="visually-hidden">読み込み中...</span>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </section>

                <!-- Sales Section with Chart -->
                <section id="sales" class="dashboard-section d-none">
                    <div class="card shadow-lg border-0 mb-4">
                        <div class="card-header card-header-light border-bottom">
                            <h5 class="mb-0 fw-bold">
                                <i class="bi bi-graph-up me-2 text-primary"></i>売上統計
                            </h5>
                        </div>
                        <div class="card-body">
                            <div class="row g-2 mb-4">
                                <div class="col-6 col-md-6">
                                    <div class="card card-light">
                                        <div class="card-body">
                                            <h6 class="text-muted mb-2">今月の売上</h6>
                                            <h3 class="mb-0" id="monthlySales">¥0</h3>
                                        </div>
                                    </div>
                                </div>
                                <div class="col-6 col-md-6">
                                    <div class="card card-light">
                                        <div class="card-body">
                                            <h6 class="text-muted mb-2">今月の注文数</h6>
                                            <h3 class="mb-0" id="monthlyOrders">0</h3>
                                        </div>
                                    </div>
                                </div>
                            </div>
                            <div class="chart-container">
                                <canvas id="salesChart"></canvas>
                            </div>
                        </div>
                    </div>
                </section>

                <!-- Messages Section -->
                <section id="messages" class="dashboard-section d-none">
                    <div class="card shadow-lg border-0">
                        <div class="card-header card-header-light border-bottom">
                            <h5 class="mb-0 fw-bold">
                                <i class="bi bi-chat-dots me-2 text-primary"></i>メッセージ
                            </h5>
                        </div>
                        <div class="card-body">
                            <div id="dashboardMessages">
                                <div class="text-center py-4">
                                    <div class="spinner-border text-primary" role="status">
                                        <span class="visually-hidden">読み込み中...</span>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </section>

                <!-- Settings Section -->
                <section id="settings" class="dashboard-section d-none">
                    <div class="card shadow-lg border-0">
                        <div class="card-header card-header-light border-bottom">
                            <h5 class="mb-0 fw-bold">
                                <i class="bi bi-gear me-2 text-primary"></i>設定
                            </h5>
                        </div>
                        <div class="card-body">
                            <div class="d-grid gap-2">
                                <a href="${pageContext.request.contextPath}/profile.jsp" class="btn btn-outline-primary">
                                    <i class="bi bi-person me-2"></i>プロフィール編集
                                </a>
                                <a href="${pageContext.request.contextPath}/settings.jsp" class="btn btn-outline-primary">
                                    <i class="bi bi-gear me-2"></i>アカウント設定
                                </a>
                            </div>
                        </div>
                    </div>
                </section>
            </div>
        </div>
    </div>
</main>

<script type="text/javascript" src="<%= request.getContextPath() %>/chart-js.jsp"></script>
<script type="text/javascript" src="<%= request.getContextPath() %>/dashboard-js.jsp"></script>
<%@ include file="includes/footer.jsp" %>
