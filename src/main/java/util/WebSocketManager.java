package util;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.websocket.Session;

import com.google.gson.Gson;

/**
 * WebSocket Manager untuk mengelola koneksi WebSocket dan komunikasi dengan servlet
 * Singleton class untuk akses global dari servlet dan WebSocket endpoint
 */
public class WebSocketManager {
    private static WebSocketManager instance;
    private static final Gson gson = new Gson();
    
    // Maps untuk menyimpan session WebSocket per user
    private final Map<Integer, Session> messageSessions = new ConcurrentHashMap<>();
    private final Map<Integer, Session> notificationSessions = new ConcurrentHashMap<>();
    
    private WebSocketManager() {
        // Private constructor untuk singleton
    }
    
    public static synchronized WebSocketManager getInstance() {
        if (instance == null) {
            instance = new WebSocketManager();
        }
        return instance;
    }
    
    // ==================== MESSAGE SESSION MANAGEMENT ====================
    
    public void addMessageSession(int userId, Session session) {
        messageSessions.put(userId, session);
        System.out.println("[WebSocket] Message session added for user: " + userId);
    }
    
    public void removeMessageSession(int userId) {
        Session removed = messageSessions.remove(userId);
        if (removed != null) {
            System.out.println("[WebSocket] Message session removed for user: " + userId);
        }
    }
    
    public Session getMessageSession(int userId) {
        return messageSessions.get(userId);
    }
    
    public void removeMessageSessionBySession(Session session) {
        messageSessions.entrySet().removeIf(entry -> entry.getValue().equals(session));
    }
    
    // ==================== NOTIFICATION SESSION MANAGEMENT ====================
    
    public void addNotificationSession(int userId, Session session) {
        notificationSessions.put(userId, session);
        System.out.println("[WebSocket] Notification session added for user: " + userId);
    }
    
    public void removeNotificationSession(int userId) {
        Session removed = notificationSessions.remove(userId);
        if (removed != null) {
            System.out.println("[WebSocket] Notification session removed for user: " + userId);
        }
    }
    
    public Session getNotificationSession(int userId) {
        return notificationSessions.get(userId);
    }
    
    public void removeNotificationSessionBySession(Session session) {
        notificationSessions.entrySet().removeIf(entry -> entry.getValue().equals(session));
    }
    
    // ==================== SEND MESSAGE NOTIFICATIONS ====================
    
