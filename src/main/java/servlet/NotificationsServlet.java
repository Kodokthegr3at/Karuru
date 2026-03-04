package servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.text.SimpleDateFormat;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.websocket.Session;
// WebSocket annotations removed - WebSocket endpoints moved to websocket package
// But Session type is still needed for userSessions map

import com.google.gson.Gson;

import util.DatabaseConnection;
import util.FilterEncodingUTF8;
import util.WebSocketManager;

@WebServlet({"/NotificationsServlet", "/Notification"})
public class NotificationsServlet extends HttpServlet {
    // Keep for backward compatibility with WebSocket endpoint
    static final Map<Integer, Session> userSessions = new ConcurrentHashMap<>();
    private static final Gson gson = new Gson();
    private static final WebSocketManager wsManager = WebSocketManager.getInstance();
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        FilterEncodingUTF8.configureUTF8ForJSON(request, response);
        response.setContentType("application/json; charset=UTF-8");
        PrintWriter out = response.getWriter();
        
        String action = request.getParameter("action");
        javax.servlet.http.HttpSession session = request.getSession(false);
        Integer userId = null;
        if (session != null) {
            // Try user_id first (primary)
            Object userIdObj = session.getAttribute("user_id");
            if (userIdObj == null) {
                // Fallback to userId (alternative)
                userIdObj = session.getAttribute("userId");
            }
            
            if (userIdObj instanceof Integer) {
                userId = (Integer) userIdObj;
            } else if (userIdObj != null) {
                try {
                    userId = Integer.valueOf(userIdObj.toString());
                } catch (NumberFormatException e) {
                    userId = null;
                }
            }
        }
        
