package servlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class LoginServletTest {

    @Test
    void acceptsPathsInsideTheApp() {
        assertEquals("/orders.jsp", LoginServlet.localPath("/orders.jsp"));
        assertEquals("/product-detail.jsp?id=3", LoginServlet.localPath("/product-detail.jsp?id=3"));
    }

    @Test
    void rejectsOtherSitesAndLoops() {
        assertNull(LoginServlet.localPath(null));
        assertNull(LoginServlet.localPath("https://evil.example/"));
        assertNull(LoginServlet.localPath("//evil.example/"));
        assertNull(LoginServlet.localPath("/\\evil.example"));
        assertNull(LoginServlet.localPath("orders.jsp"));
        assertNull(LoginServlet.localPath("/login.jsp?redirect=/x"));
    }
}
