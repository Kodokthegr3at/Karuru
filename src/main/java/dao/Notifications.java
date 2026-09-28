package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

import util.WebSocketManager;

public final class Notifications {

    private Notifications() {
    }

    /**
     * Stores a notification and pushes it to the user's open browser tabs over WebSocket.
     * The push happens immediately, before the caller commits; if the caller later rolls back,
     * the user may briefly see a notification that is not in their list.
     */
    public static void create(Connection conn, int userId, String type, String title, String message,
            String actionUrl, String referenceType, Integer referenceId) throws SQLException {
        String sql = "INSERT INTO notifications (user_id, type, title, message, action_url, reference_type, reference_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setString(2, type);
            stmt.setString(3, title);
            stmt.setString(4, message);
            stmt.setString(5, actionUrl);
            stmt.setString(6, referenceType);
            if (referenceId == null) {
                stmt.setNull(7, Types.INTEGER);
            } else {
                stmt.setInt(7, referenceId);
            }
            stmt.executeUpdate();
        }
        WebSocketManager.getInstance().sendNotification(userId, type, title, message, actionUrl);
    }
}
