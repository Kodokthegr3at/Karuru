package websocket;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import util.WebSocketManager;
import util.WebSocketManager.Channel;

/** Real-time chat channel: delivers new messages, read receipts and typing indicators. */
@ServerEndpoint(value = "/message-websocket", configurator = SessionUserConfigurator.class)
public class MessageWebSocket {
    private static final Logger LOG = Logger.getLogger(MessageWebSocket.class.getName());

    @OnOpen
    public void onOpen(Session session) throws IOException {
        Integer userId = SessionUserConfigurator.userId(session);
        if (userId == null) {
            session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "Login required"));
            return;
        }
        WebSocketManager.getInstance().add(Channel.MESSAGES, userId, session);
    }

    /** Client frames: {"type":"ping"} or {"type":"typing","receiverId":n,"typing":true|false}. */
    @OnMessage
    public void onMessage(String text, Session session) throws IOException {
        JsonObject frame;
        try {
            frame = JsonParser.parseString(text).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            return; // ignore malformed frames
        }
        String type = frame.has("type") ? frame.get("type").getAsString() : "";
        if (type.equals("ping")) {
            synchronized (session) {
                session.getBasicRemote().sendText("{\"type\":\"pong\"}");
            }
        } else if (type.equals("typing")) {
            Integer receiverId = intField(frame, "receiverId", "receiver_id");
            JsonElement typing = frame.has("typing") ? frame.get("typing") : frame.get("isTyping");
            Integer senderId = SessionUserConfigurator.userId(session);
            if (receiverId != null && typing != null && senderId != null) {
                WebSocketManager.getInstance().sendTypingIndicator(receiverId, senderId, null, typing.getAsBoolean());
            }
        }
    }

    @OnClose
    public void onClose(Session session) {
        WebSocketManager.getInstance().remove(Channel.MESSAGES, session);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        LOG.log(Level.FINE, "Message WebSocket error", error);
        WebSocketManager.getInstance().remove(Channel.MESSAGES, session);
    }

    private static Integer intField(JsonObject frame, String... names) {
        for (String name : names) {
            JsonElement value = frame.get(name);
            if (value != null && value.isJsonPrimitive()) {
                try {
                    return value.getAsInt();
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
