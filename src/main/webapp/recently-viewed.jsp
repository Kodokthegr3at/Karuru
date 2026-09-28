<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="pageTitle" value="最近見た商品"/>
<%@ include file="includes/header.jsp" %>

<main id="main">
    <div class="container">
        <header class="page-header">
            <h1>最近見た商品</h1>
            <button class="btn btn-link" type="button" id="clearHistory" hidden>履歴を消去</button>
        </header>
        <div class="product-grid" id="recentlyViewedProducts"></div>
    </div>
</main>

<script src="${pageContext.request.contextPath}/js/recently-viewed.js"></script>
<%@ include file="includes/footer.jsp" %>
