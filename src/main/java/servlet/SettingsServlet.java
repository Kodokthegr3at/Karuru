package servlet;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Pattern;

import javax.servlet.annotation.WebServlet;

import util.Json;
import util.PasswordUtils;

/** Account settings page (settings.jsp): email and password changes, both confirmed with the current password. */
@WebServlet("/SettingsServlet")
public class SettingsServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public SettingsServlet() {
        route("GET", null, Access.USER, this::show);
        route("GET", "getSettings", Access.USER, this::show);
        route("POST", "updateEmail", Access.USER, this::updateEmail);
        route("POST", "changePassword", Access.USER, this::changePassword);
    }

    private void show(Call call) throws IOException, SQLException {
        String sql = "SELECT username, email, full_name, phone, bio, avatar_url, role, is_verified FROM users WHERE user_id = ?";
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            stmt.setInt(1, call.userId());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    call.error(SC_NOT_FOUND, "ユーザーが見つかりません");
                    return;
                }
                call.ok(Json.obj("success", true, "settings", Json.obj(
                        "username", rs.getString("username"),
                        "email", rs.getString("email"),
                        "full_name", rs.getString("full_name"),
                        "phone", rs.getString("phone"),
                        "bio", rs.getString("bio"),
                        "avatar_url", rs.getString("avatar_url"),
                        "role", rs.getString("role"),
                        "is_verified", rs.getBoolean("is_verified"))));
            }
        }
    }

    private void updateEmail(Call call) throws IOException, SQLException {
        String email = call.param("new_email");
        if (email == null) {
            call.error(SC_BAD_REQUEST, "新しいメールアドレスが必要です");
            return;
        }
        if (!EMAIL.matcher(email).matches()) {
            call.error(SC_BAD_REQUEST, "有効なメールアドレスを入力してください");
            return;
        }
        email = email.toLowerCase();
        if (!passwordMatches(call.db(), call.userId(), call.request.getParameter("password"))) {
            call.error(SC_FORBIDDEN, "パスワードが正しくありません");
            return;
        }
        if (emailTaken(call.db(), email, call.userId())) {
            call.error(SC_CONFLICT, "このメールアドレスは既に使用されています");
            return;
        }
        try (PreparedStatement stmt = call.db().prepareStatement(
                "UPDATE users SET email = ?, updated_at = NOW() WHERE user_id = ?")) {
            stmt.setString(1, email);
            stmt.setInt(2, call.userId());
            stmt.executeUpdate();
        }
        call.ok(Json.obj("success", true, "message", "メールアドレスを更新しました"));
    }

    private void changePassword(Call call) throws IOException, SQLException {
        String current = call.request.getParameter("current_password");
        String next = call.request.getParameter("new_password");
        if (current == null || current.isBlank() || next == null || next.isBlank()) {
            call.error(SC_BAD_REQUEST, "現在のパスワードと新しいパスワードが必要です");
            return;
        }
        if (!PasswordUtils.isValidPassword(next)) {
            call.error(SC_BAD_REQUEST, "新しいパスワードは6文字以上128文字以下である必要があります");
            return;
        }
        if (!passwordMatches(call.db(), call.userId(), current)) {
            call.error(SC_FORBIDDEN, "現在のパスワードが正しくありません");
            return;
        }
        try (PreparedStatement stmt = call.db().prepareStatement(
                "UPDATE users SET password_hash = ?, updated_at = NOW() WHERE user_id = ?")) {
            stmt.setString(1, PasswordUtils.hashPassword(next));
            stmt.setInt(2, call.userId());
            stmt.executeUpdate();
        }
        call.ok(Json.obj("success", true, "message", "パスワードを変更しました"));
    }

    private static boolean passwordMatches(Connection db, int userId, String password) throws SQLException {
        if (password == null || password.isEmpty()) {
            return false;
        }
        try (PreparedStatement stmt = db.prepareStatement("SELECT password_hash FROM users WHERE user_id = ?")) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && PasswordUtils.checkPassword(password, rs.getString(1));
            }
        }
    }

    private static boolean emailTaken(Connection db, String email, int exceptUserId) throws SQLException {
        try (PreparedStatement stmt = db.prepareStatement("SELECT 1 FROM users WHERE email = ? AND user_id <> ?")) {
            stmt.setString(1, email);
            stmt.setInt(2, exceptUserId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }
}
