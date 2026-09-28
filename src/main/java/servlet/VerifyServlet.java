package servlet;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import util.DatabaseConnection;

/** Email verification link target (/VerifyServlet?code=...). Always redirects to the login page. */
@WebServlet("/VerifyServlet")
public class VerifyServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(VerifyServlet.class.getName());
    private static final Duration LINK_VALIDITY = Duration.ofHours(24);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String code = request.getParameter("code");
        String outcome = (code == null || code.isBlank()) ? "error=invalid_verification_code" : verify(code);
        response.sendRedirect(request.getContextPath() + "/login.jsp?" + outcome);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        doGet(request, response);
    }

    /** Returns the login.jsp query string describing the result. */
    private String verify(String code) {
        String findSql = "SELECT is_verified, created_at FROM users WHERE verification_token = ? AND deleted_at IS NULL";
        String verifySql = "UPDATE users SET is_verified = 1, verification_token = NULL, verified_at = NOW(), "
                + "updated_at = NOW() WHERE verification_token = ?";
        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement find = conn.prepareStatement(findSql)) {
                find.setString(1, code);
                try (ResultSet rs = find.executeQuery()) {
                    if (!rs.next()) {
                        return "error=invalid_verification_code";
                    }
                    if (rs.getBoolean("is_verified")) {
                        return "info=already_verified";
                    }
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    if (Duration.between(createdAt.toInstant(), Instant.now()).compareTo(LINK_VALIDITY) > 0) {
                        return "error=verification_expired";
                    }
                }
            }
            try (PreparedStatement update = conn.prepareStatement(verifySql)) {
                update.setString(1, code);
                return update.executeUpdate() > 0 ? "success=verified" : "error=verification_failed";
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Email verification failed", e);
            return "error=database_error";
        }
    }
}
