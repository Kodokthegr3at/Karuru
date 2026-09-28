<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="pageTitle" value="マイガレージ"/>
<%@ include file="includes/header.jsp" %>

<main id="main">
    <div class="container">
        <header class="page-header">
            <div>
                <h1>マイガレージ</h1>
                <p>これまでに購入した商品です。</p>
            </div>
        </header>
        <ul class="record-list" id="garageProducts"></ul>
    </div>
</main>

<script src="${pageContext.request.contextPath}/js/garage.js"></script>
<%@ include file="includes/footer.jsp" %>
