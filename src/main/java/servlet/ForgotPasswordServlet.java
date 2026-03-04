package servlet;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
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

import util.DatabaseConnection;
import util.EmailConfig;

/**
 * Servlet untuk handle forgot password
 * Endpoint: /ForgotPasswordServlet
 */
@WebServlet({"/ForgotPasswordServlet", "/ForgotPassword"})
public class ForgotPasswordServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    public ForgotPasswordServlet() {
        super();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        String contextPath = request.getContextPath();
        response.sendRedirect(contextPath + "/forgot-password.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        // Set encoding FIRST before reading parameters
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/html; charset=UTF-8");
        
        String contextPath = request.getContextPath();
        String email = request.getParameter("email");
        
        // Validation
        if (email == null || email.trim().isEmpty()) {
            response.sendRedirect(contextPath + "/forgot-password.jsp?error=empty_email");
            return;
        }

        email = email.trim().toLowerCase();

        // Validate email format
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            response.sendRedirect(contextPath + "/forgot-password.jsp?error=invalid_email");
            return;
        }

        String resetToken = UUID.randomUUID().toString();
        Timestamp expiry = new Timestamp(System.currentTimeMillis() + 3600000); // 1 hour

        Connection conn = null;
        PreparedStatement checkStmt = null;
        PreparedStatement updateStmt = null;
        ResultSet rs = null;

        try {
            conn = DatabaseConnection.getConnection();

            // Check if email exists - sesuai struktur tabel users
            String checkSql = "SELECT user_id, username, email FROM users " +
                             "WHERE email=? AND deleted_at IS NULL";
            checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setString(1, email);
            rs = checkStmt.executeQuery();

            if (!rs.next()) {
                // For security, don't reveal if email exists or not
                response.sendRedirect(contextPath + "/forgot-password.jsp?success=true");
                return;
            }

            String username = rs.getString("username");
            int userId = rs.getInt("user_id");

            // Update reset token - sesuai field di tabel users
            String updateSql = "UPDATE users SET reset_token=?, reset_token_expiry=?, " +
                              "updated_at=NOW() WHERE email=?";
            updateStmt = conn.prepareStatement(updateSql);
            updateStmt.setString(1, resetToken);
            updateStmt.setTimestamp(2, expiry);
            updateStmt.setString(3, email);
            
            int updated = updateStmt.executeUpdate();

            if (updated > 0) {
                System.out.println("Reset token updated successfully for user: " + username + " (" + email + ")");
                
                // Send reset email
                System.out.println("Attempting to send reset email to: " + email);
                boolean emailSent = sendResetEmail(email, username, resetToken);
                
                if (emailSent) {
                    System.out.println("Password reset email sent successfully to: " + email);
                    response.sendRedirect(contextPath + "/forgot-password.jsp?success=true");
                } else {
                    System.err.println("Failed to send password reset email to: " + email);
                    response.sendRedirect(contextPath + "/forgot-password.jsp?success=true&warning=email_failed");
                }
            } else {
                System.err.println("Failed to update reset token for email: " + email);
                response.sendRedirect(contextPath + "/forgot-password.jsp?error=update_failed");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            response.sendRedirect(contextPath + "/forgot-password.jsp?error=database_error");
        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect(contextPath + "/forgot-password.jsp?error=server_error");
        } finally {
            DatabaseConnection.closeResources(rs, checkStmt, conn);
            if (updateStmt != null) {
                try {
                    updateStmt.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Send password reset email to user
     */
    private boolean sendResetEmail(String email, String username, String resetToken) {
        try {
            System.out.println("=== Starting password reset email send process ===");
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
            message.setSubject("カルル - パスワードリセット / Karuru Password Reset");

            String resetLink = EmailConfig.BASE_URL + "/ResetPasswordServlet?code=" + resetToken;
            System.out.println("Reset link: " + resetLink);
            
            String body = username + " 様\n\n" +
                         "カルルからのお知らせ\n\n" +
                         "以下のリンクをクリックしてパスワードをリセットしてください：\n" +
                         resetLink + "\n\n" +
                         "このリンクは1時間有効です。\n\n" +
                         "---\n\n" +
                         "Password Reset Request\n\n" +
                         "Click the link below to reset your password:\n" +
                         resetLink + "\n\n" +
                         "This link is valid for 1 hour.\n\n" +
                         "カルルチーム / Karuru Team";

            message.setText(body);
            
            System.out.println("Sending email...");
            Transport.send(message);

            System.out.println("✓ Password reset email sent successfully to: " + email);
            return true;

        } catch (MessagingException e) {
            System.err.println("✗ MessagingException occurred while sending email:");
            System.err.println("  Error message: " + e.getMessage());
            System.err.println("  Error class: " + e.getClass().getName());
            if (e.getCause() != null) {
                System.err.println("  Cause: " + e.getCause().getMessage());
            }
            e.printStackTrace();
            System.err.println("Failed to send password reset email to: " + email);
            return false;
        } catch (Exception e) {
            System.err.println("✗ Unexpected exception occurred while sending email:");
            System.err.println("  Error message: " + e.getMessage());
            System.err.println("  Error class: " + e.getClass().getName());
            e.printStackTrace();
            System.err.println("Failed to send password reset email to: " + email);
            return false;
        }
    }
}