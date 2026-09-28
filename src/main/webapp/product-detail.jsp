<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="includes/header.jsp" %>

<main id="main" class="product-page">
    <div class="container">
        <div id="productDetail" aria-live="polite"></div>

        <section class="section" id="reviewsSection" aria-labelledby="reviewsTitle" hidden>
            <div class="section-head">
                <h2 id="reviewsTitle">レビュー</h2>
            </div>
            <div id="reviewsContainer"></div>

            <c:if test="${signedIn}">
            <form class="panel review-form" id="reviewForm" hidden>
                <h3>レビューを書く</h3>
                <fieldset class="mb-3">
                    <legend class="form-label">評価</legend>
                    <div class="star-input">
                        <c:forEach var="n" begin="1" end="5">
                        <c:set var="stars" value="${6 - n}"/>
                        <input type="radio" name="rating" id="rating${stars}" value="${stars}" required>
                        <label for="rating${stars}" title="${stars}"><i class="bi bi-star-fill"></i><span class="visually-hidden">${stars}</span></label>
                        </c:forEach>
                    </div>
                </fieldset>
                <div class="mb-3">
                    <label class="form-label" for="reviewText">コメント</label>
                    <textarea class="form-control" id="reviewText" name="review_text" rows="4" maxlength="2000" required
                              placeholder="使い心地や商品の状態など"></textarea>
                </div>
                <button class="btn btn-primary" type="submit">投稿する</button>
            </form>
            </c:if>
        </section>

        <section class="section" id="relatedSection" aria-labelledby="relatedTitle" hidden>
            <div class="section-head">
                <h2 id="relatedTitle">似ている商品</h2>
            </div>
            <div class="product-grid" id="relatedProducts"></div>
        </section>
    </div>
</main>

<div class="modal fade" id="offerModal" tabindex="-1" aria-labelledby="offerModalTitle" aria-hidden="true">
    <div class="modal-dialog modal-dialog-centered">
        <form class="modal-content" id="offerForm">
            <div class="modal-header">
                <h2 class="modal-title h5" id="offerModalTitle">値下げを交渉する</h2>
                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="閉じる"></button>
            </div>
            <div class="modal-body">
                <p class="text-muted">販売価格 <span class="price" id="offerCurrentPrice"></span></p>
                <div class="mb-3">
                    <label class="form-label" for="offerPrice">希望価格</label>
                    <div class="input-group">
                        <span class="input-group-text">¥</span>
                        <input class="form-control" type="number" id="offerPrice" name="offer_price" min="1" step="1" required>
                    </div>
                    <div class="form-text">販売価格より低い金額を入力してください。</div>
                </div>
                <div>
                    <label class="form-label" for="offerMessage">メッセージ（任意）</label>
                    <textarea class="form-control" id="offerMessage" name="message" rows="3" maxlength="500"></textarea>
                </div>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-link" data-bs-dismiss="modal">キャンセル</button>
                <button type="submit" class="btn btn-primary">送信する</button>
            </div>
        </form>
    </div>
</div>

<script src="${pageContext.request.contextPath}/js/product-detail.js"></script>
<%@ include file="includes/footer.jsp" %>
