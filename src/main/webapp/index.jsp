<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="includes/header.jsp" %>

<main class="home-page">
    <!-- Hero Banner Section -->
    <section class="hero-banner-section py-0">
        <div class="container-fluid px-0">
            <div id="bannerSlider" class="carousel slide carousel-fade" data-bs-ride="carousel" data-bs-interval="5000">
                <div class="carousel-inner">
                    <div class="text-center py-5">
                        <div class="spinner-border text-primary" role="status">
                            <span class="visually-hidden">読み込み中...</span>
                        </div>
                    </div>
                </div>
                <button class="carousel-control-prev" type="button" data-bs-target="#bannerSlider" data-bs-slide="prev">
                    <span class="carousel-control-prev-icon" aria-hidden="true"></span>
                    <span class="visually-hidden">前へ</span>
                </button>
                <button class="carousel-control-next" type="button" data-bs-target="#bannerSlider" data-bs-slide="next">
                    <span class="carousel-control-next-icon" aria-hidden="true"></span>
                    <span class="visually-hidden">次へ</span>
                </button>
            </div>
        </div>
    </section>

    <!-- Categories Section -->
    <section class="categories-section py-5">
        <div class="container">
            <div class="section-header mb-4">
                <h2 class="section-title fw-bold mb-2">
                    <i class="bi bi-tags-fill me-2 text-primary"></i>カテゴリー
                </h2>
                <p class="text-muted mb-0">商品カテゴリーから探す</p>
            </div>
            <div class="row g-4" id="categoriesGrid">
                <div class="col-12 text-center py-4">
                    <div class="spinner-border text-primary" role="status">
                        <span class="visually-hidden">読み込み中...</span>
                    </div>
                </div>
            </div>
        </div>
    </section>

    <!-- Featured Products Section -->
    <section class="featured-products-section py-5 bg-section-alt">
        <div class="container">
            <div class="section-header mb-4">
                <h2 class="section-title fw-bold mb-2">
                    <i class="bi bi-star-fill me-2 text-primary"></i>おすすめ商品
                </h2>
                <p class="text-muted mb-0">厳選されたおすすめ商品</p>
            </div>
            <div class="row g-4" id="featuredProducts">
                <div class="col-12 text-center py-4">
                    <div class="spinner-border text-primary" role="status">
                        <span class="visually-hidden">読み込み中...</span>
                    </div>
                </div>
            </div>
        </div>
    </section>

    <!-- Recent Products Section -->
    <section class="recent-products-section py-5">
        <div class="container">
            <div class="section-header mb-4">
                <h2 class="section-title fw-bold mb-2">
                    <i class="bi bi-clock-history me-2 text-primary"></i>新着商品
                </h2>
                <p class="text-muted mb-0">最新の出品商品</p>
            </div>
            <div class="row g-4" id="recentProducts">
                <div class="col-12 text-center py-4">
                    <div class="spinner-border text-primary" role="status">
                        <span class="visually-hidden">読み込み中...</span>
                    </div>
                </div>
            </div>
        </div>
    </section>

    <!-- Popular Products Section -->
    <section class="popular-products-section py-5 bg-section-alt">
        <div class="container">
            <div class="section-header mb-4">
                <h2 class="section-title fw-bold mb-2">
                    <i class="bi bi-fire me-2 text-primary"></i>人気商品
                </h2>
                <p class="text-muted mb-0">今注目の商品</p>
            </div>
            <div class="row g-4" id="popularProducts">
                <div class="col-12 text-center py-4">
                    <div class="spinner-border text-primary" role="status">
                        <span class="visually-hidden">読み込み中...</span>
                    </div>
                </div>
            </div>
        </div>
    </section>
</main>

<script src="${pageContext.request.contextPath}/js/home.js"></script>
<%@ include file="includes/footer.jsp" %>
