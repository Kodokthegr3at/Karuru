<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="includes/header.jsp" %>

<main id="main">
    <div class="container">
        <div id="sellerProfile"></div>
        <section class="section" aria-labelledby="sellerProductsTitle">
            <div class="section-head"><h2 id="sellerProductsTitle">出品中の商品</h2></div>
            <div class="product-grid" id="sellerProducts"></div>
        </section>
    </div>
</main>

<script src="${pageContext.request.contextPath}/js/seller.js"></script>
<%@ include file="includes/footer.jsp" %>
