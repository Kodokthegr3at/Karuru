<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="includes/header.jsp" %>

<main class="bg-dark text-light py-5">
    <div class="container">
        <div class="row justify-content-center">
            <div class="col-lg-8">
                <h1 class="fw-bold mb-4">当サイトについて</h1>
                
                <div class="card bg-black border-secondary mb-4">
                    <div class="card-body">
                        <h3 class="mb-3">カルルとは</h3>
                        <p>カルルは、中古品の売買・レンタルをサポートするプラットフォームです。不要になったものを、必要としている人に届けることで、持続可能な社会の実現を目指しています。</p>
                    </div>
                </div>
                
                <div class="card bg-black border-secondary mb-4">
                    <div class="card-body">
                        <h3 class="mb-3">私たちのミッション</h3>
                        <p>モノを大切に使い、循環させることで、環境に優しく、経済的にも持続可能な社会を創ります。</p>
                    </div>
                </div>
                
                <div class="card bg-black border-secondary">
                    <div class="card-body">
                        <h3 class="mb-3">お問い合わせ</h3>
                        <p>ご質問やご意見がございましたら、<a href="${pageContext.request.contextPath}/contact.jsp" class="text-primary">お問い合わせページ</a>からご連絡ください。</p>
                    </div>
                </div>
            </div>
        </div>
    </div>
</main>

<%@ include file="includes/footer.jsp" %>

