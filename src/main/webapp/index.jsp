<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="includes/header.jsp" %>

<main id="main" class="home-page">
    <div class="container">
        <section class="home-intro">
            <h1>あなたの不要なものを、誰かの宝物に。</h1>
            <form class="home-search" action="${pageContext.request.contextPath}/products.jsp" role="search">
                <label class="visually-hidden" for="homeSearch">商品を検索</label>
                <i class="bi bi-search" aria-hidden="true"></i>
                <input class="form-control form-control-lg" type="search" id="homeSearch" name="search"
                       placeholder="何をお探しですか？" autocomplete="off">
            </form>
        </section>

        <section class="home-banners" id="bannerSection" hidden>
            <div id="bannerSlider" class="carousel slide" data-bs-ride="carousel" data-bs-interval="6000">
                <div class="carousel-inner"></div>
                <button class="carousel-control-prev" type="button" data-bs-target="#bannerSlider" data-bs-slide="prev" hidden>
                    <span class="carousel-control-prev-icon" aria-hidden="true"></span>
                    <span class="visually-hidden">前へ</span>
                </button>
                <button class="carousel-control-next" type="button" data-bs-target="#bannerSlider" data-bs-slide="next" hidden>
                    <span class="carousel-control-next-icon" aria-hidden="true"></span>
                    <span class="visually-hidden">次へ</span>
                </button>
            </div>
        </section>

        <section class="section" aria-labelledby="categoriesTitle">
            <div class="section-head">
                <h2 id="categoriesTitle">カテゴリーから探す</h2>
                <a href="${pageContext.request.contextPath}/categories.jsp">すべて見る</a>
            </div>
            <div class="category-strip" id="categoriesGrid"></div>
        </section>

        <section class="section" id="featuredSection" aria-labelledby="featuredTitle" hidden>
            <div class="section-head">
                <h2 id="featuredTitle">おすすめ</h2>
            </div>
            <div class="product-grid" id="featuredProducts"></div>
        </section>

        <section class="section" aria-labelledby="recentTitle">
            <div class="section-head">
                <h2 id="recentTitle">新着商品</h2>
                <a href="${pageContext.request.contextPath}/products.jsp?sort=newest">すべて見る</a>
            </div>
            <div class="product-grid" id="recentProducts"></div>
        </section>

        <section class="section" aria-labelledby="popularTitle">
            <div class="section-head">
                <h2 id="popularTitle">人気の商品</h2>
                <a href="${pageContext.request.contextPath}/products.jsp?sort=popular">すべて見る</a>
            </div>
            <div class="product-grid" id="popularProducts"></div>
        </section>
    </div>
</main>

<script src="${pageContext.request.contextPath}/js/home.js"></script>
<%@ include file="includes/footer.jsp" %>
