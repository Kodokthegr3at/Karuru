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
 * WebSocket Endpoint for Real-time Messaging
 */
@ServerEndpoint("/message-websocket")
public class MessageWebSocket {
    private static final Gson gson = new Gson();
    
    @OnOpen
    public void onOpen(Session session) {
        String query = session.getQueryString();
        Integer userId = extractUserIdFromQuery(query);
        
        if (userId != null) {
            // Add to WebSocketManager
            WebSocketManager.getInstance().addMessageSession(userId, session);
            // Also keep in MessagesServlet for backward compatibility
            try {
                Class<?> messagesServletClass = Class.forName("servlet.MessagesServlet");
                java.lang.reflect.Field userSessionsField = messagesServletClass.getField("userSessions");
                @SuppressWarnings("unchecked")
                Map<Integer, Session> userSessions = (Map<Integer, Session>) userSessionsField.get(null);
                if (userSessions != null) {
                    userSessions.put(userId, session);
                }
            } catch (Exception e) {
                // Ignore if MessagesServlet is not available
            }
            System.out.println("[MessageWebSocket] Connected: User " + userId);
        } else {
            System.out.println("[MessageWebSocket] Connection failed: User ID not found in query");
        }
    }
    
    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> msgData = gson.fromJson(message, Map.class);
            String type = (String) msgData.get("type");
            
            if ("typing".equals(type)) {
                Integer receiverId = null;
                Object receiverIdObj = msgData.get("receiverId") != null ? 
                    msgData.get("receiverId") : msgData.get("receiver_id");
                
                if (receiverIdObj instanceof Double) {
                    receiverId = ((Double) receiverIdObj).intValue();
                } else if (receiverIdObj instanceof Integer) {
                    receiverId = (Integer) receiverIdObj;
                } else if (receiverIdObj != null) {
                    try {
                        receiverId = Integer.parseInt(receiverIdObj.toString());
                    } catch (NumberFormatException e) {
                        // Invalid receiver ID
                    }
                }
                
                Boolean isTyping = null;
                Object typingObj = msgData.get("typing") != null ? 
                    msgData.get("typing") : msgData.get("isTyping");
                if (typingObj instanceof Boolean) {
                    isTyping = (Boolean) typingObj;
                } else if (typingObj != null) {
                    isTyping = Boolean.parseBoolean(typingObj.toString());
                }
                
                if (receiverId != null && isTyping != null) {
                    Integer senderId = extractUserIdFromQuery(session.getQueryString());
                    String senderName = null; // Could be retrieved from database if needed
                    
                    // Use WebSocketManager to send typing indicator
                    WebSocketManager.getInstance().sendTypingIndicator(receiverId, senderId, senderName, isTyping);
                }
            } else if ("ping".equals(type)) {
                // Handle ping for keep-alive - respond with pong
                try {
                    Map<String, Object> pong = new HashMap<>();
                    pong.put("type", "pong");
                    session.getBasicRemote().sendText(gson.toJson(pong));
                } catch (IOException e) {
                    System.err.println("[MessageWebSocket] Error sending pong: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[MessageWebSocket] Error processing message: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @OnClose
    public void onClose(Session session) {
        Integer userIdToRemove = null;
        
        // Find userId from WebSocketManager
        WebSocketManager wsManager = WebSocketManager.getInstance();
        for (Map.Entry<Integer, Session> entry : getMessageSessions().entrySet()) {
            if (entry.getValue().equals(session)) {
                userIdToRemove = entry.getKey();
                break;
            }
        }
        
        if (userIdToRemove != null) {
            // Remove from WebSocketManager
            wsManager.removeMessageSession(userIdToRemove);
            
            // Also remove from MessagesServlet for backward compatibility
            try {
                Class<?> messagesServletClass = Class.forName("servlet.MessagesServlet");
                java.lang.reflect.Field userSessionsField = messagesServletClass.getField("userSessions");
                @SuppressWarnings("unchecked")
                Map<Integer, Session> userSessions = (Map<Integer, Session>) userSessionsField.get(null);
                if (userSessions != null) {
                    userSessions.remove(userIdToRemove);
                }
            } catch (Exception e) {
                // Ignore if MessagesServlet is not available
            }
            
            System.out.println("[MessageWebSocket] Disconnected: User " + userIdToRemove);
        } else {
            // Try to remove by session
            wsManager.removeMessageSessionBySession(session);
        }
    }
    
    @OnError
    public void onError(Session session, Throwable error) {
        System.err.println("[MessageWebSocket] Error: " + error.getMessage());
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
                System.err.println("[MessageWebSocket] Invalid userId format: " + query);
            }
        }
        return null;
    }
    
    @SuppressWarnings("unchecked")
    private Map<Integer, Session> getMessageSessions() {
        try {
            // Access WebSocketManager's messageSessions via reflection
            WebSocketManager wsManager = WebSocketManager.getInstance();
            java.lang.reflect.Field field = WebSocketManager.class.getDeclaredField("messageSessions");
            field.setAccessible(true);
            return (Map<Integer, Session>) field.get(wsManager);
        } catch (Exception e) {
            // Return empty map if reflection fails
            return new HashMap<>();
        }
    }
}

