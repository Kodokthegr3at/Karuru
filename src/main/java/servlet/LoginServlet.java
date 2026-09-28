package servlet;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
import javax.servlet.http.HttpSession;

import util.DatabaseConnection;
import util.Params;
import util.PasswordUtils;

/**
 * Form login from login.jsp. After {@value #MAX_ATTEMPTS} wrong passwords the account is locked for 30 minutes.
 * Unknown user and wrong password produce the same error so the form can't be used to probe for accounts.
 */
@WebServlet("/LoginServlet")
public class LoginServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(LoginServlet.class.getName());
    private static final int MAX_ATTEMPTS = 5;
    private static final int SESSION_SECONDS = (int) Duration.ofMinutes(30).toSeconds();
    private static final int REMEMBER_ME_SECONDS = (int) Duration.ofDays(7).toSeconds();

    private static final String FIND_SQL = "SELECT user_id, username, password_hash, role, is_verified, locked_until, "
            + "login_attempts FROM users WHERE (email = ? OR username = ?) AND deleted_at IS NULL";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.sendRedirect(request.getContextPath() + "/login.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String login = Params.opt(request, "emailOrUser");
        String password = request.getParameter("password");
        if (login == null || password == null || password.isEmpty()) {
            redirect(request, response, "/login.jsp?error=empty");
            return;
        }
        try (Connection conn = DatabaseConnection.getConnection()) {
            redirect(request, response, authenticate(conn, request, login, password));
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Login failed with a database error", e);
            redirect(request, response, "/login.jsp?error=database_error");
        }
    }

    /** Returns the path to redirect to. Starts the session on success. */
    private String authenticate(Connection conn, HttpServletRequest request, String login, String password)
            throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(FIND_SQL)) {
            stmt.setString(1, login);
            stmt.setString(2, login);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return "/login.jsp?error=invalid";
                }
                int userId = rs.getInt("user_id");
                String role = rs.getString("role");
                Timestamp lockedUntil = rs.getTimestamp("locked_until");
                int attempts = rs.getInt("login_attempts");

                if (lockedUntil != null && lockedUntil.toInstant().isAfter(Instant.now())) {
                    long minutes = Duration.between(Instant.now(), lockedUntil.toInstant()).toMinutes();
                    return "/login.jsp?error=account_locked&minutes=" + minutes;
                }
                if (lockedUntil != null) {
                    attempts = 0; // lock expired: start counting again
                }
                if (!PasswordUtils.checkPassword(password, rs.getString("password_hash"))) {
                    return recordFailure(conn, userId, attempts + 1);
                }
                if (!rs.getBoolean("is_verified") && !"admin".equals(role)) {
                    return "/login.jsp?error=not_verified";
                }
                recordSuccess(conn, userId);
                startSession(request, userId, rs.getString("username"), role);
                if ("admin".equals(role)) {
                    logAdminLogin(conn, userId, request.getRemoteAddr());
                }
                String requested = localPath(request.getParameter("redirect"));
                if (requested != null) {
                    return requested;
                }
                boolean staff = "admin".equals(role) || "moderator".equals(role);
                return staff ? "/admin/dashboard.jsp" : "/index.jsp";
            }
        }
    }

    private String recordFailure(Connection conn, int userId, int attempts) throws SQLException {
        boolean lock = attempts >= MAX_ATTEMPTS;
        String sql = lock
                ? "UPDATE users SET login_attempts = ?, locked_until = NOW() + INTERVAL 30 MINUTE WHERE user_id = ?"
                : "UPDATE users SET login_attempts = ?, locked_until = NULL WHERE user_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, attempts);
            stmt.setInt(2, userId);
            stmt.executeUpdate();
        }
        return lock ? "/login.jsp?error=account_locked" : "/login.jsp?error=invalid";
    }

    private void recordSuccess(Connection conn, int userId) throws SQLException {
        String sql = "UPDATE users SET login_attempts = 0, locked_until = NULL, last_login = NOW() WHERE user_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.executeUpdate();
        }
    }

    private void startSession(HttpServletRequest request, int userId, String username, String role) {
        HttpSession session = request.getSession();
        request.changeSessionId(); // prevent session fixation
        session.setAttribute("user_id", userId);
        session.setAttribute("username", username);
        session.setAttribute("role", role);
        session.setMaxInactiveInterval("on".equals(request.getParameter("remember")) ? REMEMBER_ME_SECONDS : SESSION_SECONDS);
    }

    private void logAdminLogin(Connection conn, int userId, String ip) {
        String sql = "INSERT INTO activity_logs (user_id, action, entity_type, details, ip_address) "
                + "VALUES (?, 'admin_login', 'user', 'Admin logged in', ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setString(2, ip);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Could not write admin login audit log", e); // never block the login itself
        }
    }

    /** Sends the browser to path; a failed login keeps the page the user wanted to reach after logging in. */
    private static void redirect(HttpServletRequest request, HttpServletResponse response, String path)
            throws IOException {
        String requested = localPath(request.getParameter("redirect"));
        if (path.startsWith("/login.jsp") && requested != null) {
            path += (path.contains("?") ? "&" : "?") + "redirect=" + URLEncoder.encode(requested, StandardCharsets.UTF_8);
        }
        response.sendRedirect(request.getContextPath() + path);
    }

    /** The redirect target if it is a path inside this app ("/orders.jsp"), never another site ("//evil", "http:"). */
    static String localPath(String value) {
        if (value == null || !value.startsWith("/") || value.startsWith("//") || value.contains("\\")
                || value.startsWith("/login.jsp")) {
            return null;
        }
        return value;
    }
}
