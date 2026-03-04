package servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

@WebServlet("/UsersServlet")
public class UsersServlet extends HttpServlet {
    private static final Gson gson = new Gson();
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        FilterEncodingUTF8.configureUTF8ForJSON(request, response);
        PrintWriter out = response.getWriter();
        
        String action = request.getParameter("action");
        HttpSession session = request.getSession(false);
        Integer userId = (session != null) ? (Integer) session.getAttribute("user_id") : null;
        
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            
            switch (action != null ? action : "") {
                case "getUsersForMessaging":
                    if (userId == null) {
                        sendError(response, "ログインが必要です");
                        return;
                    }
                    getUsersForMessaging(conn, userId, out);
                    break;
                    
                case "getUserProfile":
                    getUserProfile(conn, request, out);
                    break;
                    
                case "searchUsers":
                    searchUsers(conn, request, out);
                    break;
                    
                case "getCurrentUser":
                    if (userId == null) {
                        sendError(response, "ログインが必要です");
                        return;
                    }
                    getCurrentUser(conn, userId, out);
                    break;
                    
                case "getAllUsers":
                    getAllUsers(conn, out);
                    break;
                    
                default:
                    sendError(response, "無効なアクションです");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "データベースエラー: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(conn);
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        FilterEncodingUTF8.configureUTF8ForJSON(request, response);
        PrintWriter out = response.getWriter();
        
        String action = request.getParameter("action");
        HttpSession session = request.getSession(false);
        Integer userId = (session != null) ? (Integer) session.getAttribute("user_id") : null;
        
        if (userId == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            
            switch (action != null ? action : "") {
                case "updateProfile":
                    updateProfile(conn, userId, request, out);
                    break;
                    
                case "updateAvatar":
                    updateAvatar(conn, userId, request, out);
                    break;
                    
                case "changePassword":
                    changePassword(conn, userId, request, out);
                    break;
                    
                default:
                    sendError(response, "無効なアクションです");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "データベースエラー: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(conn);
        }
    }
    
    // ==================== GET ALL USERS ====================
    private void getAllUsers(Connection conn, PrintWriter out) throws SQLException {
        System.out.println("=== Getting All Users from Database ===");
        
        String sql = """
            SELECT 
                u.user_id,
                u.username,
                u.email,
                u.full_name,
                u.phone,
                u.avatar_url,
                u.bio,
                u.role,
                u.is_verified,
                u.is_seller,
                u.created_at,
                u.last_login,
                COUNT(DISTINCT p.product_id) as product_count,
                COALESCE(AVG(pr.rating), 0) as average_rating,
                COUNT(DISTINCT pr.review_id) as review_count
            FROM users u
            LEFT JOIN products p ON u.user_id = p.user_id AND p.status = 'available'
            LEFT JOIN product_reviews pr ON u.user_id = pr.user_id AND pr.status = 'approved'
            WHERE u.deleted_at IS NULL
            GROUP BY u.user_id, u.username, u.email, u.full_name, u.phone, 
                     u.avatar_url, u.bio, u.role, u.is_verified, u.is_seller, 
                     u.created_at, u.last_login
            ORDER BY u.created_at DESC
            """;
        
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            
            List<Map<String, Object>> users = new ArrayList<>();
            
            while (rs.next()) {
                Map<String, Object> user = new HashMap<>();
                user.put("user_id", rs.getInt("user_id"));
                user.put("username", rs.getString("username"));
                user.put("email", rs.getString("email"));
                user.put("full_name", rs.getString("full_name"));
                user.put("phone", rs.getString("phone"));
                user.put("avatar_url", rs.getString("avatar_url"));
                user.put("bio", rs.getString("bio"));
                user.put("role", rs.getString("role"));
                user.put("is_verified", rs.getBoolean("is_verified"));
                user.put("is_seller", rs.getBoolean("is_seller"));
                user.put("created_at", rs.getTimestamp("created_at"));
                user.put("last_login", rs.getTimestamp("last_login"));
                user.put("product_count", rs.getInt("product_count"));
                user.put("average_rating", rs.getDouble("average_rating"));
                user.put("review_count", rs.getInt("review_count"));
                
                users.add(user);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("users", users);
            result.put("count", users.size());
            
            System.out.println("✅ Users found: " + users.size());
            
            out.print(gson.toJson(result));
            
        } finally {
            DatabaseConnection.closeResources(rs, stmt);
        }
    }
    
    // ==================== GET USERS FOR MESSAGING ====================
    private void getUsersForMessaging(Connection conn, int currentUserId, PrintWriter out) throws SQLException {
        String sql = """
            SELECT 
                u.user_id,
                u.username,
                u.full_name,
                u.avatar_url,
                u.is_seller,
                u.is_verified
            FROM users u
            WHERE u.user_id != ? 
            AND u.deleted_at IS NULL
            ORDER BY u.username ASC
            """;
        
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, currentUserId);
            
            rs = stmt.executeQuery();
            List<Map<String, Object>> users = new ArrayList<>();
            
            while (rs.next()) {
                Map<String, Object> user = new HashMap<>();
                user.put("user_id", rs.getInt("user_id"));
                user.put("username", rs.getString("username"));
                user.put("full_name", rs.getString("full_name"));
                user.put("avatar_url", rs.getString("avatar_url"));
                user.put("is_seller", rs.getBoolean("is_seller"));
                user.put("is_verified", rs.getBoolean("is_verified"));
                
                users.add(user);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("users", users);
            
            out.print(gson.toJson(result));
        } finally {
            DatabaseConnection.closeResources(rs, stmt);
        }
    }
    
