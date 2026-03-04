package servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.google.gson.Gson;

import util.DatabaseConnection;
import util.FilterEncodingUTF8;
import util.PasswordUtils;

/**
 * Servlet untuk handle user settings
 */
@WebServlet("/SettingsServlet")
public class SettingsServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        FilterEncodingUTF8.configureUTF8ForJSON(request, response);
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        Integer userId = (Integer) session.getAttribute("user_id");
        
        try {
            getSettings(request, response, userId);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage());
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        FilterEncodingUTF8.configureUTF8ForJSON(request, response);
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        Integer userId = (Integer) session.getAttribute("user_id");
        String action = request.getParameter("action");
        
        try {
            switch (action != null ? action : "") {
                case "updateProfile":
                    updateProfile(request, response, userId);
                    break;
                case "changePassword":
                    changePassword(request, response, userId);
                    break;
                case "updateEmail":
                    updateEmail(request, response, userId);
                    break;
                case "deleteAccount":
                    deleteAccount(request, response, userId);
                    break;
                default:
                    sendError(response, "Invalid action");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage());
        }
    }
    
    // ==================== GET SETTINGS ====================
    private void getSettings(HttpServletRequest request, HttpServletResponse response, int userId) 
            throws IOException {
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = "SELECT username, email, full_name, phone, bio, avatar_url, role, is_verified FROM users WHERE user_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                Map<String, Object> settings = new HashMap<>();
                settings.put("username", rs.getString("username"));
                settings.put("email", rs.getString("email"));
                settings.put("full_name", rs.getString("full_name"));
                settings.put("phone", rs.getString("phone"));
                settings.put("bio", rs.getString("bio"));
                settings.put("avatar_url", rs.getString("avatar_url"));
                settings.put("role", rs.getString("role"));
                settings.put("is_verified", rs.getBoolean("is_verified"));
                
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("settings", settings);
                
                sendJsonResponse(response, result);
            } else {
                sendError(response, "ユーザーが見つかりません");
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== UPDATE PROFILE ====================
    private void updateProfile(HttpServletRequest request, HttpServletResponse response, int userId) 
            throws IOException {
        
        String fullName = request.getParameter("full_name");
        String phone = request.getParameter("phone");
        String bio = request.getParameter("bio");
        
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = "UPDATE users SET full_name = ?, phone = ?, bio = ?, updated_at = NOW() WHERE user_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, fullName);
            stmt.setString(2, phone);
            stmt.setString(3, bio);
            stmt.setInt(4, userId);
            
            int rowsAffected = stmt.executeUpdate();
            
            if (rowsAffected > 0) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "プロフィールを更新しました");
                
                sendJsonResponse(response, result);
            } else {
                sendError(response, "プロフィールの更新に失敗しました");
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(null, stmt, conn);
        }
    }
    
    // ==================== CHANGE PASSWORD ====================
    private void changePassword(HttpServletRequest request, HttpServletResponse response, int userId) 
            throws IOException {
        
        String currentPassword = request.getParameter("current_password");
        String newPassword = request.getParameter("new_password");
        
        if (currentPassword == null || newPassword == null || 
            currentPassword.trim().isEmpty() || newPassword.trim().isEmpty()) {
            sendError(response, "現在のパスワードと新しいパスワードが必要です");
            return;
        }
        
        if (!PasswordUtils.isValidPassword(newPassword)) {
            sendError(response, "新しいパスワードは6文字以上128文字以下である必要があります");
            return;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            // Get current password hash
            String checkSql = "SELECT password_hash FROM users WHERE user_id = ?";
            stmt = conn.prepareStatement(checkSql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            
            if (!rs.next()) {
                sendError(response, "ユーザーが見つかりません");
                return;
            }
            
            String storedHash = rs.getString("password_hash");
            
            // Verify current password
            if (!PasswordUtils.checkPassword(currentPassword, storedHash)) {
                sendError(response, "現在のパスワードが正しくありません");
                return;
            }
            
            rs.close();
            stmt.close();
            
            // Update password
            String newPasswordHash = PasswordUtils.hashPassword(newPassword);
            String updateSql = "UPDATE users SET password_hash = ?, updated_at = NOW() WHERE user_id = ?";
            stmt = conn.prepareStatement(updateSql);
            stmt.setString(1, newPasswordHash);
            stmt.setInt(2, userId);
            
            int rowsAffected = stmt.executeUpdate();
            
            if (rowsAffected > 0) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "パスワードを変更しました");
                
                sendJsonResponse(response, result);
            } else {
                sendError(response, "パスワードの変更に失敗しました");
            }
            
        } catch (IllegalArgumentException e) {
            sendError(response, e.getMessage());
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== UPDATE EMAIL ====================
    private void updateEmail(HttpServletRequest request, HttpServletResponse response, int userId) 
            throws IOException {
        
        String newEmail = request.getParameter("new_email");
        String password = request.getParameter("password");
        
        if (newEmail == null || newEmail.trim().isEmpty()) {
            sendError(response, "新しいメールアドレスが必要です");
            return;
        }
        
        if (!newEmail.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            sendError(response, "有効なメールアドレスを入力してください");
            return;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            // Verify password
            if (password != null && !password.trim().isEmpty()) {
                String checkSql = "SELECT password_hash FROM users WHERE user_id = ?";
                stmt = conn.prepareStatement(checkSql);
                stmt.setInt(1, userId);
                rs = stmt.executeQuery();
                
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    if (!PasswordUtils.checkPassword(password, storedHash)) {
                        sendError(response, "パスワードが正しくありません");
                        return;
                    }
                }
                rs.close();
                stmt.close();
            }
            
            // Check if email already exists
            String checkEmailSql = "SELECT user_id FROM users WHERE email = ? AND user_id != ?";
            stmt = conn.prepareStatement(checkEmailSql);
            stmt.setString(1, newEmail.trim().toLowerCase());
            stmt.setInt(2, userId);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                sendError(response, "このメールアドレスは既に使用されています");
                return;
            }
            rs.close();
            stmt.close();
            
            // Update email
            String updateSql = "UPDATE users SET email = ?, is_verified = 0, updated_at = NOW() WHERE user_id = ?";
            stmt = conn.prepareStatement(updateSql);
            stmt.setString(1, newEmail.trim().toLowerCase());
            stmt.setInt(2, userId);
            
            int rowsAffected = stmt.executeUpdate();
            
            if (rowsAffected > 0) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "メールアドレスを更新しました。確認メールを送信しました。");
                
                sendJsonResponse(response, result);
            } else {
                sendError(response, "メールアドレスの更新に失敗しました");
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== DELETE ACCOUNT ====================
    private void deleteAccount(HttpServletRequest request, HttpServletResponse response, int userId) 
            throws IOException {
        
        String password = request.getParameter("password");
        
        if (password == null || password.trim().isEmpty()) {
            sendError(response, "パスワードが必要です");
            return;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            // Verify password
            String checkSql = "SELECT password_hash FROM users WHERE user_id = ?";
            stmt = conn.prepareStatement(checkSql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            
            if (!rs.next()) {
                sendError(response, "ユーザーが見つかりません");
                return;
            }
            
            String storedHash = rs.getString("password_hash");
            if (!PasswordUtils.checkPassword(password, storedHash)) {
                sendError(response, "パスワードが正しくありません");
                return;
            }
            
            rs.close();
            stmt.close();
            
            // Soft delete account (set deleted_at)
            String deleteSql = "UPDATE users SET deleted_at = NOW(), updated_at = NOW() WHERE user_id = ?";
            stmt = conn.prepareStatement(deleteSql);
            stmt.setInt(1, userId);
            
            int rowsAffected = stmt.executeUpdate();
            
            if (rowsAffected > 0) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "アカウントを削除しました");
                
                sendJsonResponse(response, result);
            } else {
                sendError(response, "アカウントの削除に失敗しました");
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== UTILITY METHODS ====================
    private void sendJsonResponse(HttpServletResponse response, Object data) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        out.print(gson.toJson(data));
        out.flush();
    }
    
    private void sendError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        sendJsonResponse(response, error);
    }
}

