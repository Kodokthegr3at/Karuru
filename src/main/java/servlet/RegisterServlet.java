package servlet;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.UUID;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import util.DatabaseConnection;
import util.EmailConfig;
import util.FilterEncodingUTF8;
import util.PasswordUtils;

/**
 * Servlet untuk handle user registration
 * Endpoint: /RegisterServlet
 */
@WebServlet("/RegisterServlet")
public class RegisterServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Gson gson = new GsonBuilder()
        .disableHtmlEscaping()
        .create();

    public RegisterServlet() {
        super();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        response.sendRedirect("register.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        // Set encoding BEFORE reading parameters
        try {
            request.setCharacterEncoding("UTF-8");
        } catch (Exception e) {
            // Encoding already set or cannot be changed
            System.err.println("Warning: Could not set request encoding: " + e.getMessage());
        }
        
        // Set response encoding and content type for JSON
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=UTF-8");
        
        // Get parameters
        String email = request.getParameter("email");
        String username = request.getParameter("username");
        String password = request.getParameter("password");
        String confirmPassword = request.getParameter("confirmPassword");
        String fullName = request.getParameter("fullName");
        String phone = request.getParameter("phone");
        
        // Debug logging
        System.out.println("RegisterServlet - Received parameters:");
        System.out.println("  email: " + (email != null ? email : "null"));
        System.out.println("  username: " + (username != null ? username : "null"));
        System.out.println("  password: " + (password != null ? "***" : "null"));
        System.out.println("  fullName: " + (fullName != null ? fullName : "null"));
        System.out.println("  phone: " + (phone != null ? phone : "null"));

        // Validation
        if (email == null || email.trim().isEmpty() || 
            username == null || username.trim().isEmpty() || 
            password == null || password.isEmpty()) {
            System.out.println("RegisterServlet - Validation failed: Missing required fields");
            sendJsonError(response, "すべての必須項目を入力してください");
            return;
        }

        // Trim dan lowercase email
        email = email.trim().toLowerCase();
        username = username.trim();

        // Validate email format
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            sendJsonError(response, "有効なメールアドレスを入力してください");
            return;
        }

        // Validate username (3-50 characters, alphanumeric and underscore)
        if (!username.matches("^[a-zA-Z0-9_]{3,50}$")) {
            sendJsonError(response, "ユーザー名は3-50文字の英数字とアンダースコアのみ使用可能です");
            return;
        }

        // Validate password length
        if (password.length() < 6) {
            sendJsonError(response, "パスワードは6文字以上必要です");
            return;
        }

        // Validate password confirmation
        if (confirmPassword != null && !password.equals(confirmPassword)) {
            sendJsonError(response, "パスワードが一致しません");
            return;
        }

        // Hash password
        String hashedPassword = PasswordUtils.hashPassword(password);
        String verificationToken = UUID.randomUUID().toString();

        Connection conn = null;
        PreparedStatement checkStmt = null;
        PreparedStatement insertStmt = null;
        PreparedStatement walletStmt = null;
        ResultSet rs = null;

        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // Start transaction

            // Check if email or username already exists
            String checkSql = "SELECT email, username FROM users WHERE email=? OR username=?";
            checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setString(1, email);
            checkStmt.setString(2, username);
            rs = checkStmt.executeQuery();

            if (rs.next()) {
                String existingEmail = rs.getString("email");
                String existingUsername = rs.getString("username");
                
                if (email.equals(existingEmail)) {
                    try {
                        if (conn != null) {
                            conn.rollback();
                            conn.setAutoCommit(true);
                        }
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                    }
                    sendJsonError(response, "このメールアドレスは既に登録されています");
                    return;
                } else if (username.equals(existingUsername)) {
                    try {
                        if (conn != null) {
                            conn.rollback();
                            conn.setAutoCommit(true);
                        }
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                    }
                    sendJsonError(response, "このユーザー名は既に使用されています");
                    return;
                }
            }

            // Insert new user sesuai struktur tabel users
            String insertSql = "INSERT INTO users (username, email, password_hash, full_name, phone, " +
                              "verification_token, role, is_verified, is_seller, " +
                              "login_attempts, created_at, updated_at) " +
                              "VALUES (?, ?, ?, ?, ?, ?, 'user', 0, 0, 0, NOW(), NOW())";
            
            insertStmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
            insertStmt.setString(1, username);
            insertStmt.setString(2, email);
            insertStmt.setString(3, hashedPassword);
            insertStmt.setString(4, fullName != null && !fullName.trim().isEmpty() ? fullName.trim() : null);
            insertStmt.setString(5, phone != null && !phone.trim().isEmpty() ? phone.trim() : null);
            insertStmt.setString(6, verificationToken);
            
            int rowsAffected = insertStmt.executeUpdate();

            if (rowsAffected > 0) {
                // Get the generated user_id
                rs = insertStmt.getGeneratedKeys();
                if (rs.next()) {
                    int userId = rs.getInt(1);
                    
                    // Create wallet for new user
                    String walletSql = "INSERT INTO user_wallets (user_id, balance, frozen_balance, " +
                                      "total_earned, total_spent, created_at, updated_at) " +
                                      "VALUES (?, 0.00, 0.00, 0.00, 0.00, NOW(), NOW())";
                    walletStmt = conn.prepareStatement(walletSql);
                    walletStmt.setInt(1, userId);
                    walletStmt.executeUpdate();
                }
                
                conn.commit(); // Commit transaction

                // Send verification email
                boolean emailSent = sendVerificationEmail(email, username, verificationToken);
                
                System.out.println("User registered successfully: " + username);
                
                // Send JSON success response
                java.util.Map<String, Object> result = new java.util.HashMap<>();
                result.put("success", true);
                result.put("message", emailSent ? 
                    "登録が完了しました。確認メールを送信しました。" : 
                    "登録が完了しました。確認メールの送信に失敗しましたが、ログインできます。");
                result.put("emailSent", emailSent);
                
                sendJsonResponse(response, result);
            } else {
                conn.rollback();
                sendJsonError(response, "登録に失敗しました");
            }

        } catch (SQLException e) {
            try {
                if (conn != null) {
                    conn.rollback();
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            e.printStackTrace();
            sendJsonError(response, "データベースエラーが発生しました");
        } catch (Exception e) {
            try {
                if (conn != null) {
                    conn.rollback();
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            e.printStackTrace();
            sendJsonError(response, "サーバーエラーが発生しました");
        } finally {
            try {
                if (conn != null) {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            DatabaseConnection.closeResources(rs, checkStmt, insertStmt, walletStmt, conn);
        }
    }

    /**
     * Send verification email to user
     */
    private boolean sendVerificationEmail(String email, String username, String token) {
        try {
            System.out.println("=== Starting verification email send process ===");
            System.out.println("To: " + email);
            System.out.println("From: " + EmailConfig.MAIL_FROM);
            System.out.println("SMTP Host: " + EmailConfig.SMTP_HOST);
            System.out.println("SMTP Port: " + EmailConfig.SMTP_PORT);
            
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
            props.put("mail.smtp.host", EmailConfig.SMTP_HOST);
            props.put("mail.smtp.port", EmailConfig.SMTP_PORT);
            props.put("mail.smtp.ssl.protocols", "TLSv1.2");
            props.put("mail.smtp.connectiontimeout", "30000");
            props.put("mail.smtp.timeout", "30000");
            props.put("mail.smtp.writetimeout", "30000");
            // Additional properties for Gmail compatibility
            props.put("mail.smtp.ssl.trust", EmailConfig.SMTP_HOST);
            props.put("mail.smtp.ssl.checkserveridentity", "true");
            props.put("mail.debug", "true"); // Enable debug for troubleshooting
            // Remove socketFactory for STARTTLS (port 587 uses STARTTLS, not direct SSL)

            System.out.println("Creating mail session...");
            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    System.out.println("Authenticating with email: " + EmailConfig.MAIL_FROM);
                    return new PasswordAuthentication(EmailConfig.MAIL_FROM, EmailConfig.MAIL_PASS);
                }
            });

            System.out.println("Creating message...");
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(EmailConfig.MAIL_FROM, "Karuru Flea Market"));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(email));
            message.setSubject("カルル - アカウント確認 / Karuru Account Verification");

            String verificationLink = EmailConfig.BASE_URL + "/VerifyServlet?code=" + token;
            System.out.println("Verification link: " + verificationLink);
            
            String body = "カルルへようこそ！ / Welcome to Karuru!\n\n" +
                         username + " 様\n\n" +
                         "ご登録ありがとうございます。以下のリンクをクリックしてアカウントを確認してください：\n" +
                         "Thank you for registering. Please click the link below to verify your account:\n\n" +
                         verificationLink + "\n\n" +
                         "このリンクは24時間有効です。\n" +
                         "This link is valid for 24 hours.\n\n" +
                         "カルルチーム / Karuru Team";

            message.setText(body);
            
            System.out.println("Sending email...");
            Transport.send(message);

            System.out.println("✓ Verification email sent successfully to: " + email);
            return true;

        } catch (MessagingException e) {
            System.err.println("✗ MessagingException occurred while sending email:");
            System.err.println("  Error message: " + e.getMessage());
            System.err.println("  Error class: " + e.getClass().getName());
            if (e.getCause() != null) {
                System.err.println("  Cause: " + e.getCause().getMessage());
            }
            e.printStackTrace();
            System.err.println("Failed to send verification email to: " + email);
            return false;
        } catch (Exception e) {
            System.err.println("✗ Unexpected exception occurred while sending email:");
            System.err.println("  Error message: " + e.getMessage());
            System.err.println("  Error class: " + e.getClass().getName());
            e.printStackTrace();
            System.err.println("Failed to send verification email to: " + email);
            return false;
        }
    }
    
    /**
     * Send JSON success response
     */
    private void sendJsonResponse(HttpServletResponse response, java.util.Map<String, Object> data) 
            throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        java.io.PrintWriter out = response.getWriter();
        out.print(gson.toJson(data));
        out.flush();
    }
    
    /**
     * Send JSON error response
     */
    private void sendJsonError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        java.util.Map<String, Object> error = new java.util.HashMap<>();
        error.put("success", false);
        error.put("error", message);
        error.put("message", message);
        java.io.PrintWriter out = response.getWriter();
        out.print(gson.toJson(error));
        out.flush();
    }
}