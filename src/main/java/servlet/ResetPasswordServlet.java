package servlet;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import util.DatabaseConnection;
import util.PasswordUtils;

/**
 * GET: target of the reset link in the email; validates the code and shows the form.
 * POST: sets the new password from reset-password.jsp.
 */
@WebServlet("/ResetPasswordServlet")
public class ResetPasswordServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(ResetPasswordServlet.class.getName());
    private static final int MIN_PASSWORD_LENGTH = 6;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String code = request.getParameter("code");
        if (code == null || code.isBlank()) {
            redirect(request, response, "/login.jsp?error=invalid_reset_code");
            return;
        }
        String sql = "SELECT 1 FROM users WHERE reset_token = ? AND reset_token_expiry > NOW() AND deleted_at IS NULL";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, code);
            try (ResultSet rs = stmt.executeQuery()) {
                redirect(request, response, rs.next()
                        ? "/reset-password.jsp?code=" + encode(code)
                        : "/login.jsp?error=reset_token_expired");
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Reset code lookup failed", e);
            redirect(request, response, "/login.jsp?error=database_error");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String code = request.getParameter("code");
        String password = request.getParameter("password");
        String confirmation = request.getParameter("confirmPassword");

        if (code == null || code.isBlank()) {
            redirect(request, response, "/login.jsp?error=invalid_reset_code");
            return;
        }
        String formError = null;
        if (password == null || password.isEmpty()) {
            formError = "empty_password";
        } else if (password.length() < MIN_PASSWORD_LENGTH) {
            formError = "password_short";
        } else if (confirmation != null && !password.equals(confirmation)) {
            formError = "password_mismatch";
        }
        if (formError != null) {
            redirect(request, response, "/reset-password.jsp?code=" + encode(code) + "&error=" + formError);
            return;
        }

        // The expiry check is part of the UPDATE so an expired code can never be used.
        String sql = "UPDATE users SET password_hash = ?, reset_token = NULL, reset_token_expiry = NULL, updated_at = NOW() "
                + "WHERE reset_token = ? AND reset_token_expiry > NOW() AND deleted_at IS NULL";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, PasswordUtils.hashPassword(password));
            stmt.setString(2, code);
            boolean updated = stmt.executeUpdate() > 0;
            redirect(request, response, updated ? "/login.jsp?success=password_reset" : "/login.jsp?error=reset_token_expired");
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Password reset failed", e);
            redirect(request, response, "/reset-password.jsp?code=" + encode(code) + "&error=database_error");
        }
    }

    private static void redirect(HttpServletRequest request, HttpServletResponse response, String path) throws IOException {
        response.sendRedirect(request.getContextPath() + path);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