    /**
     * Kirim notifikasi message baru ke user via WebSocket
     */
    public boolean sendMessageNotification(int userId, int senderId, String messageText, Integer productId) {
        Session session = messageSessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                Map<String, Object> notification = new ConcurrentHashMap<>();
                notification.put("type", "new_message");
                notification.put("sender_id", senderId);
                notification.put("message_text", messageText);
                if (productId != null) {
                    notification.put("product_id", productId);
                }
                notification.put("timestamp", System.currentTimeMillis());
                
                session.getBasicRemote().sendText(gson.toJson(notification));
                System.out.println("[WebSocket] Message notification sent to user: " + userId);
                return true;
            } catch (IOException e) {
                System.err.println("[WebSocket] Error sending message notification to user " + userId + ": " + e.getMessage());
                // Remove invalid session
                removeMessageSession(userId);
                return false;
            }
        }
        return false;
    }
    
    /**
     * Kirim typing indicator ke user via WebSocket
     */
    public boolean sendTypingIndicator(int receiverId, int senderId, String senderName, boolean isTyping) {
        Session session = messageSessions.get(receiverId);
        if (session != null && session.isOpen()) {
            try {
                Map<String, Object> typingMsg = new ConcurrentHashMap<>();
                typingMsg.put("type", "typing");
                typingMsg.put("sender_id", senderId);
                typingMsg.put("sender_name", senderName);
                typingMsg.put("typing", isTyping);
                typingMsg.put("timestamp", System.currentTimeMillis());
                
                session.getBasicRemote().sendText(gson.toJson(typingMsg));
                return true;
            } catch (IOException e) {
                System.err.println("[WebSocket] Error sending typing indicator: " + e.getMessage());
                removeMessageSession(receiverId);
                return false;
            }
        }
        return false;
    }
    
    /**
     * Kirim notifikasi bahwa message sudah dibaca
     */
    public boolean sendMessageReadNotification(int senderId, int messageId) {
        Session session = messageSessions.get(senderId);
        if (session != null && session.isOpen()) {
            try {
                Map<String, Object> readMsg = new ConcurrentHashMap<>();
                readMsg.put("type", "message_read");
                readMsg.put("message_id", messageId);
                readMsg.put("timestamp", System.currentTimeMillis());
                
                session.getBasicRemote().sendText(gson.toJson(readMsg));
                return true;
            } catch (IOException e) {
                System.err.println("[WebSocket] Error sending read notification: " + e.getMessage());
                removeMessageSession(senderId);
                return false;
            }
        }
        return false;
    }
    
    /**
     * Notify sender that receiver has read their messages (real-time 既読)
     */
    public boolean sendMessagesReadNotification(int senderId, int receiverId, Integer productId) {
        Session session = messageSessions.get(senderId);
        if (session != null && session.isOpen()) {
            try {
                Map<String, Object> readMsg = new ConcurrentHashMap<>();
                readMsg.put("type", "messages_read");
                readMsg.put("receiver_id", receiverId);
                readMsg.put("product_id", productId != null ? productId : 0);
                readMsg.put("timestamp", System.currentTimeMillis());
                
                session.getBasicRemote().sendText(gson.toJson(readMsg));
                System.out.println("[WebSocket] Messages read notification sent to sender " + senderId);
                return true;
            } catch (IOException e) {
                System.err.println("[WebSocket] Error sending messages read notification: " + e.getMessage());
                removeMessageSession(senderId);
                return false;
            }
        }
        return false;
    }
    
    // ==================== SEND NOTIFICATIONS ====================
    
    /**
     * Kirim notifikasi umum ke user via WebSocket
     */
    public boolean sendNotification(int userId, String type, String title, String message, String actionUrl) {
        Session session = notificationSessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                Map<String, Object> notification = new ConcurrentHashMap<>();
                notification.put("event", "notification");
                notification.put("type", type);
                
                Map<String, Object> data = new ConcurrentHashMap<>();
                data.put("type", type);
                data.put("title", title);
                data.put("message", message);
                if (actionUrl != null) {
                    data.put("actionUrl", actionUrl);
                }
                
                notification.put("data", data);
                notification.put("timestamp", System.currentTimeMillis());
                
                session.getBasicRemote().sendText(gson.toJson(notification));
                System.out.println("[WebSocket] Notification sent to user: " + userId + " - " + type);
                return true;
            } catch (IOException e) {
                System.err.println("[WebSocket] Error sending notification to user " + userId + ": " + e.getMessage());
                removeNotificationSession(userId);
                return false;
            }
        }
        return false;
    }
    
    /**
     * Kirim notifikasi custom dengan data tambahan
     */
    public boolean sendCustomNotification(int userId, String eventType, Map<String, Object> data) {
        Session session = notificationSessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                Map<String, Object> notification = new ConcurrentHashMap<>();
                notification.put("event", eventType);
                notification.put("data", data);
                notification.put("timestamp", System.currentTimeMillis());
                
                session.getBasicRemote().sendText(gson.toJson(notification));
                return true;
            } catch (IOException e) {
                System.err.println("[WebSocket] Error sending custom notification: " + e.getMessage());
                removeNotificationSession(userId);
                return false;
            }
        }
        return false;
    }
    
    /**
     * Broadcast notifikasi ke multiple users
     */
    public int broadcastNotification(String type, String title, String message, String actionUrl, int[] userIds) {
        int successCount = 0;
        for (int userId : userIds) {
            if (sendNotification(userId, type, title, message, actionUrl)) {
                successCount++;
            }
        }
        return successCount;
    }
    
    // ==================== UTILITY METHODS ====================
    
    /**
     * Cek apakah user memiliki active WebSocket connection
     */
    public boolean hasMessageConnection(int userId) {
        Session session = messageSessions.get(userId);
        return session != null && session.isOpen();
    }
    
    public boolean hasNotificationConnection(int userId) {
        Session session = notificationSessions.get(userId);
        return session != null && session.isOpen();
    }
    
    /**
     * Get jumlah active connections
     */
    public int getActiveMessageConnections() {
        return (int) messageSessions.values().stream()
                .filter(session -> session != null && session.isOpen())
                .count();
    }
    
    public int getActiveNotificationConnections() {
        return (int) notificationSessions.values().stream()
                .filter(session -> session != null && session.isOpen())
                .count();
    }
    
    /**
     * Cleanup invalid sessions
     */
    public void cleanupInvalidSessions() {
        // Clean message sessions
        messageSessions.entrySet().removeIf(entry -> 
            entry.getValue() == null || !entry.getValue().isOpen()
        );
        
        // Clean notification sessions
        notificationSessions.entrySet().removeIf(entry -> 
            entry.getValue() == null || !entry.getValue().isOpen()
        );
    }
}

