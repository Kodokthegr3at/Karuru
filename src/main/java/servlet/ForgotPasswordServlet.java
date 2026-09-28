package servlet;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import util.DatabaseConnection;
import util.EmailConfig;
import util.Mailer;

/** Handles forgot-password.jsp: stores a one-hour reset token and emails the reset link. */
@WebServlet({"/ForgotPasswordServlet", "/ForgotPassword"})
public class ForgotPasswordServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(ForgotPasswordServlet.class.getName());
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Duration TOKEN_VALIDITY = Duration.ofHours(1);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.sendRedirect(request.getContextPath() + "/forgot-password.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String email = request.getParameter("email");
        String outcome;
        if (email == null || email.isBlank()) {
            outcome = "error=empty_email";
        } else if (!EMAIL.matcher(email.trim()).matches()) {
            outcome = "error=invalid_email";
        } else {
            outcome = requestReset(email.trim().toLowerCase());
        }
        response.sendRedirect(request.getContextPath() + "/forgot-password.jsp?" + outcome);
    }

    /** Returns the forgot-password.jsp query string describing the result. */
    private String requestReset(String email) {
        String findSql = "SELECT username FROM users WHERE email = ? AND deleted_at IS NULL";
        String tokenSql = "UPDATE users SET reset_token = ?, reset_token_expiry = ?, updated_at = NOW() WHERE email = ?";
        String token = UUID.randomUUID().toString();
        String username;
        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement find = conn.prepareStatement(findSql)) {
                find.setString(1, email);
                try (ResultSet rs = find.executeQuery()) {
                    if (!rs.next()) {
                        return "success=true"; // same answer as success: don't reveal which emails exist
                    }
                    username = rs.getString("username");
                }
            }
            try (PreparedStatement update = conn.prepareStatement(tokenSql)) {
                update.setString(1, token);
                update.setTimestamp(2, Timestamp.from(Instant.now().plus(TOKEN_VALIDITY)));
                update.setString(3, email);
                if (update.executeUpdate() == 0) {
                    return "error=update_failed";
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Could not store reset token", e);
            return "error=database_error";
        }
        return Mailer.send(email, "カルル - パスワードリセット / Karuru Password Reset", resetMail(username, token))
                ? "success=true"
                : "success=true&warning=email_failed";
    }

    private static String resetMail(String username, String token) {
        String link = EmailConfig.BASE_URL + "/ResetPasswordServlet?code=" + token;
        return username + " 様\n\n"
                + "カルルからのお知らせ\n\n"
                + "以下のリンクをクリックしてパスワードをリセットしてください：\n" + link + "\n\n"
                + "このリンクは1時間有効です。\n\n"
                + "---\n\n"
                + "Password Reset Request\n\n"
                + "Click the link below to reset your password:\n" + link + "\n\n"
                + "This link is valid for 1 hour.\n\n"
                + "カルルチーム / Karuru Team";
    }
}
