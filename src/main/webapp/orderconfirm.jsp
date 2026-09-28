<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="pageTitle" value="ご注文ありがとうございました"/>
<%@ include file="includes/header.jsp" %>

<main id="main" class="narrow-page">
    <div class="container">
        <div class="confirm-head">
            <i class="bi bi-check-circle-fill" aria-hidden="true"></i>
            <h1>ご注文ありがとうございました</h1>
            <p class="text-muted" id="confirmNote"></p>
        </div>
        <div id="orderSummary"></div>
        <div class="confirm-actions">
            <a class="btn btn-primary" id="orderDetailLink" href="${pageContext.request.contextPath}/orders.jsp">注文の詳細を見る</a>
            <a class="btn btn-link" href="${pageContext.request.contextPath}/products.jsp">買い物を続ける</a>
        </div>
    </div>
</main>

<script src="${pageContext.request.contextPath}/js/orderconfirm.js"></script>
<%@ include file="includes/footer.jsp" %>
