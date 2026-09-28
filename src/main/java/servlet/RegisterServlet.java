package servlet;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
import util.Json;
import util.Mailer;
import util.Params;
import util.PasswordUtils;

/** Account sign-up from register.jsp: creates the user and an empty wallet, then emails a verification link. */
@WebServlet("/RegisterServlet")
public class RegisterServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(RegisterServlet.class.getName());
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9_]{3,50}$");
    private static final int MIN_PASSWORD_LENGTH = 6;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.sendRedirect("register.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String email = Params.opt(request, "email");
        String username = Params.opt(request, "username");
        String password = request.getParameter("password");
        String confirmation = request.getParameter("confirmPassword");

        String invalid = validate(email, username, password, confirmation);
        if (invalid != null) {
            Json.error(response, SC_BAD_REQUEST, invalid);
            return;
        }
        email = email.toLowerCase();
        String token = UUID.randomUUID().toString();

        try (Connection conn = DatabaseConnection.getConnection()) {
            String taken = findTaken(conn, email, username);
            if (taken != null) {
                Json.error(response, SC_CONFLICT, taken);
                return;
            }
            conn.setAutoCommit(false);
            int userId = insertUser(conn, email, username, PasswordUtils.hashPassword(password),
                    Params.opt(request, "fullName"), Params.opt(request, "phone"), token);
            insertWallet(conn, userId);
            conn.commit();
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Registration failed for " + username, e);
            Json.error(response, SC_INTERNAL_SERVER_ERROR, "データベースエラーが発生しました");
            return;
        }

        boolean emailSent = Mailer.send(email, "カルル - アカウント確認 / Karuru Account Verification",
                verificationMail(username, token));
        Json.ok(response, Json.obj(
                "success", true,
                "message", emailSent
                        ? "登録が完了しました。確認メールを送信しました。"
                        : "登録は完了しましたが、確認メールを送信できませんでした。お手数ですがお問い合わせください。",
                "emailSent", emailSent));
    }

    /** Returns an error message for the user, or null when the input is valid. */
    private static String validate(String email, String username, String password, String confirmation) {
        if (email == null || username == null || password == null || password.isEmpty()) {
            return "すべての必須項目を入力してください";
        }
        if (!EMAIL.matcher(email).matches()) {
            return "有効なメールアドレスを入力してください";
        }
        if (!USERNAME.matcher(username).matches()) {
            return "ユーザー名は3-50文字の英数字とアンダースコアのみ使用可能です";
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return "パスワードは6文字以上必要です";
        }
        if (confirmation != null && !password.equals(confirmation)) {
            return "パスワードが一致しません";
        }
        return null;
    }

    /** Returns a message if the email or username is already registered, otherwise null. */
    private static String findTaken(Connection conn, String email, String username) throws SQLException {
        String sql = "SELECT email FROM users WHERE email = ? OR username = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            stmt.setString(2, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return email.equals(rs.getString("email"))
                        ? "このメールアドレスは既に登録されています"
                        : "このユーザー名は既に使用されています";
            }
        }
    }

    private static int insertUser(Connection conn, String email, String username, String passwordHash,
            String fullName, String phone, String verificationToken) throws SQLException {
        String sql = "INSERT INTO users (username, email, password_hash, full_name, phone, verification_token) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, username);
            stmt.setString(2, email);
            stmt.setString(3, passwordHash);
            stmt.setString(4, fullName);
            stmt.setString(5, phone);
            stmt.setString(6, verificationToken);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    private static void insertWallet(Connection conn, int userId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO user_wallets (user_id) VALUES (?)")) {
            stmt.setInt(1, userId);
            stmt.executeUpdate();
        }
    }

    private static String verificationMail(String username, String token) {
        String link = EmailConfig.BASE_URL + "/VerifyServlet?code=" + token;
        return "カルルへようこそ！ / Welcome to Karuru!\n\n"
                + username + " 様\n\n"
                + "ご登録ありがとうございます。以下のリンクをクリックしてアカウントを確認してください：\n"
                + "Thank you for registering. Please click the link below to verify your account:\n\n"
                + link + "\n\n"
                + "このリンクは24時間有効です。\n"
                + "This link is valid for 24 hours.\n\n"
                + "カルルチーム / Karuru Team";
    }
}