        if (userId == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            
            switch (action != null ? action : "") {
                case "getNotifications":
                    getNotifications(conn, userId, request, out);
                    break;
                case "getUnreadCount":
                    getUnreadCount(conn, userId, out);
                    break;
                case "markAsRead":
                    markAsRead(conn, userId, request, out, null);
                    break;
                case "markAllAsRead":
                    markAllAsRead(conn, userId, out);
                    break;
                case "deleteNotification":
                case "delete":
                    deleteNotification(conn, userId, request, out, null);
                    break;
                case "clearAllNotifications":
                    clearAllNotifications(conn, userId, out);
                    break;
                default:
                    sendError(response, "無効なアクションです");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "データベースエラー: " + e.getMessage());
        } catch (NumberFormatException e) {
            e.printStackTrace();
            sendError(response, "無効なパラメータです");
        } finally {
            DatabaseConnection.closeResources(conn);
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        FilterEncodingUTF8.configureUTF8ForJSON(request, response);
        response.setContentType("application/json; charset=UTF-8");
        PrintWriter out = response.getWriter();
        
        // Try to get action from parameter first, then from JSON body
        String action = request.getParameter("action");
        Map<String, Object> jsonBody = null;
        
        // Read JSON body if present
        try {
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            try (java.io.BufferedReader reader = request.getReader()) {
                while ((line = reader.readLine()) != null) {
                    jsonBuilder.append(line);
                }
            }
            
            String body = jsonBuilder.toString();
            if (body != null && !body.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsedBody = gson.fromJson(body, Map.class);
                if (parsedBody != null) {
                    jsonBody = parsedBody;
                    if (action == null || action.isEmpty()) {
                        Object actionObj = parsedBody.get("action");
                        if (actionObj != null) {
                            action = actionObj.toString();
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore JSON parsing errors, will use parameter
            System.out.println("JSON parsing error (may be normal): " + e.getMessage());
        }
        
        javax.servlet.http.HttpSession session = request.getSession(false);
        Integer userId = null;
        if (session != null) {
            // Try user_id first (primary)
            Object userIdObj = session.getAttribute("user_id");
            if (userIdObj == null) {
                // Fallback to userId (alternative)
                userIdObj = session.getAttribute("userId");
            }
            
            if (userIdObj instanceof Integer) {
                userId = (Integer) userIdObj;
            } else if (userIdObj != null) {
                try {
                    userId = Integer.valueOf(userIdObj.toString());
                } catch (NumberFormatException e) {
                    userId = null;
                }
            }
        }
        
        if (userId == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            
            // Normalize action names
            if ("delete".equals(action)) {
                action = "deleteNotification";
            }
            
            switch (action != null ? action : "") {
                case "getNotifications":
                    getNotifications(conn, userId, request, out);
                    break;
                case "getUnreadCount":
                    getUnreadCount(conn, userId, out);
                    break;
                case "markAsRead":
                    markAsRead(conn, userId, request, out, jsonBody);
                    break;
                case "markAllAsRead":
                    markAllAsRead(conn, userId, out);
                    break;
                case "deleteNotification":
                    deleteNotification(conn, userId, request, out, jsonBody);
                    break;
                case "clearAllNotifications":
                    clearAllNotifications(conn, userId, out);
                    break;
                default:
                    sendError(response, "無効なアクションです: " + (action != null ? action : "null"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "データベースエラー: " + e.getMessage());
        } catch (NumberFormatException e) {
            e.printStackTrace();
            sendError(response, "無効なパラメータです");
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "エラー: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(conn);
        }
    }
    
    private void getNotifications(Connection conn, int userId, HttpServletRequest request, PrintWriter out) throws SQLException {
        String filter = request.getParameter("filter");
        String pageParam = request.getParameter("page");
        String pageSizeParam = request.getParameter("pageSize");
        
        int page = (pageParam != null && !pageParam.isEmpty()) ? Integer.parseInt(pageParam) : 1;
        int pageSize = (pageSizeParam != null && !pageSizeParam.isEmpty()) ? Integer.parseInt(pageSizeParam) : 20;
        int offset = (page - 1) * pageSize;
        
        StringBuilder sql = new StringBuilder(
            "SELECT " +
                "n.notification_id, " +
                "n.type, " +
                "n.title, " +
                "n.message, " +
                "n.action_url, " +
                "n.reference_type, " +
                "n.reference_id, " +
                "n.is_read, " +
                "n.read_at, " +
                "n.created_at " +
            "FROM notifications n " +
            "WHERE n.user_id = ?"
        );
        
        List<Object> params = new ArrayList<>();
        params.add(userId);
        
        // Apply filters
        if (filter != null && !"all".equals(filter)) {
            switch (filter) {
                case "unread":
                    sql.append(" AND n.is_read = 0");
                    break;
                case "order":
                    sql.append(" AND n.type = 'order'");
                    break;
                case "message":
                    sql.append(" AND n.type = 'message'");
                    break;
                case "system":
                    sql.append(" AND n.type = 'system'");
                    break;
                case "review":
                    sql.append(" AND n.type = 'review'");
                    break;
                case "promotion":
                    sql.append(" AND n.type = 'promotion'");
                    break;
            }
        }
        
        sql.append(" ORDER BY n.created_at DESC LIMIT ? OFFSET ?");
        params.add(pageSize);
        params.add(offset);
        
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            stmt = conn.prepareStatement(sql.toString());
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            
            rs = stmt.executeQuery();
            List<Map<String, Object>> notifications = new ArrayList<>();
            
            while (rs.next()) {
                Map<String, Object> notification = new HashMap<>();
                
                // Basic notification info
                notification.put("notification_id", rs.getInt("notification_id"));
                String notifType = rs.getString("type") != null ? rs.getString("type") : "system";
                notification.put("type", notifType);
                notification.put("title", rs.getString("title") != null ? rs.getString("title") : "通知");
                notification.put("message", rs.getString("message") != null ? rs.getString("message") : "");
                
                // Normalize action_url based on type
                String actionUrl = rs.getString("action_url");
                String referenceType = rs.getString("reference_type");
                Object referenceIdObj = rs.getObject("reference_id");
                
                if (actionUrl != null) {
                    // For message type, redirect to messages.jsp with user_id from reference_id
                    if ("message".equals(notifType)) {
                        if (referenceIdObj != null) {
                            try {
                                int senderId = 0;
                                if (referenceIdObj instanceof Number) {
                                    senderId = ((Number) referenceIdObj).intValue();
                                } else {
                                    senderId = Integer.parseInt(referenceIdObj.toString());
                                }
                                if (senderId > 0) {
                                    actionUrl = "messages.jsp?user_id=" + senderId;
                                } else {
                                    actionUrl = "messages.jsp";
                                }
                            } catch (NumberFormatException e) {
                                actionUrl = "messages.jsp";
                            }
                        } else {
                            actionUrl = "messages.jsp";
                        }
                    }
                    // For order type: redirect to order-detail.jsp (works for both buyer and seller)
                    else if ("order".equals(notifType)) {
                        if (referenceIdObj != null) {
                            try {
                                int orderId = referenceIdObj instanceof Number
                                    ? ((Number) referenceIdObj).intValue()
                                    : Integer.parseInt(referenceIdObj.toString());
                                if (orderId > 0) {
                                    actionUrl = "order-detail.jsp?id=" + orderId;
                                }
                            } catch (NumberFormatException e) {
                                // Keep original actionUrl from DB
                            }
                        }
                    }
                    // For review type or product reference, use product-detail.jsp#review
                    else if ("review".equals(notifType) || "product".equals(referenceType)) {
                        if (referenceIdObj != null) {
                            int productId = 0;
                            if (referenceIdObj instanceof Number) {
                                productId = ((Number) referenceIdObj).intValue();
                            } else {
                                try {
                                    productId = Integer.parseInt(referenceIdObj.toString());
                                } catch (NumberFormatException e) {
                                    // Keep original URL if can't parse
                                }
                            }
                            if (productId > 0) {
                                actionUrl = "product-detail.jsp?id=" + productId + "#review";
                            }
                        }
                    }
                    // For rental type, use rental-detail.jsp
                    else if ("rental".equals(notifType) || "rental".equals(referenceType)) {
                        if (referenceIdObj != null) {
                            int rentalId = 0;
                            if (referenceIdObj instanceof Number) {
                                rentalId = ((Number) referenceIdObj).intValue();
                            } else {
                                try {
                                    rentalId = Integer.parseInt(referenceIdObj.toString());
                                } catch (NumberFormatException e) {}
                            }
                            if (rentalId > 0) {
                                actionUrl = "rental-detail.jsp?id=" + rentalId;
                            }
                        }
                    }
                }
                notification.put("action_url", actionUrl);
                notification.put("reference_type", referenceType);
                
                // Handle reference_id - can be null
                Object refId = rs.getObject("reference_id");
                if (refId != null) {
                    notification.put("reference_id", refId);
                }
                
                // Handle is_read - ensure it's always a boolean
                boolean isRead = false;
                try {
                    Object isReadObj = rs.getObject("is_read");
                    if (isReadObj instanceof Boolean) {
                        isRead = (Boolean) isReadObj;
                    } else if (isReadObj instanceof Number) {
                        isRead = ((Number) isReadObj).intValue() != 0;
                    } else {
                        // Try getBoolean first
                        try {
                            isRead = rs.getBoolean("is_read");
                        } catch (SQLException e) {
                            // Fallback to int
                            isRead = rs.getInt("is_read") != 0;
                        }
                    }
                } catch (SQLException e) {
                    // If all fails, default to false
                    isRead = false;
                }
                notification.put("is_read", isRead);
                
                // Format timestamps as ISO 8601 strings for JavaScript compatibility
                // Use ISO 8601 format: yyyy-MM-dd'T'HH:mm:ss.SSSXXX
                SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
                isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                
                java.sql.Timestamp readAt = rs.getTimestamp("read_at");
                if (readAt != null) {
                    notification.put("read_at", isoFormat.format(new java.util.Date(readAt.getTime())));
                } else {
                    notification.put("read_at", null);
                }
                
                java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
                if (createdAt != null) {
                    notification.put("created_at", isoFormat.format(new java.util.Date(createdAt.getTime())));
                } else {
                    // Fallback to current time if null (shouldn't happen, but safety check)
                    notification.put("created_at", isoFormat.format(new java.util.Date()));
                }
                
                notifications.add(notification);
            }
            
            // Check if there are more results
            boolean hasMore = notifications.size() == pageSize;
            
            // Get total unread count
            int unreadCount = getTotalUnreadCount(conn, userId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("notifications", notifications);
            result.put("hasMore", hasMore);
            result.put("unreadCount", unreadCount);
            
            out.print(gson.toJson(result));
        } finally {
            DatabaseConnection.closeResources(rs, stmt);
        }
    }
    
    private void getUnreadCount(Connection conn, int userId, PrintWriter out) throws SQLException {
        int unreadCount = getTotalUnreadCount(conn, userId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("count", unreadCount);
        result.put("unread_count", unreadCount); // Support both formats
        
        out.print(gson.toJson(result));
    }
    
    private int getTotalUnreadCount(Connection conn, int userId) throws SQLException {
        String sql = "SELECT COUNT(*) as count FROM notifications WHERE user_id = ? AND is_read = 0";
        
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("count");
            }
        } finally {
            DatabaseConnection.closeResources(rs, stmt);
        }
        return 0;
    }
    
    private void markAsRead(Connection conn, int userId, HttpServletRequest request, PrintWriter out, Map<String, Object> jsonBody) throws SQLException {
        String notificationIdParam = null;
        
        // Try to get from JSON body first, then from parameter
        if (jsonBody != null && jsonBody.containsKey("notification_id")) {
            Object notificationIdObj = jsonBody.get("notification_id");
            if (notificationIdObj instanceof Number) {
                notificationIdParam = String.valueOf(((Number) notificationIdObj).intValue());
            } else if (notificationIdObj != null) {
                notificationIdParam = notificationIdObj.toString();
            }
        }
        
        if (notificationIdParam == null || notificationIdParam.isEmpty()) {
            notificationIdParam = request.getParameter("notification_id");
        }
        
        if (notificationIdParam == null || notificationIdParam.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "通知IDが必要です");
            out.print(gson.toJson(error));
            return;
        }
        
        int notificationId;
        try {
            notificationId = Integer.parseInt(notificationIdParam);
        } catch (NumberFormatException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "無効な通知IDです");
            out.print(gson.toJson(error));
            return;
        }
        
        String sql = "UPDATE notifications SET is_read = 1, read_at = NOW() WHERE notification_id = ? AND user_id = ?";
        
        PreparedStatement stmt = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, notificationId);
            stmt.setInt(2, userId);
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows > 0) {
                int unreadCount = getTotalUnreadCount(conn, userId);
                
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("unreadCount", unreadCount);
                out.print(gson.toJson(result));
            } else {
                sendError(out, "通知が見つかりません");
            }
        } finally {
            DatabaseConnection.closeResources(stmt);
        }
    }
    
