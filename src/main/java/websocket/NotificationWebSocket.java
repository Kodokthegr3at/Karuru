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

import util.WebSocketManager;
import util.WebSocketManager.Channel;

/** Real-time notification channel. The client only sends keep-alive pings. */
@ServerEndpoint(value = "/notification-websocket", configurator = SessionUserConfigurator.class)
public class NotificationWebSocket {
    private static final Logger LOG = Logger.getLogger(NotificationWebSocket.class.getName());

    @OnOpen
    public void onOpen(Session session) throws IOException {
        Integer userId = SessionUserConfigurator.userId(session);
        if (userId == null) {
            session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "Login required"));
            return;
        }
        WebSocketManager.getInstance().add(Channel.NOTIFICATIONS, userId, session);
    }

    @OnMessage
    public void onMessage(String text, Session session) throws IOException {
        if (text.contains("\"ping\"")) {
            synchronized (session) {
                session.getBasicRemote().sendText("{\"type\":\"pong\"}");
            }
        }
    }

    @OnClose
    public void onClose(Session session) {
        WebSocketManager.getInstance().remove(Channel.NOTIFICATIONS, session);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        LOG.log(Level.FINE, "Notification WebSocket error", error);
        WebSocketManager.getInstance().remove(Channel.NOTIFICATIONS, session);
    }
}
