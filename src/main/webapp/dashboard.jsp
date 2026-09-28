<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="pageTitle" value="出品者ダッシュボード"/>
<%@ include file="includes/header.jsp" %>

<main id="main">
    <div class="container">
        <header class="page-header">
            <h1>出品者ダッシュボード</h1>
            <a class="btn btn-primary" href="${pageContext.request.contextPath}/create-listing.jsp">出品する</a>
        </header>

        <div class="stat-grid" aria-live="polite">
            <div class="stat"><span class="stat-label">出品中</span><span class="stat-value" id="activeListings">-</span></div>
            <div class="stat"><span class="stat-label">今月の売上</span><span class="stat-value" id="salesThisMonth">-</span>
                <span class="stat-note" id="salesLastMonth"></span></div>
            <div class="stat"><span class="stat-label">対応が必要な注文</span><span class="stat-value" id="ordersToHandle">-</span></div>
            <div class="stat"><span class="stat-label">未読メッセージ</span><span class="stat-value" id="unreadMessages">-</span></div>
        </div>

        <section class="section" aria-labelledby="ordersTitle">
            <div class="section-head">
                <h2 id="ordersTitle">受けた注文</h2>
            </div>
            <ul class="record-list" id="sellerOrders"></ul>
        </section>

        <section class="section" aria-labelledby="salesTitle">
            <div class="section-head">
                <h2 id="salesTitle">売上の推移</h2>
            </div>
            <div class="panel chart-panel">
                <canvas id="salesChart" aria-label="日別の売上" role="img"></canvas>
                <p class="text-muted mb-0" id="salesEmpty" hidden>まだ売上はありません。</p>
            </div>
        </section>

        <section class="section" aria-labelledby="listingsTitle">
            <div class="section-head">
                <h2 id="listingsTitle">出品した商品</h2>
            </div>
            <ul class="record-list" id="userProducts"></ul>
        </section>
    </div>
</main>

<script src="${pageContext.request.contextPath}/js/chart.umd.min.js"></script>
<script src="${pageContext.request.contextPath}/js/dashboard.js"></script>
<%@ include file="includes/footer.jsp" %>
