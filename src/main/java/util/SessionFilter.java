package util;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Filter untuk protect pages yang memerlukan authentication
 * 
 * Note: Filter ini dikonfigurasi di web.xml, bukan menggunakan @WebFilter annotation
 * untuk menghindari duplikasi konfigurasi.
 */
public class SessionFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Initialization
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        HttpSession session = httpRequest.getSession(false);
        
        String requestURI = httpRequest.getRequestURI();
        String contextPath = httpRequest.getContextPath();
        String path = requestURI.substring(contextPath.length());
        
        // Check if user is logged in
        if (session == null || session.getAttribute("user_id") == null) {
            // Save the original URL for redirect after login
            String queryString = httpRequest.getQueryString();
            String redirectURL = path;
            if (queryString != null) {
                redirectURL += "?" + queryString;
            }
            
            httpResponse.sendRedirect(contextPath + "/login.jsp?redirect=" + 
                java.net.URLEncoder.encode(redirectURL, "UTF-8"));
            return;
        }
        
        // Check admin pages
        if (path.startsWith("/admin/")) {
            String role = (String) session.getAttribute("role");
            if (role == null || !role.equals("admin")) {
                httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "管理者権限が必要です");
                return;
            }
        }
        
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // Cleanup
    }
}

