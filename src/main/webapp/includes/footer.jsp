<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<footer class="site-footer">
    <div class="container site-footer-inner">
        <div>
            <a class="brand" href="${pageContext.request.contextPath}/index.jsp">
                <img src="${pageContext.request.contextPath}/img/logo.png" alt="" width="24" height="24">
                <span>カルル</span>
            </a>
            <p class="site-footer-tagline">あなたの不要なものを、誰かの宝物に。</p>
        </div>
        <nav class="site-footer-links" aria-label="フッター">
            <a href="${pageContext.request.contextPath}/about.jsp">カルルについて</a>
            <a href="${pageContext.request.contextPath}/help.jsp">ヘルプ</a>
            <a href="${pageContext.request.contextPath}/faq.jsp">よくある質問</a>
            <a href="${pageContext.request.contextPath}/contact.jsp">お問い合わせ</a>
            <a href="${pageContext.request.contextPath}/terms.jsp">利用規約</a>
            <a href="${pageContext.request.contextPath}/privacy.jsp">プライバシーポリシー</a>
        </nav>
    </div>
    <div class="container site-footer-copy">&copy; <%= java.time.Year.now() %> カルル</div>
</footer>
</body>
</html>
