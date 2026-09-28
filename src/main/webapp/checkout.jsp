<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="pageTitle" value="購入手続き"/>
<%@ include file="includes/header.jsp" %>

<main id="main" class="checkout-page">
    <div class="container">
        <header class="page-header">
            <h1>購入手続き</h1>
        </header>

        <form class="checkout-layout" id="checkoutForm" novalidate>
            <div class="checkout-steps">
                <section class="panel" aria-labelledby="addressTitle">
                    <div class="section-head">
                        <h2 id="addressTitle">お届け先</h2>
                        <button type="button" class="btn btn-link btn-sm" data-bs-toggle="modal" data-bs-target="#addressModal">
                            <i class="bi bi-plus"></i>住所を追加
                        </button>
                    </div>
                    <div class="choice-list" id="addressesList"></div>
                </section>

                <section class="panel" aria-labelledby="deliveryTitle">
                    <h2 id="deliveryTitle">配送方法</h2>
                    <div class="choice-list">
                        <label class="choice">
                            <input class="form-check-input" type="radio" name="delivery_method" value="standard" checked>
                            <span class="choice-body"><strong>通常配送</strong><small>3〜5営業日でお届け</small></span>
                            <span class="price">¥500</span>
                        </label>
                        <label class="choice">
                            <input class="form-check-input" type="radio" name="delivery_method" value="express">
                            <span class="choice-body"><strong>お急ぎ便</strong><small>1〜2営業日でお届け</small></span>
                            <span class="price">¥1,200</span>
                        </label>
                        <label class="choice">
                            <input class="form-check-input" type="radio" name="delivery_method" value="same_day">
                            <span class="choice-body"><strong>当日配送</strong><small>正午までのご注文で当日中</small></span>
                            <span class="price">¥2,500</span>
                        </label>
                    </div>
                    <p class="form-text mt-2 mb-0">複数の出品者の商品をまとめて購入する場合、出品者が1人増えるごとに送料が¥200加算されます。</p>
                    <label class="form-label mt-3" for="courier">配送業者</label>
                    <select class="form-select" id="courier" name="courier">
                        <option value="yamato">ヤマト運輸</option>
                        <option value="sagawa">佐川急便</option>
                        <option value="japan_post">日本郵便</option>
                    </select>
                </section>

                <section class="panel" aria-labelledby="paymentTitle">
                    <h2 id="paymentTitle">支払い方法</h2>
                    <div class="choice-list">
                        <label class="choice">
                            <input class="form-check-input" type="radio" name="payment_method" value="wallet" checked>
                            <span class="choice-body"><strong>ウォレット残高</strong><small>すぐに支払いが完了します</small></span>
                        </label>
                        <label class="choice">
                            <input class="form-check-input" type="radio" name="payment_method" value="credit_card">
                            <span class="choice-body"><strong>クレジットカード</strong><small>デモ環境のため実際の請求は発生しません</small></span>
                        </label>
                        <label class="choice">
                            <input class="form-check-input" type="radio" name="payment_method" value="bank_transfer">
                            <span class="choice-body"><strong>銀行振込</strong><small>入金確認後に発送されます</small></span>
                        </label>
                        <label class="choice">
                            <input class="form-check-input" type="radio" name="payment_method" value="cod">
                            <span class="choice-body"><strong>代金引換</strong><small>商品の受け取り時にお支払い</small></span>
                        </label>
                    </div>
                </section>

                <section class="panel" aria-labelledby="itemsTitle">
                    <h2 id="itemsTitle">ご注文内容</h2>
                    <ul class="line-items line-items-compact" id="orderItems"></ul>
                </section>
            </div>

            <aside class="panel order-summary" aria-label="お支払い金額">
                <dl class="summary-list">
                    <div><dt>商品の小計</dt><dd class="price" id="subtotal"></dd></div>
                    <div><dt>送料</dt><dd class="price" id="shipping"></dd></div>
                    <div><dt>手数料</dt><dd class="price" id="fee"></dd></div>
                    <div class="summary-total"><dt>お支払い金額</dt><dd class="price" id="total"></dd></div>
                </dl>
                <p class="form-text text-success" id="savings"></p>
                <button class="btn btn-primary btn-lg w-100" type="submit" id="placeOrderBtn" disabled>注文を確定する</button>
            </aside>
        </form>
    </div>
</main>

<div class="modal fade" id="addressModal" tabindex="-1" aria-labelledby="addressModalTitle" aria-hidden="true">
    <div class="modal-dialog modal-dialog-scrollable">
        <form class="modal-content" id="addressForm">
            <div class="modal-header">
                <h2 class="modal-title h5" id="addressModalTitle">住所を追加</h2>
                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="閉じる"></button>
            </div>
            <div class="modal-body">
                <div class="row g-3">
                    <div class="col-12">
                        <label class="form-label" for="recipientName">お名前</label>
                        <input class="form-control" id="recipientName" name="full_name" autocomplete="name" required>
                    </div>
                    <div class="col-12">
                        <label class="form-label" for="phone">電話番号</label>
                        <input class="form-control" type="tel" id="phone" name="phone" autocomplete="tel" placeholder="090-1234-5678" required>
                    </div>
                    <div class="col-sm-5">
                        <label class="form-label" for="postalCode">郵便番号</label>
                        <input class="form-control" id="postalCode" name="postal_code" autocomplete="postal-code"
                               inputmode="numeric" pattern="\d{3}-?\d{4}" maxlength="8" placeholder="123-4567" required>
                        <div class="form-text" id="postalStatus" aria-live="polite">入力すると住所を自動で補完します</div>
                    </div>
                    <div class="col-sm-7">
                        <label class="form-label" for="prefecture">都道府県</label>
                        <input class="form-control" id="prefecture" name="prefecture" autocomplete="address-level1" required>
                    </div>
                    <div class="col-12">
                        <label class="form-label" for="city">市区町村</label>
                        <input class="form-control" id="city" name="city" autocomplete="address-level2" required>
                    </div>
                    <div class="col-12">
                        <label class="form-label" for="addressLine1">町名・番地</label>
                        <input class="form-control" id="addressLine1" name="address_line" autocomplete="address-line1" required>
                    </div>
                    <div class="col-12">
                        <label class="form-label" for="building">建物名・部屋番号（任意）</label>
                        <input class="form-control" id="building" name="building" autocomplete="address-line2">
                    </div>
                    <div class="col-12">
                        <label class="form-label" for="addressLabel">呼び名（任意）</label>
                        <input class="form-control" id="addressLabel" name="address_label" placeholder="自宅、職場など">
                    </div>
                    <div class="col-12 form-check ms-2">
                        <input class="form-check-input" type="checkbox" id="isDefault" name="is_default" value="true" checked>
                        <label class="form-check-label" for="isDefault">いつも使う住所にする</label>
                    </div>
                </div>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-link" data-bs-dismiss="modal">キャンセル</button>
                <button type="submit" class="btn btn-primary">保存する</button>
            </div>
        </form>
    </div>
</div>

<script src="${pageContext.request.contextPath}/js/checkout.js"></script>
<%@ include file="includes/footer.jsp" %>
