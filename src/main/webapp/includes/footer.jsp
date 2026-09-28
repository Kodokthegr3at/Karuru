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
            <%@ include file="site-links.jspf" %>
        </nav>
    </div>
    <div class="container site-footer-copy">&copy; <%= java.time.Year.now() %> カルル</div>
</footer>
</body>
</html>
