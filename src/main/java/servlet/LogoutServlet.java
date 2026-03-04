package servlet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Servlet untuk handle user logout
 * Endpoint: /Logout atau /LogoutServlet (untuk kompatibilitas)
 */
@WebServlet({"/Logout", "/LogoutServlet"})
public class LogoutServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    public LogoutServlet() {
        super();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        performLogout(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        performLogout(request, response);
    }
    
    /**
     * Perform logout operation
     */
    private void performLogout(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        HttpSession session = request.getSession(false);
        
        if (session != null) {
            // Get username before invalidating session (optional for logging)
            String username = (String) session.getAttribute("username");
            
            // Invalidate session
            session.invalidate();
            
            // Optional: Log logout event
            if (username != null) {
                System.out.println("User logged out: " + username);
            }
        }
        
        // Redirect to home page with logout success message
        response.sendRedirect("index.jsp?logout=success");
    }
}