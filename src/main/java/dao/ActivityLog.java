package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/** Audit trail in activity_logs. */
public final class ActivityLog {

    private ActivityLog() {
    }

    public static void log(Connection conn, int userId, String action, String entityType, Integer entityId,
            String detailsJson) throws SQLException {
        String sql = "INSERT INTO activity_logs (user_id, action, entity_type, entity_id, ip_address, details) "
                + "VALUES (?, ?, ?, ?, 'system', ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setString(2, action);
            stmt.setString(3, entityType);
            if (entityId == null) {
                stmt.setNull(4, Types.INTEGER);
            } else {
                stmt.setInt(4, entityId);
            }
            stmt.setString(5, detailsJson);
            stmt.executeUpdate();
        }
    }
}
