<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="includes/header.jsp" %>

<main class="bg-dark text-light py-5">
    <div class="container">
        <div class="row justify-content-center">
            <div class="col-lg-8">
                <h1 class="fw-bold mb-4">よくある質問（FAQ）</h1>
                
                <div class="accordion" id="faqAccordion">
                    <div class="accordion-item bg-black border-secondary">
                        <h2 class="accordion-header">
                            <button class="accordion-button bg-dark text-light" type="button" data-bs-toggle="collapse" data-bs-target="#faq1">
                                アカウントの作成方法は？
                            </button>
                        </h2>
                        <div id="faq1" class="accordion-collapse collapse show" data-bs-parent="#faqAccordion">
                            <div class="accordion-body">
                                トップページの「新規登録」ボタンから、メールアドレスとパスワードを入力してアカウントを作成できます。
                            </div>
                        </div>
                    </div>
                    
                    <div class="accordion-item bg-black border-secondary">
                        <h2 class="accordion-header">
                            <button class="accordion-button collapsed bg-dark text-light" type="button" data-bs-toggle="collapse" data-bs-target="#faq2">
                                商品を出品するには？
                            </button>
                        </h2>
                        <div id="faq2" class="accordion-collapse collapse" data-bs-parent="#faqAccordion">
                            <div class="accordion-body">
                                ログイン後、「商品を出品」ページから商品情報を入力し、画像をアップロードして出品できます。
                            </div>
                        </div>
                    </div>
                    
                    <div class="accordion-item bg-black border-secondary">
                        <h2 class="accordion-header">
                            <button class="accordion-button collapsed bg-dark text-light" type="button" data-bs-toggle="collapse" data-bs-target="#faq3">
                                支払い方法は？
                            </button>
                        </h2>
                        <div id="faq3" class="accordion-collapse collapse" data-bs-parent="#faqAccordion">
                            <div class="accordion-body">
                                ウォレット機能を使用して、事前にチャージした残高から支払いができます。
                            </div>
                        </div>
                    </div>
                    
                    <div class="accordion-item bg-black border-secondary">
                        <h2 class="accordion-header">
                            <button class="accordion-button collapsed bg-dark text-light" type="button" data-bs-toggle="collapse" data-bs-target="#faq4">
                                返品・交換は可能ですか？
                            </button>
                        </h2>
                        <div id="faq4" class="accordion-collapse collapse" data-bs-parent="#faqAccordion">
                            <div class="accordion-body">
                                商品の状態によって異なります。商品詳細ページで確認するか、販売者に直接お問い合わせください。
                            </div>
                        </div>
                    </div>
                    
                    <div class="accordion-item bg-black border-secondary">
                        <h2 class="accordion-header">
                            <button class="accordion-button collapsed bg-dark text-light" type="button" data-bs-toggle="collapse" data-bs-target="#faq5">
                                トラブルが発生した場合は？
                            </button>
                        </h2>
                        <div id="faq5" class="accordion-collapse collapse" data-bs-parent="#faqAccordion">
                            <div class="accordion-body">
                                <a href="${pageContext.request.contextPath}/contact.jsp" class="text-primary">お問い合わせページ</a>からご連絡ください。迅速に対応いたします。
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</main>

<%@ include file="includes/footer.jsp" %>

