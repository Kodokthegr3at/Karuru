<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="includes/header.jsp" %>

<main class="bg-dark text-light py-5">
    <div class="container">
        <div class="row justify-content-center">
            <div class="col-lg-8">
                <h1 class="fw-bold mb-4">ヘルプ</h1>
                
                <div class="card bg-black border-secondary mb-4">
                    <div class="card-body">
                        <h3 class="mb-3">よくある質問</h3>
                        <p><a href="${pageContext.request.contextPath}/faq.jsp" class="text-primary">よくある質問（FAQ）ページ</a>をご覧ください。</p>
                    </div>
                </div>
                
                <div class="card bg-black border-secondary mb-4">
                    <div class="card-body">
                        <h3 class="mb-3">使い方ガイド</h3>
                        <h5 class="mt-3">商品を出品する</h5>
                        <ol>
                            <li>ログイン後、「商品を出品」をクリック</li>
                            <li>商品情報を入力</li>
                            <li>商品画像をアップロード</li>
                            <li>「商品を出品」ボタンをクリック</li>
                        </ol>
                        
                        <h5 class="mt-4">商品を購入する</h5>
                        <ol>
                            <li>商品一覧から商品を選択</li>
                            <li>商品詳細を確認</li>
                            <li>「カートに追加」をクリック</li>
                            <li>カートからチェックアウト</li>
                        </ol>
                    </div>
                </div>
                
                <div class="card bg-black border-secondary">
                    <div class="card-body">
                        <h3 class="mb-3">お問い合わせ</h3>
                        <p>ご不明な点がございましたら、<a href="${pageContext.request.contextPath}/contact.jsp" class="text-primary">お問い合わせページ</a>からご連絡ください。</p>
                    </div>
                </div>
            </div>
        </div>
    </div>
</main>

<%@ include file="includes/footer.jsp" %>

