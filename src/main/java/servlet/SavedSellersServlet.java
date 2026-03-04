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

/**
 * Servlet untuk handle saved sellers
 * Menggunakan activity_logs dengan action 'seller_saved' sebagai temporary solution
 */
@WebServlet("/SavedSellersServlet")
public class SavedSellersServlet extends HttpServlet {
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
            getSavedSellers(request, response, userId);
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
                case "saveSeller":
                    saveSeller(request, response, userId);
                    break;
                case "deleteSeller":
                    deleteSeller(request, response, userId);
                    break;
                case "clearAll":
                    clearAll(request, response, userId);
                    break;
                default:
                    sendError(response, "Invalid action");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage());
        }
    }
    
    // ==================== GET SAVED SELLERS ====================
    private void getSavedSellers(HttpServletRequest request, HttpServletResponse response, int userId) 
            throws IOException {
        
        List<Map<String, Object>> sellers = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            // Get saved sellers from activity_logs and join with users
            String sql = """
                SELECT DISTINCT
                    al.log_id, al.entity_id as seller_id, al.created_at,
                    u.username, u.full_name, u.avatar_url, u.is_verified,
                    COUNT(DISTINCT p.product_id) as total_products,
                    AVG(pr.rating) as avg_rating
                FROM activity_logs al
                INNER JOIN users u ON al.entity_id = u.user_id
                LEFT JOIN products p ON u.user_id = p.user_id AND p.status = 'available'
                LEFT JOIN product_reviews pr ON p.product_id = pr.product_id AND pr.status = 'approved'
                WHERE al.user_id = ? 
                AND al.action = 'seller_saved' 
                AND al.entity_type = 'seller'
                AND u.deleted_at IS NULL
                GROUP BY al.log_id, al.entity_id, al.created_at, u.username, u.full_name, u.avatar_url, u.is_verified
                ORDER BY al.created_at DESC
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> seller = new HashMap<>();
                seller.put("log_id", rs.getLong("log_id"));
                seller.put("seller_id", rs.getInt("seller_id"));
                seller.put("username", rs.getString("username"));
                seller.put("full_name", rs.getString("full_name"));
                seller.put("avatar_url", rs.getString("avatar_url"));
                seller.put("is_verified", rs.getBoolean("is_verified"));
                seller.put("total_products", rs.getLong("total_products"));
                seller.put("avg_rating", rs.getDouble("avg_rating"));
                seller.put("created_at", rs.getTimestamp("created_at"));
                sellers.add(seller);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("sellers", sellers);
            result.put("count", sellers.size());
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== SAVE SELLER ====================
    private void saveSeller(HttpServletRequest request, HttpServletResponse response, int userId) 
            throws IOException {
        
        String sellerIdParam = request.getParameter("seller_id");
        if (sellerIdParam == null || sellerIdParam.isEmpty()) {
            sendError(response, "出品者IDが必要です");
            return;
        }
        
        int sellerId = Integer.parseInt(sellerIdParam);
        
        // Don't allow saving self
        if (sellerId == userId) {
            sendError(response, "自分自身を保存することはできません");
            return;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            // Check if already saved
            String checkSql = """
                SELECT log_id FROM activity_logs 
                WHERE user_id = ? AND entity_id = ? AND action = 'seller_saved' AND entity_type = 'seller'
                """;
            stmt = conn.prepareStatement(checkSql);
            stmt.setInt(1, userId);
            stmt.setInt(2, sellerId);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                sendError(response, "既に保存されています");
                return;
            }
            
            rs.close();
            stmt.close();
            
            // Insert saved seller
            String insertSql = """
                INSERT INTO activity_logs 
                (user_id, action, entity_type, entity_id, ip_address, created_at)
                VALUES (?, 'seller_saved', 'seller', ?, 'system', NOW())
                """;
            
            stmt = conn.prepareStatement(insertSql);
            stmt.setInt(1, userId);
            stmt.setInt(2, sellerId);
            
            int rowsAffected = stmt.executeUpdate();
            
            if (rowsAffected > 0) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "出品者を保存しました");
                
                sendJsonResponse(response, result);
            } else {
                sendError(response, "出品者の保存に失敗しました");
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== DELETE SELLER ====================
    private void deleteSeller(HttpServletRequest request, HttpServletResponse response, int userId) 
            throws IOException {
        
        String logIdParam = request.getParameter("log_id");
        if (logIdParam == null || logIdParam.isEmpty()) {
            sendError(response, "ログIDが必要です");
            return;
        }
        
        long logId = Long.parseLong(logIdParam);
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = "DELETE FROM activity_logs WHERE log_id = ? AND user_id = ? AND action = 'seller_saved'";
            stmt = conn.prepareStatement(sql);
            stmt.setLong(1, logId);
            stmt.setInt(2, userId);
            
            int rowsAffected = stmt.executeUpdate();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", rowsAffected > 0);
            result.put("message", rowsAffected > 0 ? "出品者を削除しました" : "出品者が見つかりません");
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(null, stmt, conn);
        }
    }
    
    // ==================== CLEAR ALL ====================
    private void clearAll(HttpServletRequest request, HttpServletResponse response, int userId) 
            throws IOException {
        
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = "DELETE FROM activity_logs WHERE user_id = ? AND action = 'seller_saved' AND entity_type = 'seller'";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            
            int rowsAffected = stmt.executeUpdate();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "すべての出品者を削除しました");
            result.put("deleted_count", rowsAffected);
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(null, stmt, conn);
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

