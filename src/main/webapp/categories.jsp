<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="pageTitle" value="カテゴリー"/>
<%@ include file="includes/header.jsp" %>

<main id="main">
    <div class="container">
        <header class="page-header">
            <h1>カテゴリー</h1>
        </header>
        <div class="category-strip category-grid" id="categoriesGrid"></div>
    </div>
</main>

<script src="${pageContext.request.contextPath}/js/categories.js"></script>
<%@ include file="includes/footer.jsp" %>
