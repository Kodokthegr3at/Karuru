<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!-- Mobile compact footer (light bg, above bottom nav) -->
<footer class="d-lg-none border-top bg-white py-2 mt-3 footer-mobile">
    <div class="container">
        <div class="d-flex flex-wrap justify-content-center gap-2 footer-mobile-links">
            <a href="${pageContext.request.contextPath}/about.jsp" class="text-decoration-none">について</a>
            <span>|</span>
            <a href="${pageContext.request.contextPath}/contact.jsp" class="text-decoration-none">お問い合わせ</a>
            <span>|</span>
            <a href="${pageContext.request.contextPath}/terms.jsp" class="text-decoration-none">利用規約</a>
            <span>|</span>
            <a href="${pageContext.request.contextPath}/privacy.jsp" class="text-decoration-none">プライバシー</a>
        </div>
        <p class="text-center footer-mobile-copyright mb-0 mt-1">&copy; 2024 カルル</p>
    </div>
</footer>
<!-- Desktop footer (hidden on mobile) -->
<footer class="bg-black text-light border-top border-secondary mt-5 d-none d-lg-block">
    <div class="container py-5">
        <div class="row g-4">
            <div class="col-md-4">
                <h5 class="fw-bold mb-3">
                    <i class="bi bi-shop"></i> カルル
                </h5>
                <p class="text-secondary">あなたの不要なものを、誰かの宝物に。</p>
            </div>
            <div class="col-md-4">
                <h5 class="fw-bold mb-3">リンク</h5>
                <ul class="list-unstyled">
                    <li class="mb-2"><a href="${pageContext.request.contextPath}/about.jsp" class="text-secondary text-decoration-none"><i class="bi bi-info-circle"></i> について</a></li>
                    <li class="mb-2"><a href="${pageContext.request.contextPath}/contact.jsp" class="text-secondary text-decoration-none"><i class="bi bi-envelope"></i> お問い合わせ</a></li>
                    <li class="mb-2"><a href="${pageContext.request.contextPath}/terms.jsp" class="text-secondary text-decoration-none"><i class="bi bi-file-text"></i> 利用規約</a></li>
                    <li class="mb-2"><a href="${pageContext.request.contextPath}/privacy.jsp" class="text-secondary text-decoration-none"><i class="bi bi-shield-lock"></i> プライバシーポリシー</a></li>
                </ul>
            </div>
            <div class="col-md-4">
                <h5 class="fw-bold mb-3">カテゴリー</h5>
                <ul class="list-unstyled">
                    <li class="mb-2"><a href="${pageContext.request.contextPath}/products.jsp?category=electronics" class="text-secondary text-decoration-none"><i class="bi bi-laptop"></i> 電子機器</a></li>
                    <li class="mb-2"><a href="${pageContext.request.contextPath}/products.jsp?category=fashion" class="text-secondary text-decoration-none"><i class="bi bi-bag"></i> ファッション</a></li>
                    <li class="mb-2"><a href="${pageContext.request.contextPath}/products.jsp?category=books" class="text-secondary text-decoration-none"><i class="bi bi-book"></i> 書籍</a></li>
                    <li class="mb-2"><a href="${pageContext.request.contextPath}/products.jsp?category=home" class="text-secondary text-decoration-none"><i class="bi bi-house"></i> ホーム</a></li>
                </ul>
            </div>
        </div>
        <hr class="bg-secondary my-4">
        <div class="text-center text-secondary">
            <p class="mb-0">&copy; 2024 カルル. All rights reserved.</p>
        </div>
    </div>
</footer>

</body>
</html>