    // ==================== GET USER PROFILE ====================
    private void getUserProfile(Connection conn, HttpServletRequest request, PrintWriter out) throws SQLException {
        String userIdParam = request.getParameter("user_id");
        if (userIdParam == null || userIdParam.isEmpty()) {
            sendError(out, "ユーザーIDが必要です");
            return;
        }
        
        int targetUserId = Integer.parseInt(userIdParam);
        
        String sql = """
            SELECT 
                u.user_id,
                u.username,
                u.email,
                u.full_name,
                u.phone,
                u.avatar_url,
                u.bio,
                u.role,
                u.is_verified,
                u.is_seller,
                u.created_at,
                u.last_login,
                COUNT(DISTINCT p.product_id) as product_count,
                COALESCE(AVG(pr.rating), 0) as average_rating,
                COUNT(DISTINCT pr.review_id) as review_count
            FROM users u
            LEFT JOIN products p ON u.user_id = p.user_id AND p.status = 'available'
            LEFT JOIN product_reviews pr ON u.user_id = pr.user_id AND pr.status = 'approved'
            WHERE u.user_id = ? AND u.deleted_at IS NULL
            GROUP BY u.user_id, u.username, u.email, u.full_name, u.phone, 
                     u.avatar_url, u.bio, u.role, u.is_verified, u.is_seller, 
                     u.created_at, u.last_login
            """;
        
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, targetUserId);
            
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                Map<String, Object> user = new HashMap<>();
                user.put("user_id", rs.getInt("user_id"));
                user.put("username", rs.getString("username"));
                user.put("email", rs.getString("email"));
                user.put("full_name", rs.getString("full_name"));
                user.put("phone", rs.getString("phone"));
                user.put("avatar_url", rs.getString("avatar_url"));
                user.put("bio", rs.getString("bio"));
                user.put("role", rs.getString("role"));
                user.put("is_verified", rs.getBoolean("is_verified"));
                user.put("is_seller", rs.getBoolean("is_seller"));
                user.put("created_at", rs.getTimestamp("created_at"));
                user.put("last_login", rs.getTimestamp("last_login"));
                user.put("product_count", rs.getInt("product_count"));
                user.put("average_rating", rs.getDouble("average_rating"));
                user.put("review_count", rs.getInt("review_count"));
                
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("user", user);
                
