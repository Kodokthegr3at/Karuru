package servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
import java.io.File;

@WebServlet({"/MessagesServlet", "/Message"})
@javax.servlet.annotation.MultipartConfig(
    maxFileSize = 10485760, // 10MB
    maxRequestSize = 10485760,
    fileSizeThreshold = 1024
)
public class MessagesServlet extends HttpServlet {
    private static final WebSocketManager wsManager = WebSocketManager.getInstance();
    public static final Map<Integer, Session> userSessions = new ConcurrentHashMap<>();
    private static final Gson gson = new Gson();
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        FilterEncodingUTF8.configureUTF8ForJSON(request, response);
        PrintWriter out = response.getWriter();
        
        String action = request.getParameter("action");
        Integer userId = (Integer) request.getSession().getAttribute("user_id");
        
        if (userId == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            
            switch (action != null ? action : "") {
                case "getConversations":
                    getConversations(conn, userId, out);
                    break;
                case "getMessages":
                    getMessages(conn, userId, request, out);
                    break;
                case "markAsRead":
                    markMessagesAsRead(conn, userId, request, out, null);
                    break;
                case "getUnreadCount":
                    getUnreadCount(conn, userId, out);
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
        response.setContentType("application/json; charset=UTF-8");
        PrintWriter out = response.getWriter();
        
        // Check if this is a multipart request (file upload)
        String contentType = request.getContentType();
        boolean isMultipart = contentType != null && contentType.toLowerCase().startsWith("multipart/form-data");
        
        // Try to get action from parameter first, then from JSON body
        String action = request.getParameter("action");
        Map<String, Object> jsonBody = null;
        
        // If not multipart and action is not in parameter, try to read from JSON body
        if (!isMultipart && (action == null || action.isEmpty())) {
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
                        if (parsedBody.containsKey("action")) {
                            action = (String) parsedBody.get("action");
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore JSON parsing errors, will use parameter
            }
        }
        
        Integer userId = (Integer) request.getSession().getAttribute("user_id");
        
        if (userId == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            
            // Normalize action names (frontend may send 'send' but servlet expects 'sendMessage')
            if ("send".equals(action)) {
                action = "sendMessage";
            }
            
            switch (action != null ? action : "") {
                case "sendMessage":
                    sendMessage(conn, userId, request, out, jsonBody, isMultipart);
                    break;
                case "markAsRead":
                    markMessagesAsRead(conn, userId, request, out, jsonBody);
                    break;
                default:
                    sendError(response, "無効なアクションです: " + (action != null ? action : "null"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "データベースエラー: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "エラー: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(conn);
        }
    }
    
    // ==================== GET UNREAD COUNT ====================
    private void getUnreadCount(Connection conn, int userId, PrintWriter out) throws SQLException {
        String sql = "SELECT COUNT(*) as unread_count FROM messages WHERE receiver_id = ? AND is_read = 0";
        
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            
            int unreadCount = 0;
            if (rs.next()) {
                unreadCount = rs.getInt("unread_count");
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("count", unreadCount);
            
            out.print(gson.toJson(result));
        } finally {
            DatabaseConnection.closeResources(rs, stmt);
        }
    }
    
    private void getConversations(Connection conn, int userId, PrintWriter out) throws SQLException {
        // Simplified query to fix the complex join issues
        String sql = """
            SELECT 
                u.user_id as other_user_id,
                u.username as other_user_name,
                u.full_name as other_user_full_name,
                u.avatar_url as other_user_avatar_url,
                m.message_text as last_message,
                m.sent_at as last_message_time,
                COUNT(CASE WHEN m2.receiver_id = ? AND m2.is_read = 0 THEN 1 END) as unread_count
            FROM (
                SELECT 
                    CASE 
                        WHEN sender_id = ? THEN receiver_id 
                        ELSE sender_id 
                    END as other_user_id,
                    MAX(sent_at) as max_sent_at
                FROM messages 
                WHERE sender_id = ? OR receiver_id = ?
                GROUP BY other_user_id
            ) latest
            INNER JOIN messages m ON (
                (m.sender_id = ? AND m.receiver_id = latest.other_user_id) OR 
                (m.receiver_id = ? AND m.sender_id = latest.other_user_id)
            ) AND m.sent_at = latest.max_sent_at
            INNER JOIN users u ON latest.other_user_id = u.user_id
            LEFT JOIN messages m2 ON (
                (m2.sender_id = latest.other_user_id AND m2.receiver_id = ?) AND m2.is_read = 0
            )
            GROUP BY u.user_id, u.username, u.full_name, u.avatar_url, m.message_text, m.sent_at
            ORDER BY m.sent_at DESC
            """;
        
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            stmt.setInt(2, userId);
            stmt.setInt(3, userId);
            stmt.setInt(4, userId);
            stmt.setInt(5, userId);
            stmt.setInt(6, userId);
            stmt.setInt(7, userId);
            
            rs = stmt.executeQuery();
            List<Map<String, Object>> conversations = new ArrayList<>();
            
            while (rs.next()) {
                Map<String, Object> conversation = new HashMap<>();
                conversation.put("other_user_id", rs.getInt("other_user_id"));
                conversation.put("other_user_name", rs.getString("other_user_name"));
                conversation.put("other_user_full_name", rs.getString("other_user_full_name"));
                String avatarUrl = rs.getString("other_user_avatar_url");
                conversation.put("other_user_avatar_url", avatarUrl != null ? avatarUrl : "");
                conversation.put("last_message", rs.getString("last_message"));
                conversation.put("last_message_time", rs.getTimestamp("last_message_time"));
                conversation.put("unread_count", rs.getInt("unread_count"));
                
                conversations.add(conversation);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("conversations", conversations);
            
            out.print(gson.toJson(result));
        } catch (SQLException e) {
            e.printStackTrace();
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "データベースエラー: " + e.getMessage());
            out.print(gson.toJson(error));
        } finally {
            DatabaseConnection.closeResources(rs, stmt);
        }
    }
    
    private void getMessages(Connection conn, int userId, HttpServletRequest request, PrintWriter out) throws SQLException {
        String otherUserIdParam = request.getParameter("other_user_id");
        if (otherUserIdParam == null || otherUserIdParam.isEmpty()) {
            sendError(out, "相手ユーザーIDが必要です");
            return;
        }
        
        // Handle both "2" and "2.0" formats for other_user_id
        int otherUserId;
        try {
            if (otherUserIdParam.contains(".")) {
                otherUserId = (int) Double.parseDouble(otherUserIdParam);
            } else {
                otherUserId = Integer.parseInt(otherUserIdParam);
            }
        } catch (NumberFormatException e) {
            sendError(out, "無効なユーザーIDです: " + otherUserIdParam);
            return;
        }
        
        String productIdParam = request.getParameter("product_id");
        // Handle both "2" and "2.0" formats for product_id
        Integer productId = null;
        if (productIdParam != null && !productIdParam.isEmpty()) {
            try {
                if (productIdParam.contains(".")) {
                    productId = (int) Double.parseDouble(productIdParam);
                } else {
                    productId = Integer.parseInt(productIdParam);
                }
            } catch (NumberFormatException e) {
                // Product ID is optional, so ignore error
                productId = null;
            }
        }
        
        String sql = """
            SELECT 
                m.message_id,
                m.sender_id,
                m.receiver_id,
                m.product_id,
                m.message_text,
                m.attachment_url,
                m.is_read,
                m.read_at,
                m.sent_at,
                s.username as sender_name,
                s.avatar_url as sender_avatar_url,
                r.username as receiver_name,
                r.avatar_url as receiver_avatar_url,
                p.product_name,
                p.image_url as product_image_url
            FROM messages m
            INNER JOIN users s ON m.sender_id = s.user_id
            INNER JOIN users r ON m.receiver_id = r.user_id
            LEFT JOIN products p ON m.product_id = p.product_id
            WHERE ((m.sender_id = ? AND m.receiver_id = ?) OR (m.sender_id = ? AND m.receiver_id = ?))
            AND (m.product_id = ? OR ? IS NULL)
            ORDER BY m.sent_at ASC
            """;
        
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            stmt.setInt(2, otherUserId);
            stmt.setInt(3, otherUserId);
            stmt.setInt(4, userId);
            if (productId != null) {
                stmt.setInt(5, productId);
                stmt.setInt(6, productId);
            } else {
                stmt.setNull(5, Types.INTEGER);
                stmt.setNull(6, Types.INTEGER);
            }
            
            rs = stmt.executeQuery();
            List<Map<String, Object>> messages = new ArrayList<>();
            
            while (rs.next()) {
                Map<String, Object> message = new HashMap<>();
                message.put("message_id", rs.getInt("message_id"));
                message.put("sender_id", rs.getInt("sender_id"));
                message.put("receiver_id", rs.getInt("receiver_id"));
                message.put("product_id", rs.getObject("product_id"));
                message.put("message_text", rs.getString("message_text"));
                message.put("attachment_url", rs.getString("attachment_url"));
                message.put("is_read", rs.getBoolean("is_read"));
                message.put("read_at", rs.getTimestamp("read_at"));
                message.put("sent_at", rs.getTimestamp("sent_at"));
                message.put("sender_name", rs.getString("sender_name"));
                message.put("sender_avatar_url", rs.getString("sender_avatar_url"));
                message.put("receiver_name", rs.getString("receiver_name"));
                message.put("receiver_avatar_url", rs.getString("receiver_avatar_url"));
                message.put("product_name", rs.getString("product_name"));
                message.put("product_image_url", rs.getString("product_image_url"));
                
                messages.add(message);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("messages", messages);
            
            out.print(gson.toJson(result));
        } finally {
            DatabaseConnection.closeResources(rs, stmt);
        }
    }
    
    private void sendMessage(Connection conn, int userId, HttpServletRequest request, PrintWriter out, Map<String, Object> jsonBody, boolean isMultipart) throws SQLException, IOException {
        // Try to get from parameter first, then from JSON body
        String receiverIdParam = request.getParameter("receiver_id");
        String productIdParam = request.getParameter("product_id");
        String messageText = request.getParameter("message_text");
        
        // If not in parameters, try JSON body
        if (jsonBody != null) {
            if (receiverIdParam == null || receiverIdParam.isEmpty()) {
                Object receiverIdObj = jsonBody.get("receiver_id");
                if (receiverIdObj != null) {
                    receiverIdParam = receiverIdObj.toString();
                }
            }
            if (productIdParam == null || productIdParam.isEmpty()) {
                Object productIdObj = jsonBody.get("product_id");
                if (productIdObj != null) {
                    productIdParam = productIdObj.toString();
                }
            }
            if (messageText == null || messageText.isEmpty()) {
                Object messageTextObj = jsonBody.get("message_text");
                if (messageTextObj != null) {
                    messageText = messageTextObj.toString();
                }
            }
        }
        
        if (receiverIdParam == null || receiverIdParam.isEmpty()) {
            sendError(out, "受信者IDが必要です");
            return;
        }
        
        // Handle both "2" and "2.0" formats for receiver_id
        int receiverId;
        try {
            if (receiverIdParam.contains(".")) {
                receiverId = (int) Double.parseDouble(receiverIdParam);
            } else {
                receiverId = Integer.parseInt(receiverIdParam);
            }
        } catch (NumberFormatException e) {
            sendError(out, "無効な受信者IDです: " + receiverIdParam);
            return;
        }
        
        // Handle both "2" and "2.0" formats for product_id
        Integer productId = null;
        if (productIdParam != null && !productIdParam.isEmpty()) {
            try {
                if (productIdParam.contains(".")) {
                    productId = (int) Double.parseDouble(productIdParam);
                } else {
                    productId = Integer.parseInt(productIdParam);
                }
            } catch (NumberFormatException e) {
                // Product ID is optional, so ignore error
                productId = null;
            }
        }
        
        // Handle file attachments if multipart
        String attachmentUrl = null;
        if (isMultipart) {
            try {
                java.util.Collection<javax.servlet.http.Part> parts = request.getParts();
                for (javax.servlet.http.Part part : parts) {
                    if (part.getName() != null && part.getName().equals("attachments") && part.getSize() > 0) {
                        String fileName = getFileName(part);
                        if (fileName != null && !fileName.isEmpty() && part.getContentType() != null && part.getContentType().startsWith("image/")) {
                            // Generate unique filename
                            String fileExtension = "";
                            int lastDot = fileName.lastIndexOf('.');
                            if (lastDot > 0) {
                                fileExtension = fileName.substring(lastDot);
                            }
                            String uniqueFileName = java.util.UUID.randomUUID().toString() + fileExtension;
                            
                            // Determine upload directory
                            String uploadDir = "img/attachments";
                            String workspacePath = System.getProperty("user.dir");
                            if (workspacePath == null || !workspacePath.contains("workspace")) {
                                String catalinaBase = System.getProperty("catalina.base");
                                if (catalinaBase != null) {
                                    java.io.File baseFile = new java.io.File(catalinaBase);
                                    java.io.File parent = baseFile.getParentFile();
                                    if (parent != null && parent.getName().equals("workspace")) {
                                        workspacePath = parent.getAbsolutePath();
                                    }
                                }
                            }
                            
                            String uploadPath = workspacePath + java.io.File.separator + "KaruruFleaMarket" + 
                                              java.io.File.separator + "src" + java.io.File.separator + "main" + 
                                              java.io.File.separator + "webapp" + java.io.File.separator + uploadDir;
                            
                            java.io.File uploadDirFile = new java.io.File(uploadPath);
                            if (!uploadDirFile.exists()) {
                                uploadDirFile.mkdirs();
                            }
                            
                            String filePath = uploadPath + java.io.File.separator + uniqueFileName;
                            
                            // Save file
                            java.io.InputStream fileContent = part.getInputStream();
                            java.io.FileOutputStream outputStream = new java.io.FileOutputStream(filePath);
                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            while ((bytesRead = fileContent.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, bytesRead);
                            }
                            outputStream.flush();
                            outputStream.close();
                            fileContent.close();
                            
                            // Set attachment URL (relative to webapp root)
                            attachmentUrl = uploadDir + "/" + uniqueFileName;
                            break; // Only handle first attachment for now
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error handling attachment: " + e.getMessage());
                e.printStackTrace();
                // Continue without attachment
            }
        }
        
        if ((messageText == null || messageText.trim().isEmpty()) && attachmentUrl == null) {
            sendError(out, "メッセージテキストまたは添付ファイルが必要です");
            return;
        }
        
        // Validate product_id if provided
        if (productId != null) {
            // Check if product exists and is related to this conversation
            String productCheckSql = """
                SELECT p.user_id as seller_id,
                       (SELECT COUNT(*) FROM messages 
                        WHERE ((sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?))
                        AND product_id = ?) as previous_usage
                FROM products p
                WHERE p.product_id = ?
                """;
            
            PreparedStatement productCheckStmt = null;
            ResultSet productCheckRs = null;
            
            try {
                productCheckStmt = conn.prepareStatement(productCheckSql);
                productCheckStmt.setInt(1, userId);
                productCheckStmt.setInt(2, receiverId);
                productCheckStmt.setInt(3, receiverId);
                productCheckStmt.setInt(4, userId);
                productCheckStmt.setInt(5, productId);
                productCheckStmt.setInt(6, productId);
                
                productCheckRs = productCheckStmt.executeQuery();
                
                if (!productCheckRs.next()) {
                    // Product doesn't exist
                    sendError(out, "商品が見つかりません");
                    return;
                }
                
                int sellerId = productCheckRs.getInt("seller_id");
                int previousUsage = productCheckRs.getInt("previous_usage");
                
                // Allow product_id if:
                // 1. Product belongs to sender (seller sending to buyer)
                // 2. Product belongs to receiver (buyer sending to seller)
                // 3. Product has been used in previous messages in this conversation
                boolean isSeller = (sellerId == userId);
                boolean isBuyer = (sellerId == receiverId);
                boolean wasUsedBefore = (previousUsage > 0);
                
                if (!isSeller && !isBuyer && !wasUsedBefore) {
                    sendError(out, "この商品はこの会話に関連していません");
                    return;
                }
                
            } catch (SQLException e) {
                e.printStackTrace();
                sendError(out, "商品の検証中にエラーが発生しました");
                return;
            } finally {
                DatabaseConnection.closeResources(productCheckRs, productCheckStmt);
            }
        }
        
        String sql = "INSERT INTO messages (sender_id, receiver_id, product_id, message_text, attachment_url, sent_at) VALUES (?, ?, ?, ?, ?, NOW())";
        
        PreparedStatement stmt = null;
        ResultSet generatedKeys = null;
        
        try {
            stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            stmt.setInt(1, userId);
            stmt.setInt(2, receiverId);
            if (productId != null) {
                stmt.setInt(3, productId);
            } else {
                stmt.setNull(3, Types.INTEGER);
            }
            stmt.setString(4, messageText != null ? messageText.trim() : "");
            if (attachmentUrl != null) {
                stmt.setString(5, attachmentUrl);
            } else {
                stmt.setNull(5, Types.VARCHAR);
            }
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows > 0) {
                generatedKeys = stmt.getGeneratedKeys();
                int messageId = -1;
                if (generatedKeys.next()) {
                    messageId = generatedKeys.getInt(1);
                }
                
                // Log message activity (if Activity class exists)
                logMessageActivity(userId, messageId, receiverId);
                
                // Send real-time notification via WebSocket
                sendWebSocketNotification(receiverId, userId, messageText.trim(), productId);
                
                // Create notification
                createMessageNotification(conn, userId, receiverId, messageId);
                
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message_id", messageId);
                out.print(gson.toJson(result));
            } else {
                sendError(out, "メッセージの送信に失敗しました");
            }
        } finally {
            DatabaseConnection.closeResources(generatedKeys, stmt);
        }
    }
    
    private void markMessagesAsRead(Connection conn, int userId, HttpServletRequest request, PrintWriter out, Map<String, Object> jsonBody) throws SQLException {
        // Try to get from parameter first, then from JSON body
        String otherUserIdParam = request.getParameter("other_user_id");
        String productIdParam = request.getParameter("product_id");
        
        // If not in parameters, try JSON body
        if (jsonBody != null) {
            if (otherUserIdParam == null || otherUserIdParam.isEmpty()) {
                Object otherUserIdObj = jsonBody.get("other_user_id");
                if (otherUserIdObj != null) {
                    // Handle both Integer and Double (from JSON)
                    if (otherUserIdObj instanceof Double) {
                        otherUserIdParam = String.valueOf(((Double) otherUserIdObj).intValue());
                    } else if (otherUserIdObj instanceof Integer) {
                        otherUserIdParam = String.valueOf((Integer) otherUserIdObj);
                    } else {
                        otherUserIdParam = otherUserIdObj.toString();
                    }
                }
            }
            if (productIdParam == null || productIdParam.isEmpty()) {
                Object productIdObj = jsonBody.get("product_id");
                if (productIdObj != null) {
                    // Handle both Integer and Double (from JSON)
                    if (productIdObj instanceof Double) {
                        productIdParam = String.valueOf(((Double) productIdObj).intValue());
                    } else if (productIdObj instanceof Integer) {
                        productIdParam = String.valueOf((Integer) productIdObj);
                    } else {
                        productIdParam = productIdObj.toString();
                    }
                }
            }
        }
        
        if (otherUserIdParam == null || otherUserIdParam.isEmpty()) {
            sendError(out, "相手ユーザーIDが必要です");
            return;
        }
        
        int otherUserId;
        try {
            // Handle both "2" and "2.0" formats
            if (otherUserIdParam.contains(".")) {
                otherUserId = (int) Double.parseDouble(otherUserIdParam);
            } else {
                otherUserId = Integer.parseInt(otherUserIdParam);
            }
        } catch (NumberFormatException e) {
            sendError(out, "無効なユーザーIDです: " + otherUserIdParam);
            return;
        }
        
        Integer productId = null;
        if (productIdParam != null && !productIdParam.isEmpty()) {
            try {
                // Handle both "2" and "2.0" formats
                if (productIdParam.contains(".")) {
                    productId = (int) Double.parseDouble(productIdParam);
                } else {
                    productId = Integer.parseInt(productIdParam);
                }
            } catch (NumberFormatException e) {
                // Product ID is optional, so ignore error
                productId = null;
            }
        }
        
        String sql = "UPDATE messages SET is_read = 1, read_at = NOW() WHERE receiver_id = ? AND sender_id = ? AND is_read = 0";
        if (productId != null) {
            sql += " AND product_id = ?";
        }
        
        PreparedStatement stmt = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            stmt.setInt(2, otherUserId);
            if (productId != null) {
                stmt.setInt(3, productId);
            }
            
            int updatedRows = stmt.executeUpdate();
            
            // Send WebSocket notification to sender for real-time 既読 update
            if (updatedRows > 0) {
                wsManager.sendMessagesReadNotification(otherUserId, userId, productId);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("updated_count", updatedRows);
            out.print(gson.toJson(result));
        } finally {
            DatabaseConnection.closeResources(stmt);
        }
    }
    
    private void createMessageNotification(Connection conn, int senderId, int receiverId, int messageId) throws SQLException {
        String sql = "INSERT INTO notifications (user_id, type, title, message, action_url, reference_type, reference_id, created_at) VALUES (?, 'message', ?, ?, ?, 'message', ?, NOW())";
        
        PreparedStatement stmt = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, receiverId);
            
            // Get sender name for notification
            String senderName = getUsername(conn, senderId);
            String title = "新しいメッセージ";
            String message = senderName + "さんからメッセージが届きました";
            String actionUrl = "messages.jsp";
            
            stmt.setString(2, title);
            stmt.setString(3, message);
            stmt.setString(4, actionUrl);
            stmt.setInt(5, messageId);
            
            stmt.executeUpdate();
            
            // Send WebSocket notification
            Map<String, Object> notificationData = new HashMap<>();
            notificationData.put("type", "notification");
            notificationData.put("title", title);
            notificationData.put("message", message);
            notificationData.put("actionUrl", actionUrl);
            sendWebSocketNotification(receiverId, "notification", notificationData);
        } finally {
            DatabaseConnection.closeResources(stmt);
        }
    }
    
    private String getUsername(Connection conn, int userId) throws SQLException {
        String sql = "SELECT username FROM users WHERE user_id = ?";
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("username");
            }
        } finally {
            DatabaseConnection.closeResources(rs, stmt);
        }
        return "ユーザー";
    }
    
    // Overloaded method for WebSocket notifications
    private void sendWebSocketNotification(int userId, int senderId, String messageText, Integer productId) {
        // Use WebSocketManager for better session management
        boolean sent = wsManager.sendMessageNotification(userId, senderId, messageText, productId);
        if (!sent) {
            // Fallback to direct session access for backward compatibility
            Session session = userSessions.get(userId);
            if (session != null && session.isOpen()) {
                try {
                    Map<String, Object> notification = new HashMap<>();
                    notification.put("type", "new_message");
                    notification.put("sender_id", senderId);
                    notification.put("message_text", messageText);
                    if (productId != null) {
                        notification.put("product_id", productId);
                    }
                    notification.put("timestamp", System.currentTimeMillis());
                    
                    session.getBasicRemote().sendText(gson.toJson(notification));
                } catch (IOException e) {
                    System.err.println("Error sending message notification: " + e.getMessage());
                }
            }
        }
    }
    
    // Overloaded method for general notifications
    private void sendWebSocketNotification(int userId, String type, Map<String, Object> data) {
        // Extract notification data
        String title = (String) data.get("title");
        String message = (String) data.get("message");
        String actionUrl = (String) data.get("actionUrl");
        
        // Use WebSocketManager for notification
        boolean sent = wsManager.sendNotification(userId, type, title, message, actionUrl);
        if (!sent) {
            // Fallback to direct session access
            Session session = userSessions.get(userId);
            if (session != null && session.isOpen()) {
                try {
                    Map<String, Object> notification = new HashMap<>();
                    notification.put("type", type);
                    notification.putAll(data);
                    notification.put("timestamp", System.currentTimeMillis());
                    
                    session.getBasicRemote().sendText(gson.toJson(notification));
                } catch (IOException e) {
                    System.err.println("Error sending notification: " + e.getMessage());
                }
            }
        }
    }
    
    // Helper method to log message activity
    private void logMessageActivity(int userId, int messageId, int receiverId) {
        // This would typically log to activity_logs table
        // For now, we'll just print to console
        System.out.println("Message sent: User " + userId + " to User " + receiverId + ", Message ID: " + messageId);
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
    
    private String getFileName(javax.servlet.http.Part part) {
        String contentDisposition = part.getHeader("content-disposition");
        if (contentDisposition != null) {
            String[] tokens = contentDisposition.split(";");
            for (String token : tokens) {
                if (token.trim().startsWith("filename")) {
                    String fileName = token.substring(token.indexOf('=') + 1).trim().replace("\"", "");
                    return fileName;
                }
            }
        }
        return null;
    }
}

// WebSocket Endpoint has been moved to websocket.MessageWebSocket class
// This allows Tomcat to properly scan and register the WebSocket endpoint