<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="pageTitle" value="商品を探す"/>
<%@ include file="includes/header.jsp" %>

<main id="main" class="products-page">
    <div class="container">
        <header class="page-header">
            <div>
                <h1 id="resultsTitle">商品を探す</h1>
                <p id="resultsInfo" aria-live="polite"></p>
            </div>
        </header>

        <%-- The filters are a plain GET form: the URL holds the search, so results can be bookmarked and shared. --%>
        <form class="search-layout" id="filterForm" action="${pageContext.request.contextPath}/products.jsp" role="search">
            <div class="search-bar">
                <label class="visually-hidden" for="searchInput">キーワード</label>
                <input class="form-control" type="search" id="searchInput" name="search" list="searchSuggestions"
                       placeholder="キーワードで検索" autocomplete="off">
                <datalist id="searchSuggestions"></datalist>
                <button class="btn btn-primary" type="submit" aria-label="検索"><i class="bi bi-search"></i></button>
                <label class="visually-hidden" for="sortFilter">並び替え</label>
                <select class="form-select" id="sortFilter" name="sort">
                    <option value="newest">新着順</option>
                    <option value="popular">人気順</option>
                    <option value="price-low">価格の安い順</option>
                    <option value="price-high">価格の高い順</option>
                </select>
                <button class="btn btn-outline-secondary d-lg-none" type="button" data-bs-toggle="collapse"
                        data-bs-target="#filterPanel" aria-expanded="false" aria-controls="filterPanel">
                    <i class="bi bi-sliders"></i>絞り込み
                </button>
            </div>

            <aside class="filter-panel collapse d-lg-block" id="filterPanel" aria-label="絞り込み">
                <div class="filter-group">
                    <label class="form-label" for="categoryFilter">カテゴリー</label>
                    <select class="form-select" id="categoryFilter" name="category">
                        <option value="">すべて</option>
                    </select>
                </div>

                <fieldset class="filter-group">
                    <legend class="form-label">価格</legend>
                    <div class="price-inputs">
                        <input class="form-control" type="number" name="min" min="0" step="100" placeholder="下限" aria-label="価格の下限">
                        <span aria-hidden="true">〜</span>
                        <input class="form-control" type="number" name="max" min="0" step="100" placeholder="上限" aria-label="価格の上限">
                    </div>
                </fieldset>

                <fieldset class="filter-group">
                    <legend class="form-label">商品の状態</legend>
                    <div id="conditionFilters"></div>
                </fieldset>

                <div class="filter-group form-check form-switch">
                    <input class="form-check-input" type="checkbox" role="switch" id="rentalFilter" name="rental" value="1">
                    <label class="form-check-label" for="rentalFilter">レンタルできる商品のみ</label>
                </div>

                <div class="filter-actions">
                    <button class="btn btn-primary" type="submit">この条件で探す</button>
                    <a class="btn btn-link" href="${pageContext.request.contextPath}/products.jsp">条件をクリア</a>
                </div>
            </aside>

            <section class="search-results" aria-labelledby="resultsTitle">
                <div class="related-matches" id="relatedMatches" hidden></div>
                <div class="product-grid" id="productsGrid"></div>
                <div class="text-center mt-4">
                    <button class="btn btn-outline-secondary" type="button" id="loadMore" hidden>もっと見る</button>
                </div>
            </section>
        </form>
    </div>
</main>

<script src="${pageContext.request.contextPath}/js/products.js"></script>
<%@ include file="includes/footer.jsp" %>
