<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="pageTitle" value="注文履歴"/>
<%@ include file="includes/header.jsp" %>

<main id="main">
    <div class="container">
        <header class="page-header">
            <h1>注文履歴</h1>
            <div>
                <label class="visually-hidden" for="statusFilter">状態で絞り込む</label>
                <select class="form-select" id="statusFilter">
                    <option value="">すべての注文</option>
                </select>
            </div>
        </header>
        <ul class="record-list" id="ordersList"></ul>
    </div>
</main>

<script src="${pageContext.request.contextPath}/js/orders.js"></script>
<%@ include file="includes/footer.jsp" %>
