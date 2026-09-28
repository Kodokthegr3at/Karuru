<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="pageTitle" value="お気に入り"/>
<%@ include file="includes/header.jsp" %>

<main id="main">
    <div class="container">
        <header class="page-header"><h1>お気に入り</h1></header>
        <div class="product-grid" id="favoritesGrid"></div>
    </div>
</main>

<script src="${pageContext.request.contextPath}/js/favorites.js"></script>
<%@ include file="includes/footer.jsp" %>
