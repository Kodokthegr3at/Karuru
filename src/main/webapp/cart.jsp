<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="pageTitle" value="カート"/>
<%@ include file="includes/header.jsp" %>

<main id="main" class="cart-page">
    <div class="container">
        <header class="page-header">
            <h1>カート</h1>
        </header>

        <div class="checkout-layout" id="cartContainer" hidden>
            <section aria-label="カートの商品">
                <ul class="line-items" id="cartItems"></ul>
            </section>
            <aside class="panel order-summary" aria-label="合計">
                <dl class="summary-list">
                    <div><dt>小計</dt><dd class="price" id="subtotal"></dd></div>
                    <div><dt>送料（目安）</dt><dd class="price" id="shipping"></dd></div>
                    <div class="summary-total"><dt>合計</dt><dd class="price" id="total"></dd></div>
                </dl>
                <p class="form-text">送料は次の画面で選ぶ配送方法によって変わります。</p>
                <a class="btn btn-primary btn-lg w-100" href="${pageContext.request.contextPath}/checkout.jsp">購入手続きへ</a>
            </aside>
        </div>

        <div class="empty-state" id="emptyCart" hidden>
            <i class="bi bi-bag"></i>
            <p>カートに商品はありません</p>
            <a class="btn btn-primary" href="${pageContext.request.contextPath}/products.jsp">商品を探す</a>
        </div>
    </div>
</main>

<script src="${pageContext.request.contextPath}/js/cart.js"></script>
<%@ include file="includes/footer.jsp" %>
