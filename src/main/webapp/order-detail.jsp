<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="pageTitle" value="注文の詳細"/>
<%@ include file="includes/header.jsp" %>

<main id="main">
    <div class="container">
        <nav class="breadcrumbs" aria-label="パンくずリスト">
            <a href="${pageContext.request.contextPath}/orders.jsp">注文履歴</a>
        </nav>
        <header class="page-header">
            <div>
                <h1>注文の詳細</h1>
                <p id="orderStatus"></p>
            </div>
        </header>
        <div id="orderDetail"></div>
    </div>
</main>

<div class="modal fade" id="shipModal" tabindex="-1" aria-labelledby="shipModalTitle" aria-hidden="true">
    <div class="modal-dialog">
        <form class="modal-content" id="shipForm">
            <div class="modal-header">
                <h2 class="modal-title h5" id="shipModalTitle">発送の連絡</h2>
                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="閉じる"></button>
            </div>
            <div class="modal-body">
                <div class="mb-3">
                    <label class="form-label" for="courier">配送業者</label>
                    <select class="form-select" id="courier" name="courier" required>
                        <option value="ヤマト運輸">ヤマト運輸</option>
                        <option value="佐川急便">佐川急便</option>
                        <option value="日本郵便">日本郵便</option>
                    </select>
                </div>
                <div>
                    <label class="form-label" for="trackingNumber">追跡番号</label>
                    <input class="form-control" id="trackingNumber" name="tracking_number" maxlength="50" required>
                </div>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-link" data-bs-dismiss="modal">キャンセル</button>
                <button type="submit" class="btn btn-primary">発送済みにする</button>
            </div>
        </form>
    </div>
</div>

<script src="${pageContext.request.contextPath}/js/order-detail.js"></script>
<%@ include file="includes/footer.jsp" %>
