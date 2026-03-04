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

@WebServlet({"/ActivityServlet", "/Activity"})
public class ActivityServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        FilterEncodingUTF8.configureUTF8ForJSON(request, response);
        
        String action = request.getParameter("action");
        
        try {
            if (action == null || action.isEmpty()) {
                sendError(response, "Action parameter is required");
                return;
            }
            
            switch (action) {
                case "getRecentActivities":
                    getRecentActivities(request, response);
                    break;
                case "getActivityCount":
                    getActivityCount(request, response);
                    break;
                case "getUserActivities":
                    getUserActivities(request, response);
                    break;
                default:
                    sendError(response, "Invalid action: " + action);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage());
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        FilterEncodingUTF8.configureUTF8ForJSON(request, response);
        
        String action = request.getParameter("action");
        
        try {
            if (action == null || action.isEmpty()) {
                sendError(response, "Action parameter is required");
                return;
            }
            
            switch (action) {
                case "logActivity":
                    logActivity(request, response);
                    break;
                case "clearActivities":
                    clearActivities(request, response);
                    break;
                default:
                    sendError(response, "Invalid action: " + action);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage());
        }
    }
    
    // ==================== GET RECENT ACTIVITIES ====================
    private void getRecentActivities(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            sendJsonResponse(response, new ArrayList<>());
            return;
        }
        
        Integer userId = (Integer) session.getAttribute("user_id");
        String limitParam = request.getParameter("limit");
        int limit = 10; // default limit
        
        try {
            if (limitParam != null && !limitParam.isEmpty()) {
                limit = Integer.parseInt(limitParam);
            }
        } catch (NumberFormatException e) {
            limit = 10;
        }
        
        List<Map<String, Object>> activities = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = """
                SELECT 
                    al.log_id,
                    al.user_id,
                    al.action,
                    al.entity_type,
                    al.entity_id,
                    al.ip_address,
                    al.details,
                    al.created_at,
                    u.username,
                    u.full_name
                FROM activity_logs al
                LEFT JOIN users u ON al.user_id = u.user_id
                WHERE al.user_id = ?
                ORDER BY al.created_at DESC
                LIMIT ?
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            stmt.setInt(2, limit);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                activities.add(mapActivityFromResultSet(rs));
            }
            
            System.out.println("✅ Recent activities found: " + activities.size() + " for user: " + userId);
            
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("❌ SQL Error in getRecentActivities: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
        
        sendJsonResponse(response, activities);
    }
    
    // ==================== GET ACTIVITY COUNT ====================
    private void getActivityCount(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            sendJsonResponse(response, Map.of("count", 0));
            return;
        }
        
        Integer userId = (Integer) session.getAttribute("user_id");
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = "SELECT COUNT(*) as activity_count FROM activity_logs WHERE user_id = ?";
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            
            int count = 0;
            if (rs.next()) {
                count = rs.getInt("activity_count");
            }
            
            sendJsonResponse(response, Map.of("count", count));
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendJsonResponse(response, Map.of("count", 0));
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== GET USER ACTIVITIES ====================
    private void getUserActivities(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        Integer userId = (Integer) session.getAttribute("user_id");
        String filter = request.getParameter("filter");
        String pageParam = request.getParameter("page");
        String pageSizeParam = request.getParameter("pageSize");
        
        int page = (pageParam != null && !pageParam.isEmpty()) ? Integer.parseInt(pageParam) : 1;
        int pageSize = (pageSizeParam != null && !pageSizeParam.isEmpty()) ? Integer.parseInt(pageSizeParam) : 20;
        int offset = (page - 1) * pageSize;
        
        List<Map<String, Object>> activities = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            StringBuilder sql = new StringBuilder();
            sql.append("""
                SELECT 
                    al.log_id,
                    al.user_id,
                    al.action,
                    al.entity_type,
                    al.entity_id,
                    al.ip_address,
                    al.details,
                    al.created_at,
                    u.username,
                    u.full_name
                FROM activity_logs al
                LEFT JOIN users u ON al.user_id = u.user_id
                WHERE al.user_id = ?
                """);
            
            List<Object> params = new ArrayList<>();
            params.add(userId);
            
            // Apply filter if provided
            if (filter != null && !filter.isEmpty() && !"all".equals(filter)) {
                sql.append(" AND al.action = ?");
                params.add(filter);
            }
            
            sql.append(" ORDER BY al.created_at DESC LIMIT ? OFFSET ?");
            params.add(pageSize);
            params.add(offset);
            
            stmt = conn.prepareStatement(sql.toString());
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                activities.add(mapActivityFromResultSet(rs));
            }
            
            // Get total count for pagination
            int totalCount = getTotalActivityCount(conn, userId, filter);
            boolean hasMore = (offset + activities.size()) < totalCount;
            
            Map<String, Object> result = new HashMap<>();
            result.put("activities", activities);
            result.put("totalCount", totalCount);
            result.put("hasMore", hasMore);
            result.put("currentPage", page);
            result.put("pageSize", pageSize);
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== LOG ACTIVITY ====================
    private void logActivity(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        String action = request.getParameter("action");
        String entityType = request.getParameter("entity_type");
        String entityIdParam = request.getParameter("entity_id");
        String details = request.getParameter("details");
        
        if (action == null || action.isEmpty()) {
            sendError(response, "Action is required");
            return;
        }
        
        Integer userId = (Integer) session.getAttribute("user_id");
        String ipAddress = request.getRemoteAddr();
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = """
                INSERT INTO activity_logs 
                (user_id, action, entity_type, entity_id, ip_address, details, created_at) 
                VALUES (?, ?, ?, ?, ?, ?, NOW())
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            stmt.setString(2, action);
            
            if (entityType != null && !entityType.isEmpty()) {
                stmt.setString(3, entityType);
            } else {
                stmt.setNull(3, java.sql.Types.VARCHAR);
            }
            
            if (entityIdParam != null && !entityIdParam.isEmpty()) {
                try {
                    int entityId = Integer.parseInt(entityIdParam);
                    stmt.setInt(4, entityId);
                } catch (NumberFormatException e) {
                    stmt.setNull(4, java.sql.Types.INTEGER);
                }
            } else {
                stmt.setNull(4, java.sql.Types.INTEGER);
            }
            
            stmt.setString(5, ipAddress);
            
            if (details != null && !details.isEmpty()) {
                stmt.setString(6, details);
            } else {
                stmt.setNull(6, java.sql.Types.VARCHAR);
            }
            
            int rowsAffected = stmt.executeUpdate();
            
            Map<String, Object> result = new HashMap<>();
            
            if (rowsAffected > 0) {
                result.put("success", true);
                result.put("message", "Activity logged successfully");
            } else {
                result.put("success", false);
                result.put("message", "Failed to log activity");
            }
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(null, stmt, conn);
        }
    }
    
    // ==================== CLEAR ACTIVITIES ====================
    private void clearActivities(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        Integer userId = (Integer) session.getAttribute("user_id");
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = "DELETE FROM activity_logs WHERE user_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            int rowsAffected = stmt.executeUpdate();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Activities cleared successfully");
            result.put("count", rowsAffected);
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(null, stmt, conn);
        }
    }
    
    // ==================== HELPER METHODS ====================
    private int getTotalActivityCount(Connection conn, int userId, String filter) throws SQLException {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            StringBuilder sql = new StringBuilder("SELECT COUNT(*) as total FROM activity_logs WHERE user_id = ?");
            List<Object> params = new ArrayList<>();
            params.add(userId);
            
            if (filter != null && !filter.isEmpty() && !"all".equals(filter)) {
                sql.append(" AND action = ?");
                params.add(filter);
            }
            
            stmt = conn.prepareStatement(sql.toString());
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            
            rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("total");
            }
            return 0;
        } finally {
            DatabaseConnection.closeResources(rs, stmt);
        }
    }
    
    private Map<String, Object> mapActivityFromResultSet(ResultSet rs) throws SQLException {
        Map<String, Object> activity = new HashMap<>();
        
        activity.put("log_id", rs.getLong("log_id"));
        activity.put("user_id", rs.getInt("user_id"));
        activity.put("action", rs.getString("action"));
        activity.put("entity_type", rs.getString("entity_type"));
        activity.put("entity_id", rs.getObject("entity_id"));
        activity.put("ip_address", rs.getString("ip_address"));
        activity.put("details", rs.getString("details"));
        activity.put("created_at", rs.getTimestamp("created_at"));
        activity.put("username", rs.getString("username"));
        activity.put("full_name", rs.getString("full_name"));
        
        return activity;
    }
    
    // ==================== UTILITY METHODS ====================
    private void sendJsonResponse(HttpServletResponse response, Object data) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        PrintWriter out = response.getWriter();
        out.print(gson.toJson(data));
        out.flush();
    }
    
    private void sendError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        PrintWriter out = response.getWriter();
        out.print(gson.toJson(error));
        out.flush();
    }
    
    // ==================== STATIC METHODS FOR OTHER SERVLETS ====================
    
    /**
     * Static method to log activities from other servlets
     */
    public static void logUserActivity(int userId, String action, String entityType, Integer entityId, String details) {
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = """
                INSERT INTO activity_logs 
                (user_id, action, entity_type, entity_id, ip_address, details, created_at) 
                VALUES (?, ?, ?, ?, 'system', ?, NOW())
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            stmt.setString(2, action);
            
            if (entityType != null && !entityType.isEmpty()) {
                stmt.setString(3, entityType);
            } else {
                stmt.setNull(3, java.sql.Types.VARCHAR);
            }
            
            if (entityId != null) {
                stmt.setInt(4, entityId);
            } else {
                stmt.setNull(4, java.sql.Types.INTEGER);
            }
            
            if (details != null && !details.isEmpty()) {
                stmt.setString(5, details);
            } else {
                stmt.setNull(5, java.sql.Types.VARCHAR);
            }
            
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("❌ Failed to log activity: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(null, stmt, conn);
        }
    }
    
    /**
     * Log product view activity
     */
    public static void logProductView(int userId, int productId) {
        logUserActivity(userId, "product_view", "product", productId, 
            "{\"action\": \"view\", \"product_id\": " + productId + "}");
    }
    
    /**
     * Log product like activity
     */
    public static void logProductLike(int userId, int productId) {
        logUserActivity(userId, "product_like", "product", productId, 
            "{\"action\": \"like\", \"product_id\": " + productId + "}");
    }
    
    /**
     * Log product purchase activity
     */
    public static void logProductPurchase(int userId, int productId, int orderId) {
        logUserActivity(userId, "product_purchase", "product", productId, 
            "{\"action\": \"purchase\", \"product_id\": " + productId + ", \"order_id\": " + orderId + "}");
    }
    
    /**
     * Log order creation activity
     */
    public static void logOrderCreate(int userId, int orderId) {
        logUserActivity(userId, "order_create", "order", orderId, 
            "{\"action\": \"create\", \"order_id\": " + orderId + "}");
    }
    
    /**
     * Log message sent activity
     */
    public static void logMessageSent(int userId, int messageId, int receiverId) {
        logUserActivity(userId, "message_sent", "message", messageId, 
            "{\"action\": \"send\", \"message_id\": " + messageId + ", \"receiver_id\": " + receiverId + "}");
    }
    
    /**
     * Log review added activity
     */
    public static void logReviewAdded(int userId, int reviewId, int productId) {
        logUserActivity(userId, "review_added", "review", reviewId, 
            "{\"action\": \"add\", \"review_id\": " + reviewId + ", \"product_id\": " + productId + "}");
    }
    
    /**
     * Log user login activity
     */
    public static void logUserLogin(int userId, String ipAddress) {
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = """
                INSERT INTO activity_logs 
                (user_id, action, entity_type, entity_id, ip_address, details, created_at) 
                VALUES (?, 'user_login', 'user', ?, ?, '{\"action\": \"login\"}', NOW())
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            stmt.setInt(2, userId);
            stmt.setString(3, ipAddress);
            
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("❌ Failed to log user login: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(null, stmt, conn);
        }
    }
}