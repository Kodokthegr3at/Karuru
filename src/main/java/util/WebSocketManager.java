package util;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.Session;

/**
 * Open WebSocket connections per user, for pushing chat messages and notifications.
 * A user can have several connections at once (one per browser tab); every push goes to all of them.
 */
public final class WebSocketManager {
    private static final Logger LOG = Logger.getLogger(WebSocketManager.class.getName());
    private static final WebSocketManager INSTANCE = new WebSocketManager();

    public enum Channel { MESSAGES, NOTIFICATIONS }

    private final Map<Channel, Map<Integer, Set<Session>>> sessions = Map.of(
            Channel.MESSAGES, new ConcurrentHashMap<>(),
            Channel.NOTIFICATIONS, new ConcurrentHashMap<>());

    private WebSocketManager() {
    }

    public static WebSocketManager getInstance() {
        return INSTANCE;
    }

    public void add(Channel channel, int userId, Session session) {
        sessions.get(channel).computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void remove(Channel channel, Session session) {
        sessions.get(channel).values().forEach(set -> set.remove(session));
        sessions.get(channel).values().removeIf(Set::isEmpty);
    }

    public boolean sendMessageNotification(int userId, int senderId, String messageText, Integer productId) {
        Map<String, Object> payload = Json.obj("type", "new_message", "sender_id", senderId, "message_text", messageText,
                "timestamp", System.currentTimeMillis());
        if (productId != null) {
            payload.put("product_id", productId);
        }
        return send(Channel.MESSAGES, userId, payload);
    }

    public boolean sendTypingIndicator(int receiverId, int senderId, String senderName, boolean isTyping) {
        return send(Channel.MESSAGES, receiverId, Json.obj("type", "typing", "sender_id", senderId,
                "sender_name", senderName, "typing", isTyping, "timestamp", System.currentTimeMillis()));
    }

    /** Tells the original sender that the receiver has read their messages. */
    public boolean sendMessagesReadNotification(int senderId, int receiverId, Integer productId) {
        return send(Channel.MESSAGES, senderId, Json.obj("type", "messages_read", "receiver_id", receiverId,
                "product_id", productId == null ? 0 : productId, "timestamp", System.currentTimeMillis()));
    }

    public boolean sendNotification(int userId, String type, String title, String message, String actionUrl) {
        Map<String, Object> data = Json.obj("type", type, "title", title, "message", message);
        if (actionUrl != null) {
            data.put("actionUrl", actionUrl);
        }
        return send(Channel.NOTIFICATIONS, userId, Json.obj("event", "notification", "type", type, "data", data,
                "timestamp", System.currentTimeMillis()));
    }

    /** Returns true if at least one of the user's connections received the payload. */
    private boolean send(Channel channel, int userId, Object payload) {
        Set<Session> open = sessions.get(channel).get(userId);
        if (open == null) {
            return false;
        }
        String text = Json.GSON.toJson(payload);
        boolean delivered = false;
        for (Session session : open) {
            if (!session.isOpen()) {
                open.remove(session);
                continue;
            }
            // A WebSocket session allows one write at a time; concurrent sends would throw IllegalStateException.
            synchronized (session) {
                try {
                    session.getBasicRemote().sendText(text);
                    delivered = true;
                } catch (IOException | IllegalStateException e) {
                    LOG.log(Level.FINE, "Dropping broken WebSocket session for user " + userId, e);
                    open.remove(session);
                }
            }
        }
        return delivered;
    }
}
