package websocket;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import com.google.gson.Gson;

import util.WebSocketManager;

/**
 * WebSocket Endpoint for Real-time Notifications
 */
@ServerEndpoint("/notification-websocket")
public class NotificationWebSocket {
    private static final Gson gson = new Gson();
    
    @OnOpen
    public void onOpen(Session session) {
        String query = session.getQueryString();
        Integer userId = extractUserIdFromQuery(query);
        
        if (userId != null) {
            // Add to WebSocketManager
            WebSocketManager.getInstance().addNotificationSession(userId, session);
            // Also keep in NotificationsServlet for backward compatibility
            try {
                Class<?> notificationsServletClass = Class.forName("servlet.NotificationsServlet");
                java.lang.reflect.Field userSessionsField = notificationsServletClass.getField("userSessions");
                @SuppressWarnings("unchecked")
                Map<Integer, Session> userSessions = (Map<Integer, Session>) userSessionsField.get(null);
                if (userSessions != null) {
                    userSessions.put(userId, session);
                }
            } catch (Exception e) {
                // Ignore if NotificationsServlet is not available
            }
            System.out.println("[NotificationWebSocket] Connected: User " + userId);
        } else {
            System.out.println("[NotificationWebSocket] Connection failed: User ID not found in query");
        }
    }
    
    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> msgData = gson.fromJson(message, Map.class);
            String type = (String) msgData.get("type");
            
            if ("ping".equals(type)) {
                // Handle ping for keep-alive - respond with pong
                try {
                    Map<String, Object> pong = new HashMap<>();
                    pong.put("type", "pong");
                    session.getBasicRemote().sendText(gson.toJson(pong));
                } catch (IOException e) {
                    System.err.println("[NotificationWebSocket] Error sending pong: " + e.getMessage());
                }
            }
            // Handle other message types if needed
        } catch (Exception e) {
            System.err.println("[NotificationWebSocket] Error processing message: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @OnClose
    public void onClose(Session session) {
        Integer userIdToRemove = null;
        
        // Find userId from WebSocketManager
        WebSocketManager wsManager = WebSocketManager.getInstance();
        for (Map.Entry<Integer, Session> entry : getNotificationSessions().entrySet()) {
            if (entry.getValue().equals(session)) {
                userIdToRemove = entry.getKey();
                break;
            }
        }
        
        if (userIdToRemove != null) {
            // Remove from WebSocketManager
            wsManager.removeNotificationSession(userIdToRemove);
            
            // Also remove from NotificationsServlet for backward compatibility
            try {
                Class<?> notificationsServletClass = Class.forName("servlet.NotificationsServlet");
                java.lang.reflect.Field userSessionsField = notificationsServletClass.getField("userSessions");
                @SuppressWarnings("unchecked")
                Map<Integer, Session> userSessions = (Map<Integer, Session>) userSessionsField.get(null);
                if (userSessions != null) {
                    userSessions.remove(userIdToRemove);
                }
            } catch (Exception e) {
                // Ignore if NotificationsServlet is not available
            }
            
            System.out.println("[NotificationWebSocket] Disconnected: User " + userIdToRemove);
        } else {
            // Try to remove by session
            wsManager.removeNotificationSessionBySession(session);
        }
    }
    
    @OnError
    public void onError(Session session, Throwable error) {
        System.err.println("[NotificationWebSocket] Error: " + error.getMessage());
        error.printStackTrace();
    }
    
    private Integer extractUserIdFromQuery(String query) {
        if (query != null) {
            try {
                String[] params = query.split("&");
                for (String param : params) {
                    // Support both userId and user_id
                    if (param.startsWith("userId=")) {
                        return Integer.parseInt(param.substring(7));
                    } else if (param.startsWith("user_id=")) {
                        return Integer.parseInt(param.substring(8));
                    }
                }
            } catch (NumberFormatException e) {
                System.err.println("[NotificationWebSocket] Invalid userId format: " + query);
            }
        }
        return null;
    }
    
    @SuppressWarnings("unchecked")
    private Map<Integer, Session> getNotificationSessions() {
        try {
            // Access WebSocketManager's notificationSessions via reflection
            WebSocketManager wsManager = WebSocketManager.getInstance();
            java.lang.reflect.Field field = WebSocketManager.class.getDeclaredField("notificationSessions");
            field.setAccessible(true);
            return (Map<Integer, Session>) field.get(wsManager);
        } catch (Exception e) {
            // Return empty map if reflection fails
            return new HashMap<>();
        }
    }
}