                out.print(gson.toJson(result));
            } else {
                sendError(out, "ユーザーが見つかりません");
            }
        } finally {
            DatabaseConnection.closeResources(rs, stmt);
        }
    }
    
    // ==================== SEARCH USERS ====================
    private void searchUsers(Connection conn, HttpServletRequest request, PrintWriter out) throws SQLException {
        String query = request.getParameter("query");
        if (query == null || query.trim().isEmpty()) {
            sendError(out, "検索クエリが必要です");
            return;
        }
        
        String searchTerm = "%" + query.trim() + "%";
        
        String sql = """
            SELECT 
                u.user_id,
                u.username,
                u.full_name,
                u.avatar_url,
                u.is_seller,
                u.is_verified,
                u.bio
            FROM users u
            WHERE (u.username LIKE ? OR u.full_name LIKE ?)
            AND u.deleted_at IS NULL
            ORDER BY u.is_seller DESC, u.username ASC
            LIMIT 50
            """;
        
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, searchTerm);
            stmt.setString(2, searchTerm);
            
            rs = stmt.executeQuery();
            List<Map<String, Object>> users = new ArrayList<>();
            
            while (rs.next()) {
                Map<String, Object> user = new HashMap<>();
                user.put("user_id", rs.getInt("user_id"));
                user.put("username", rs.getString("username"));
                user.put("full_name", rs.getString("full_name"));
                user.put("avatar_url", rs.getString("avatar_url"));
                user.put("is_seller", rs.getBoolean("is_seller"));
                user.put("is_verified", rs.getBoolean("is_verified"));
                user.put("bio", rs.getString("bio"));
                
                users.add(user);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("users", users);
            
            out.print(gson.toJson(result));
        } finally {
            DatabaseConnection.closeResources(rs, stmt);
        }
    }
    
    // ==================== GET CURRENT USER ====================
    private void getCurrentUser(Connection conn, int userId, PrintWriter out) throws SQLException {
        String sql = """
            SELECT 
                u.user_id,
                u.username,
                u.email,
                u.full_name,
                u.phone,
                u.avatar_url,
                u.bio,
                u.role,
                u.is_verified,
                u.is_seller,
                u.created_at,
                u.last_login,
                w.balance,
                w.frozen_balance
            FROM users u
            LEFT JOIN user_wallets w ON u.user_id = w.user_id
            WHERE u.user_id = ? AND u.deleted_at IS NULL
            """;
        
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                Map<String, Object> user = new HashMap<>();
                user.put("user_id", rs.getInt("user_id"));
                user.put("username", rs.getString("username"));
                user.put("email", rs.getString("email"));
                user.put("full_name", rs.getString("full_name"));
                user.put("phone", rs.getString("phone"));
                user.put("avatar_url", rs.getString("avatar_url"));
                user.put("bio", rs.getString("bio"));
                user.put("role", rs.getString("role"));
                user.put("is_verified", rs.getBoolean("is_verified"));
                user.put("is_seller", rs.getBoolean("is_seller"));
                user.put("created_at", rs.getTimestamp("created_at"));
                user.put("last_login", rs.getTimestamp("last_login"));
                user.put("balance", rs.getObject("balance"));
                user.put("frozen_balance", rs.getObject("frozen_balance"));
                
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("user", user);
                
                out.print(gson.toJson(result));
            } else {
                sendError(out, "ユーザーが見つかりません");
            }
        } finally {
            DatabaseConnection.closeResources(rs, stmt);
        }
    }
    
    // ==================== UPDATE PROFILE ====================
    private void updateProfile(Connection conn, int userId, HttpServletRequest request, PrintWriter out) throws SQLException {
        String fullName = request.getParameter("full_name");
        String phone = request.getParameter("phone");
        String bio = request.getParameter("bio");
        
        String sql = """
            UPDATE users 
            SET full_name = ?, 
                phone = ?, 
                bio = ?,
                updated_at = NOW()
            WHERE user_id = ?
            """;
        
        PreparedStatement stmt = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, fullName);
            stmt.setString(2, phone);
            stmt.setString(3, bio);
            stmt.setInt(4, userId);
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows > 0) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "プロフィールを更新しました");
                
                out.print(gson.toJson(result));
            } else {
                sendError(out, "プロフィールの更新に失敗しました");
            }
        } finally {
            DatabaseConnection.closeResources(stmt);
        }
    }
    
    // ==================== UPDATE AVATAR ====================
    private void updateAvatar(Connection conn, int userId, HttpServletRequest request, PrintWriter out) throws SQLException {
        String avatarUrl = request.getParameter("avatar_url");
        
        if (avatarUrl == null || avatarUrl.trim().isEmpty()) {
            sendError(out, "アバターURLが必要です");
            return;
        }
        
        String sql = """
            UPDATE users 
            SET avatar_url = ?,
                updated_at = NOW()
            WHERE user_id = ?
            """;
        
        PreparedStatement stmt = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, avatarUrl);
            stmt.setInt(2, userId);
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows > 0) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "アバターを更新しました");
                result.put("avatar_url", avatarUrl);
                
                out.print(gson.toJson(result));
            } else {
                sendError(out, "アバターの更新に失敗しました");
            }
        } finally {
            DatabaseConnection.closeResources(stmt);
        }
    }
    
    // ==================== CHANGE PASSWORD ====================
    private void changePassword(Connection conn, int userId, HttpServletRequest request, PrintWriter out) throws SQLException {
        String currentPassword = request.getParameter("current_password");
        String newPassword = request.getParameter("new_password");
        
        if (currentPassword == null || newPassword == null || 
            currentPassword.trim().isEmpty() || newPassword.trim().isEmpty()) {
            sendError(out, "現在のパスワードと新しいパスワードが必要です");
            return;
        }
        
        // Validate new password strength
        if (!PasswordUtils.isValidPassword(newPassword)) {
            sendError(out, "新しいパスワードは6文字以上128文字以下である必要があります");
            return;
        }
        
        // Get current password hash
        String checkSql = "SELECT password_hash FROM users WHERE user_id = ?";
        PreparedStatement checkStmt = null;
        ResultSet rs = null;
        
        try {
            checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setInt(1, userId);
            rs = checkStmt.executeQuery();
            
            if (!rs.next()) {
                sendError(out, "ユーザーが見つかりません");
                return;
            }
            
            String storedHash = rs.getString("password_hash");
            
            // Verify current password using BCrypt
            if (!PasswordUtils.checkPassword(currentPassword, storedHash)) {
                sendError(out, "現在のパスワードが正しくありません");
                return;
            }
            
            // Hash new password with BCrypt
            String newPasswordHash = PasswordUtils.hashPassword(newPassword);
            
            String updateSql = """
                UPDATE users 
                SET password_hash = ?,
                    updated_at = NOW()
                WHERE user_id = ?
                """;
            
            PreparedStatement updateStmt = null;
            
            try {
                updateStmt = conn.prepareStatement(updateSql);
                updateStmt.setString(1, newPasswordHash);
                updateStmt.setInt(2, userId);
                
                int affectedRows = updateStmt.executeUpdate();
                
                if (affectedRows > 0) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("message", "パスワードを変更しました");
                    
                    out.print(gson.toJson(result));
                } else {
                    sendError(out, "パスワードの変更に失敗しました");
                }
            } finally {
                DatabaseConnection.closeResources(updateStmt);
            }
            
        } catch (IllegalArgumentException e) {
            sendError(out, e.getMessage());
        } catch (Exception e) {
            sendError(out, "パスワードの変更中にエラーが発生しました");
            e.printStackTrace();
        } finally {
            DatabaseConnection.closeResources(rs, checkStmt);
        }
    }
    
    // ==================== UTILITY METHODS ====================
    private void sendError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        response.getWriter().print(gson.toJson(error));
    }
    
    private void sendError(PrintWriter out, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        out.print(gson.toJson(error));
    }
}