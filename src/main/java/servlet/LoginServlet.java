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
import javax.servlet.http.HttpSession;

import util.DatabaseConnection;
import util.PasswordUtils;

/**
 * Servlet untuk handle user login
 * Endpoint: /LoginServlet
 */
@WebServlet("/LoginServlet")
public class LoginServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    public LoginServlet() {
        super();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        // Redirect to login page
        String contextPath = request.getContextPath();
        response.sendRedirect(contextPath + "/login.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        String contextPath = request.getContextPath();
        try {
            doPostInternal(request, response);
        } catch (Throwable t) {
            t.printStackTrace();
            if (!response.isCommitted()) {
                response.sendRedirect(contextPath + "/login.jsp?error=server_error");
            }
        }
    }
    
    private void doPostInternal(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        // Encoding is set by FilterEncodingUTF8 (web.xml); avoid duplicate setCharacterEncoding
        String emailOrUser = request.getParameter("emailOrUser");
        String password = request.getParameter("password");
        String remember = request.getParameter("remember");

        // Validation
        if (emailOrUser == null || emailOrUser.trim().isEmpty() || 
            password == null || password.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/login.jsp?error=empty");
            return;
        }

        Connection conn = null;
        PreparedStatement ps = null;
        PreparedStatement updatePs = null;
        PreparedStatement resetLockPs = null;
        ResultSet rs = null;

        try {
            conn = DatabaseConnection.getConnection();
            
            // Query sesuai dengan struktur tabel users di database
            String sql = "SELECT user_id, username, email, password_hash, full_name, role, " +
                        "is_verified, is_seller, locked_until, login_attempts " +
                        "FROM users WHERE (email=? OR username=?) AND deleted_at IS NULL";
            
            ps = conn.prepareStatement(sql);
            ps.setString(1, emailOrUser.trim());
            ps.setString(2, emailOrUser.trim());
            
            rs = ps.executeQuery();
            
            if (rs.next()) {
                int userId = rs.getInt("user_id");
                String username = rs.getString("username");
                String email = rs.getString("email");
                String fullName = rs.getString("full_name");
                String role = rs.getString("role");
                String hashed = rs.getString("password_hash");
                boolean verified = rs.getBoolean("is_verified");
                boolean isSeller = rs.getBoolean("is_seller");
                java.sql.Timestamp lockedUntil = rs.getTimestamp("locked_until");
                int loginAttempts = rs.getInt("login_attempts");

                // Check if account is locked
                java.sql.Timestamp currentTime = new java.sql.Timestamp(System.currentTimeMillis());
                if (lockedUntil != null && lockedUntil.after(currentTime)) {
                    // Account is still locked
                    long minutesLeft = (lockedUntil.getTime() - currentTime.getTime()) / (60 * 1000);
                    response.sendRedirect(request.getContextPath() + "/login.jsp?error=account_locked&minutes=" + minutesLeft);
                    return;
                } else if (lockedUntil != null && lockedUntil.before(currentTime)) {
                    // Lock has expired, reset login attempts
                    String resetExpiredLockSql = "UPDATE users SET login_attempts=0, locked_until=NULL WHERE user_id=?";
                    if (resetLockPs != null) {
                        try {
                            resetLockPs.close();
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                    }
                    resetLockPs = conn.prepareStatement(resetExpiredLockSql);
                    resetLockPs.setInt(1, userId);
                    resetLockPs.executeUpdate();
                    loginAttempts = 0; // Reset for this session
                }

                // Check if account is verified (kecuali admin)
                if (!verified && !"admin".equals(role)) {
                    response.sendRedirect(request.getContextPath() + "/login.jsp?error=not_verified");
                    return;
                }

                // Verify password
                if (PasswordUtils.checkPassword(password, hashed)) {
                    // Reset login attempts on successful login
                    String resetSql = "UPDATE users SET login_attempts=0, locked_until=NULL, " +
                                     "last_login=NOW() WHERE user_id=?";
                    if (updatePs != null) {
                        try {
                            updatePs.close();
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                    }
                    updatePs = conn.prepareStatement(resetSql);
                    updatePs.setInt(1, userId);
                    updatePs.executeUpdate();

                    // Create session
                    HttpSession session = request.getSession();
                    session.setAttribute("user_id", userId);
                    session.setAttribute("userId", userId); // Also set userId for consistency
                    session.setAttribute("username", username);
                    session.setAttribute("email", email);
                    session.setAttribute("full_name", fullName);
                    session.setAttribute("role", role);
                    session.setAttribute("is_seller", isSeller);
                    
                    // Set session timeout based on remember me
                    if ("on".equals(remember)) {
                        session.setMaxInactiveInterval(7 * 24 * 60 * 60); // 7 days
                    } else {
                        session.setMaxInactiveInterval(30 * 60); // 30 minutes
                    }

                    // Log activity untuk admin
                    if ("admin".equals(role)) {
                        logAdminLogin(conn, userId, request.getRemoteAddr());
                    }

                    // Redirect berdasarkan role
                    if ("admin".equals(role) || "moderator".equals(role)) {
                        response.sendRedirect(request.getContextPath() + "/admin/dashboard.jsp?login=success");
                    } else {
                        response.sendRedirect(request.getContextPath() + "/index.jsp?login=success");
                    }
                } else {
                    // Increment login attempts
                    loginAttempts++;
                    String updateAttemptsSql;
                    
                    if (loginAttempts >= 5) {
                        // Lock account for 30 minutes after 5 failed attempts
                        updateAttemptsSql = "UPDATE users SET login_attempts=?, " +
                                          "locked_until=DATE_ADD(NOW(), INTERVAL 30 MINUTE) " +
                                          "WHERE user_id=?";
                    } else {
                        updateAttemptsSql = "UPDATE users SET login_attempts=? WHERE user_id=?";
                    }
                    
                    // Close previous updatePs if exists
                    if (updatePs != null) {
                        try {
                            updatePs.close();
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                    }
                    updatePs = conn.prepareStatement(updateAttemptsSql);
                    updatePs.setInt(1, loginAttempts);
                    updatePs.setInt(2, userId);
                    updatePs.executeUpdate();
                    
                    if (loginAttempts >= 5) {
                        response.sendRedirect(request.getContextPath() + "/login.jsp?error=account_locked");
                    } else {
                        response.sendRedirect(request.getContextPath() + "/login.jsp?error=invalid_password&attempts=" + loginAttempts);
                    }
                }
            } else {
                response.sendRedirect(request.getContextPath() + "/login.jsp?error=not_found");
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
            safeRedirect(response, request.getContextPath() + "/login.jsp?error=database_error");
        } catch (Exception e) {
            e.printStackTrace();
            safeRedirect(response, request.getContextPath() + "/login.jsp?error=server_error");
        } finally {
            // Close all resources
            if (rs != null) {
                try {
                    rs.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            if (ps != null) {
                try {
                    ps.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            if (resetLockPs != null) {
                try {
                    resetLockPs.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            if (updatePs != null) {
                try {
                    updatePs.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    /** Redirect safely; if response already committed, try forwarding error to avoid 500. */
    private void safeRedirect(HttpServletResponse response, String url) {
        try {
            if (!response.isCommitted()) {
                response.sendRedirect(url);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Log admin login activity untuk audit trail
     */
    private void logAdminLogin(Connection conn, int userId, String ipAddress) {
        PreparedStatement logPs = null;
        try {
            String logSql = "INSERT INTO activity_logs (user_id, action, entity_type, details, ip_address) " +
                           "VALUES (?, 'admin_login', 'user', 'Admin logged in', ?)";
            logPs = conn.prepareStatement(logSql);
            logPs.setInt(1, userId);
            logPs.setString(2, ipAddress);
            logPs.executeUpdate();
        } catch (SQLException e) {
            // Log error tapi jangan ganggu login process
            e.printStackTrace();
        } finally {
            if (logPs != null) {
                try {
                    logPs.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}