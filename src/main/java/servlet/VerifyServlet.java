package servlet;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import util.DatabaseConnection;

/**
 * Servlet untuk verify user account
 * Endpoint: /VerifyServlet
 */
@WebServlet("/VerifyServlet")
public class VerifyServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    public VerifyServlet() {
        super();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        String code = request.getParameter("code");
        String contextPath = request.getContextPath();
        
        if (code == null || code.trim().isEmpty()) {
            response.sendRedirect(contextPath + "/login.jsp?error=invalid_verification_code");
            return;
        }

        Connection conn = null;
        PreparedStatement checkStmt = null;
        PreparedStatement updateStmt = null;
        ResultSet rs = null;

        try {
            conn = DatabaseConnection.getConnection();
            
            // Check if token exists - sesuai struktur tabel users
            String checkSql = "SELECT user_id, username, email, is_verified, created_at " +
                             "FROM users WHERE verification_token=? AND deleted_at IS NULL";
            checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setString(1, code);
            rs = checkStmt.executeQuery();

            if (rs.next()) {
                boolean alreadyVerified = rs.getBoolean("is_verified");
                
                if (alreadyVerified) {
                    response.sendRedirect(contextPath + "/login.jsp?info=already_verified");
                    return;
                }

                // Check if token is expired (24 hours)
                java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
                long hoursSinceCreation = (System.currentTimeMillis() - createdAt.getTime()) / (1000 * 60 * 60);
                
                if (hoursSinceCreation > 24) {
                    response.sendRedirect(contextPath + "/login.jsp?error=verification_expired");
                    return;
                }

                // Update user verification status - sesuai field di tabel users
                String updateSql = "UPDATE users SET is_verified=1, verification_token=NULL, " +
                                  "verified_at=NOW(), updated_at=NOW() WHERE verification_token=?";
                updateStmt = conn.prepareStatement(updateSql);
                updateStmt.setString(1, code);
                
                int updated = updateStmt.executeUpdate();

                if (updated > 0) {
                    String username = rs.getString("username");
                    System.out.println("User verified successfully: " + username);
                    response.sendRedirect(contextPath + "/login.jsp?success=verified");
                } else {
                    response.sendRedirect(contextPath + "/login.jsp?error=verification_failed");
                }
            } else {
                response.sendRedirect(contextPath + "/login.jsp?error=invalid_verification_code");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            response.sendRedirect(contextPath + "/login.jsp?error=database_error");
        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect(contextPath + "/login.jsp?error=server_error");
        } finally {
            DatabaseConnection.closeResources(rs, checkStmt, updateStmt, conn);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        doGet(request, response);
    }
}