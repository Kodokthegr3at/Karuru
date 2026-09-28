package websocket;

import java.util.List;

import javax.servlet.http.HttpSession;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;

import util.CsrfFilter;

/**
 * Identifies the WebSocket user from the logged-in HTTP session (never from the URL),
 * and only for same-origin handshakes. Endpoints read "user_id" from session.getUserProperties().
 */
public class SessionUserConfigurator extends ServerEndpointConfig.Configurator {

    @Override
    public void modifyHandshake(ServerEndpointConfig config, HandshakeRequest request, HandshakeResponse response) {
        HttpSession httpSession = (HttpSession) request.getHttpSession();
        if (httpSession == null
                || !CsrfFilter.isSameOrigin(first(request, "origin"), null, first(request, "host"))) {
            return;
        }
        Object userId = httpSession.getAttribute("user_id");
        if (userId instanceof Integer) {
            config.getUserProperties().put("user_id", userId);
        }
    }

    /** The user id stored during the handshake, or null for anonymous connections. */
    public static Integer userId(javax.websocket.Session session) {
        return (Integer) session.getUserProperties().get("user_id");
    }

    private static String first(HandshakeRequest request, String header) {
        for (var e : request.getHeaders().entrySet()) {
            if (e.getKey().equalsIgnoreCase(header)) {
                List<String> values = e.getValue();
                return values.isEmpty() ? null : values.get(0);
            }
        }
        return null;
    }
}
