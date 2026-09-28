<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%-- Page head and site navigation. A page may <c:set var="pageTitle" value="..."/> before including this. --%>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<c:set var="signedIn" value="${not empty sessionScope.user_id}"/>
<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
    <title><c:if test="${not empty pageTitle}"><c:out value="${pageTitle}"/> | </c:if>カルル</title>
    <link rel="icon" type="image/png" href="${ctx}/img/logo.png">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Noto+Sans+JP:wght@400;500;700&display=swap">
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" integrity="sha384-T3c6CoIi6uLrA9TneNEoa7RxnatzjcDSCmG1MXxSR1GAsXEV/Dwwykc2MPK8M2HN" crossorigin="anonymous">
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.1/font/bootstrap-icons.css">
    <link rel="stylesheet" href="${ctx}/css/style.css">
    <script>
        window.CONTEXT_PATH = '${ctx}';
        <c:if test="${signedIn}">window.currentUserId = ${sessionScope.user_id};</c:if>
    </script>
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js" integrity="sha384-C6RzsynM9kWDrMNeT87bh95OGNyZPhcTNXj1NW7RuBCsyN/o0jlpcV8Qyq46cDfL" crossorigin="anonymous"></script>
    <script src="${ctx}/js/main.js"></script>
    <c:if test="${signedIn}"><script src="${ctx}/js/websocket.js"></script></c:if>
</head>
<body>
<a class="skip-link" href="#main">本文へスキップ</a>

<header class="site-header">
    <div class="container site-header-inner">
        <a class="brand" href="${ctx}/index.jsp">
            <img src="${ctx}/img/logo.png" alt="" width="28" height="28">
            <span>カルル</span>
        </a>

        <nav class="site-nav d-none d-lg-flex" aria-label="メイン">
            <a href="${ctx}/products.jsp">商品を探す</a>
            <a href="${ctx}/categories.jsp">カテゴリー</a>
            <a href="${ctx}/rental.jsp">レンタル</a>
        </nav>

        <div class="site-actions">
            <c:choose>
                <c:when test="${signedIn}">
                    <a class="icon-link-btn d-none d-lg-inline-flex" href="${ctx}/favorites.jsp" aria-label="お気に入り">
                        <i class="bi bi-heart" data-favorites-icon></i>
                    </a>
                    <a class="icon-link-btn" href="${ctx}/messages.jsp" aria-label="メッセージ">
                        <i class="bi bi-chat"></i><span class="count-badge" id="messageBadge" hidden></span>
                    </a>
                    <a class="icon-link-btn" href="${ctx}/notifications.jsp" aria-label="通知">
                        <i class="bi bi-bell"></i><span class="count-badge" id="notificationBadge" hidden></span>
                    </a>
                    <a class="icon-link-btn d-none d-lg-inline-flex" href="${ctx}/cart.jsp" aria-label="カート">
                        <i class="bi bi-bag"></i><span class="count-badge" id="cartBadge" hidden></span>
                    </a>
                    <a class="btn btn-primary d-none d-lg-inline-flex" href="${ctx}/create-listing.jsp">出品する</a>
                    <div class="dropdown d-none d-lg-block">
                        <button class="account-btn" type="button" data-bs-toggle="dropdown" aria-expanded="false">
                            <i class="bi bi-person-circle"></i>
                            <span class="account-name"><c:out value="${sessionScope.username}"/></span>
                            <i class="bi bi-chevron-down small"></i>
                        </button>
                        <ul class="dropdown-menu dropdown-menu-end account-menu">
                            <%@ include file="account-menu.jspf" %>
                        </ul>
                    </div>
                </c:when>
                <c:otherwise>
                    <a class="btn btn-link" href="${ctx}/login.jsp">ログイン</a>
                    <a class="btn btn-primary" href="${ctx}/register.jsp">新規登録</a>
                </c:otherwise>
            </c:choose>
        </div>
    </div>
</header>

<nav class="tab-bar d-lg-none" aria-label="メイン">
    <a class="tab-bar-item" href="${ctx}/index.jsp" data-page="index.jsp"><i class="bi bi-house"></i><span>ホーム</span></a>
    <a class="tab-bar-item" href="${ctx}/products.jsp" data-page="products.jsp categories.jsp"><i class="bi bi-search"></i><span>探す</span></a>
    <c:choose>
        <c:when test="${signedIn}">
            <a class="tab-bar-item" href="${ctx}/create-listing.jsp" data-page="create-listing.jsp"><i class="bi bi-plus-square"></i><span>出品</span></a>
            <a class="tab-bar-item" href="${ctx}/cart.jsp" data-page="cart.jsp">
                <i class="bi bi-bag"></i><span>カート</span><span class="count-badge" id="mobileCartBadge" hidden></span>
            </a>
            <button class="tab-bar-item" type="button" data-bs-toggle="offcanvas" data-bs-target="#accountSheet" aria-controls="accountSheet">
                <i class="bi bi-person"></i><span>マイページ</span>
            </button>
        </c:when>
        <c:otherwise>
            <a class="tab-bar-item" href="${ctx}/rental.jsp" data-page="rental.jsp"><i class="bi bi-calendar"></i><span>レンタル</span></a>
            <a class="tab-bar-item" href="${ctx}/login.jsp" data-page="login.jsp register.jsp"><i class="bi bi-person"></i><span>ログイン</span></a>
        </c:otherwise>
    </c:choose>
</nav>

<c:if test="${signedIn}">
<div class="offcanvas offcanvas-bottom account-sheet d-lg-none" tabindex="-1" id="accountSheet" aria-labelledby="accountSheetTitle">
    <div class="offcanvas-header">
        <h2 class="offcanvas-title h6" id="accountSheetTitle"><c:out value="${sessionScope.username}"/></h2>
        <button type="button" class="btn-close" data-bs-dismiss="offcanvas" aria-label="閉じる"></button>
    </div>
    <div class="offcanvas-body p-0">
        <ul class="dropdown-menu show position-static account-menu w-100 border-0 shadow-none">
            <li><a class="dropdown-item" href="${ctx}/favorites.jsp"><i class="bi bi-heart"></i>お気に入り</a></li>
            <%@ include file="account-menu.jspf" %>
        </ul>
    </div>
</div>
</c:if>