    private void markAllAsRead(Connection conn, int userId, PrintWriter out) throws SQLException {
        String sql = "UPDATE notifications SET is_read = 1, read_at = NOW() WHERE user_id = ? AND is_read = 0";
        
        PreparedStatement stmt = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            int affectedRows = stmt.executeUpdate();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("unreadCount", 0);
            result.put("message", "すべての通知を既読にしました");
            result.put("affectedRows", affectedRows);
            out.print(gson.toJson(result));
        } finally {
            DatabaseConnection.closeResources(stmt);
        }
    }
    
    private void deleteNotification(Connection conn, int userId, HttpServletRequest request, PrintWriter out, Map<String, Object> jsonBody) throws SQLException {
        String notificationIdParam = null;
        
        // Try to get from JSON body first, then from parameter
        if (jsonBody != null && jsonBody.containsKey("notification_id")) {
            Object notificationIdObj = jsonBody.get("notification_id");
            if (notificationIdObj instanceof Number) {
                notificationIdParam = String.valueOf(((Number) notificationIdObj).intValue());
            } else if (notificationIdObj != null) {
                notificationIdParam = notificationIdObj.toString();
            }
        }
        
        if (notificationIdParam == null || notificationIdParam.isEmpty()) {
            notificationIdParam = request.getParameter("notification_id");
        }
        
        if (notificationIdParam == null || notificationIdParam.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "通知IDが必要です");
            out.print(gson.toJson(error));
            return;
        }
        
        int notificationId;
        try {
            notificationId = Integer.parseInt(notificationIdParam);
        } catch (NumberFormatException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "無効な通知IDです");
            out.print(gson.toJson(error));
            return;
        }
        
        String sql = "DELETE FROM notifications WHERE notification_id = ? AND user_id = ?";
        
        PreparedStatement stmt = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, notificationId);
            stmt.setInt(2, userId);
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows > 0) {
                int unreadCount = getTotalUnreadCount(conn, userId);
                
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("unreadCount", unreadCount);
                result.put("message", "通知を削除しました");
                out.print(gson.toJson(result));
            } else {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "通知が見つかりません");
                out.print(gson.toJson(error));
            }
        } finally {
            DatabaseConnection.closeResources(stmt);
        }
    }
    
    private void clearAllNotifications(Connection conn, int userId, PrintWriter out) throws SQLException {
        String sql = "DELETE FROM notifications WHERE user_id = ?";
        
        PreparedStatement stmt = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            int affectedRows = stmt.executeUpdate();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("unreadCount", 0);
            result.put("message", "すべての通知を削除しました");
            result.put("affectedRows", affectedRows);
            out.print(gson.toJson(result));
        } finally {
            DatabaseConnection.closeResources(stmt);
        }
    }
    
    // Method to create notifications from other parts of the application
    public static void createNotification(Connection conn, int userId, String type, String title, String message, String actionUrl, String referenceType, Integer referenceId) throws SQLException {
        String sql = "INSERT INTO notifications (user_id, type, title, message, action_url, reference_type, reference_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, NOW())";
        
        PreparedStatement stmt = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            stmt.setString(2, type);
            stmt.setString(3, title);
            stmt.setString(4, message);
            stmt.setString(5, actionUrl);
            stmt.setString(6, referenceType);
            if (referenceId != null) {
                stmt.setInt(7, referenceId);
            } else {
                stmt.setNull(7, Types.INTEGER);
            }
            
            stmt.executeUpdate();
            
            // Send WebSocket notification via WebSocketManager
            wsManager.sendNotification(userId, type, title, message, actionUrl);
        } finally {
            DatabaseConnection.closeResources(stmt);
        }
    }
    
    // Deprecated: Use WebSocketManager.getInstance().sendNotification() instead
    @Deprecated
    private static void sendWebSocketNotification(int userId, String eventType, Object data) {
        // Try WebSocketManager first
        if (data instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) data;
            String type = (String) dataMap.get("type");
            String title = (String) dataMap.get("title");
            String message = (String) dataMap.get("message");
            String actionUrl = (String) dataMap.get("actionUrl");
            
            if (wsManager.sendNotification(userId, type, title, message, actionUrl)) {
                return; // Successfully sent via WebSocketManager
            }
        }
        
        // Fallback to direct session access
        Session session = userSessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                Map<String, Object> notification = new HashMap<>();
                notification.put("event", eventType);
                notification.put("data", data);
                notification.put("timestamp", new Date(System.currentTimeMillis()));
                
                session.getBasicRemote().sendText(new Gson().toJson(notification));
            } catch (IOException e) {
                System.err.println("Error sending WebSocket notification: " + e.getMessage());
            }
        }
    }
    
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

// WebSocket Endpoint has been moved to websocket.NotificationWebSocket class
// This allows Tomcat to properly scan and register the WebSocket endpoint