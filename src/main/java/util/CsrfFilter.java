package util;

import java.io.IOException;
import java.net.URI;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * CSRF protection: rejects state-changing requests (POST/PUT/DELETE/PATCH)
 * whose Origin (or Referer, as fallback) is not this site.
 * Works together with the SameSite=Lax session cookie set in META-INF/context.xml.
 *
 * Note: Filter ini dikonfigurasi di web.xml, bukan menggunakan @WebFilter annotation.
 */
public class CsrfFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String method = req.getMethod();
        boolean unsafe = !("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method));

        if (unsafe && !isSameOrigin(req.getHeader("Origin"), req.getHeader("Referer"), req.getHeader("Host"))) {
            ((HttpServletResponse) response).sendError(HttpServletResponse.SC_FORBIDDEN, "Cross-site request blocked");
            return;
        }
        chain.doFilter(request, response);
    }

    /** True if Origin (or Referer when Origin is absent) points at the same host:port as Host. */
    public static boolean isSameOrigin(String origin, String referer, String host) {
        String source = origin != null ? origin : referer;
        if (source == null || host == null) {
            return false;
        }
        try {
            return host.equalsIgnoreCase(URI.create(source).getRawAuthority());
        } catch (IllegalArgumentException e) {
            return false; // includes Origin: null
        }
    }
}
