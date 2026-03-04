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
import util.PasswordUtils;

/**
 * Servlet untuk handle password reset
 * Endpoint: /ResetPasswordServlet
 */
@WebServlet("/ResetPasswordServlet")
public class ResetPasswordServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    public ResetPasswordServlet() {
        super();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        String code = request.getParameter("code");
        String contextPath = request.getContextPath();
        
        if (code == null || code.trim().isEmpty()) {
            response.sendRedirect(contextPath + "/login.jsp?error=invalid_reset_code");
            return;
        }

        Connection conn = null;
        PreparedStatement checkStmt = null;
        ResultSet rs = null;

        try {
            conn = DatabaseConnection.getConnection();
            
            // Check if token exists and is valid - sesuai struktur tabel users
            String checkSql = "SELECT user_id, username FROM users " +
                             "WHERE reset_token=? AND reset_token_expiry > NOW() " +
                             "AND deleted_at IS NULL";
            checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setString(1, code);
            rs = checkStmt.executeQuery();

            if (rs.next()) {
                // Token is valid, redirect to reset password form
                response.sendRedirect(contextPath + "/reset-password.jsp?code=" + code);
            } else {
                response.sendRedirect(contextPath + "/login.jsp?error=reset_token_expired");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            response.sendRedirect(contextPath + "/login.jsp?error=database_error");
        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect(contextPath + "/login.jsp?error=server_error");
        } finally {
            DatabaseConnection.closeResources(rs, checkStmt, conn);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        // Set encoding
        request.setCharacterEncoding("UTF-8");
        String contextPath = request.getContextPath();
        
        String code = request.getParameter("code");
        String newPassword = request.getParameter("password");
        String confirmPassword = request.getParameter("confirmPassword");

        // Validation
        if (code == null || code.trim().isEmpty()) {
            response.sendRedirect(contextPath + "/login.jsp?error=invalid_reset_code");
            return;
        }

        if (newPassword == null || newPassword.isEmpty()) {
            response.sendRedirect(contextPath + "/reset-password.jsp?code=" + code + "&error=empty_password");
            return;
        }

        if (newPassword.length() < 6) {
            response.sendRedirect(contextPath + "/reset-password.jsp?code=" + code + "&error=password_short");
            return;
        }

        // Validate password confirmation
        if (confirmPassword != null && !newPassword.equals(confirmPassword)) {
            response.sendRedirect(contextPath + "/reset-password.jsp?code=" + code + "&error=password_mismatch");
            return;
        }

        String hashedPassword = PasswordUtils.hashPassword(newPassword);

        Connection conn = null;
        PreparedStatement checkStmt = null;
        PreparedStatement updateStmt = null;
        ResultSet rs = null;

        try {
            conn = DatabaseConnection.getConnection();
            
            // Verify token is still valid - sesuai struktur tabel users
            String checkSql = "SELECT user_id, username, email FROM users " +
                             "WHERE reset_token=? AND reset_token_expiry > NOW() " +
                             "AND deleted_at IS NULL";
            checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setString(1, code);
            rs = checkStmt.executeQuery();

            if (!rs.next()) {
                response.sendRedirect(contextPath + "/login.jsp?error=reset_token_expired");
                return;
            }

            String username = rs.getString("username");
            String email = rs.getString("email");

            // Update password and clear reset token - sesuai field di tabel users
            String updateSql = "UPDATE users SET password_hash=?, reset_token=NULL, " +
                              "reset_token_expiry=NULL, updated_at=NOW() " +
                              "WHERE reset_token=?";
            updateStmt = conn.prepareStatement(updateSql);
            updateStmt.setString(1, hashedPassword);
            updateStmt.setString(2, code);
            
            int updated = updateStmt.executeUpdate();

            if (updated > 0) {
                System.out.println("Password reset successfully for user: " + username + " (" + email + ")");
                response.sendRedirect(contextPath + "/login.jsp?success=password_reset");
            } else {
                response.sendRedirect(contextPath + "/login.jsp?error=reset_failed");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            response.sendRedirect(contextPath + "/reset-password.jsp?code=" + code + "&error=database_error");
        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect(contextPath + "/reset-password.jsp?code=" + code + "&error=server_error");
        } finally {
            DatabaseConnection.closeResources(rs, checkStmt, updateStmt, conn);
        }
    }
}