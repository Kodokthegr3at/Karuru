package servlet;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.annotation.WebServlet;

import util.Json;

/** The logged-in user's notification list (notifications.jsp) and header badge count. */
@WebServlet({"/NotificationsServlet", "/Notification"})
public class NotificationsServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    /** filter parameter → extra WHERE condition. Unknown filters show everything. */
    private static final Map<String, String> FILTERS = Map.of(
            "unread", " AND is_read = 0",
            "order", " AND type = 'order'",
            "message", " AND type = 'message'",
            "system", " AND type = 'system'",
            "review", " AND type = 'review'",
            "promotion", " AND type = 'promotion'");

    public NotificationsServlet() {
        for (String method : new String[] {"GET", "POST"}) {
            route(method, "getNotifications", Access.USER, this::list);
            route(method, "getUnreadCount", Access.USER, this::unreadCount);
        }
        route("POST", "markAsRead", Access.USER, this::markAsRead);
        route("POST", "markAllAsRead", Access.USER, this::markAllAsRead);
        route("POST", "deleteNotification", Access.USER, this::delete);
        route("POST", "delete", Access.USER, this::delete);
        route("POST", "clearAllNotifications", Access.USER, this::clearAll);
    }

    private void list(Call call) throws IOException, SQLException {
        Integer page = call.intParam("page");
        Integer size = call.intParam("pageSize");
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int offset = (page == null || page < 1 ? 0 : page - 1) * pageSize;

        String sql = "SELECT notification_id, type, title, message, action_url, reference_type, reference_id, "
                + "is_read, read_at, created_at FROM notifications WHERE user_id = ?"
                + FILTERS.getOrDefault(call.param("filter", "all"), "")
                + " ORDER BY created_at DESC LIMIT ? OFFSET ?";
        List<Map<String, Object>> notifications = new ArrayList<>();
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            stmt.setInt(1, call.userId());
            stmt.setInt(2, pageSize);
            stmt.setInt(3, offset);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    notifications.add(toNotification(rs));
                }
            }
        }
        call.ok(Json.obj(
                "success", true,
                "notifications", notifications,
                "hasMore", notifications.size() == pageSize,
                "unreadCount", unread(call.db(), call.userId())));
    }

    private void unreadCount(Call call) throws IOException, SQLException {
        call.ok(Json.obj("success", true, "count", unread(call.db(), call.userId())));
    }

    private void markAsRead(Call call) throws IOException, SQLException {
        Integer id = requireId(call);
        if (id == null) {
            return;
        }
        String sql = "UPDATE notifications SET is_read = 1, read_at = NOW() WHERE notification_id = ? AND user_id = ?";
        if (updateOwn(call, sql, id) == 0) {
            call.error(SC_NOT_FOUND, "通知が見つかりません");
            return;
        }
        call.ok(Json.obj("success", true, "unreadCount", unread(call.db(), call.userId())));
    }

    private void markAllAsRead(Call call) throws IOException, SQLException {
        int changed = updateAll(call, "UPDATE notifications SET is_read = 1, read_at = NOW() WHERE user_id = ? AND is_read = 0");
        call.ok(Json.obj("success", true, "unreadCount", 0, "message", "すべての通知を既読にしました", "affectedRows", changed));
    }

    private void delete(Call call) throws IOException, SQLException {
        Integer id = requireId(call);
        if (id == null) {
            return;
        }
        if (updateOwn(call, "DELETE FROM notifications WHERE notification_id = ? AND user_id = ?", id) == 0) {
            call.error(SC_NOT_FOUND, "通知が見つかりません");
            return;
        }
        call.ok(Json.obj("success", true, "unreadCount", unread(call.db(), call.userId()), "message", "通知を削除しました"));
    }

    private void clearAll(Call call) throws IOException, SQLException {
        int deleted = updateAll(call, "DELETE FROM notifications WHERE user_id = ?");
        call.ok(Json.obj("success", true, "unreadCount", 0, "message", "すべての通知を削除しました", "affectedRows", deleted));
    }

    private static Integer requireId(Call call) throws IOException {
        if (call.param("notification_id") == null) {
            call.error(SC_BAD_REQUEST, "通知IDが必要です");
            return null;
        }
        Integer id = call.intParam("notification_id");
        if (id == null) {
            call.error(SC_BAD_REQUEST, "無効な通知IDです");
        }
        return id;
    }

    /** Runs a statement of the form "... WHERE notification_id = ? AND user_id = ?". */
    private static int updateOwn(Call call, String sql, int notificationId) throws SQLException {
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            stmt.setInt(1, notificationId);
            stmt.setInt(2, call.userId());
            return stmt.executeUpdate();
        }
    }

    /** Runs a statement of the form "... WHERE user_id = ?". */
    private static int updateAll(Call call, String sql) throws SQLException {
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            stmt.setInt(1, call.userId());
            return stmt.executeUpdate();
        }
    }

    private static int unread(Connection db, int userId) throws SQLException {
        try (PreparedStatement stmt = db.prepareStatement("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = 0")) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static Map<String, Object> toNotification(ResultSet rs) throws SQLException {
        String type = rs.getString("type") == null ? "system" : rs.getString("type");
        String referenceType = rs.getString("reference_type");
        Integer referenceId = (Integer) rs.getObject("reference_id");
        Map<String, Object> notification = Json.obj(
                "notification_id", rs.getInt("notification_id"),
                "type", type,
                "title", rs.getString("title") == null ? "通知" : rs.getString("title"),
                "message", rs.getString("message") == null ? "" : rs.getString("message"),
                "action_url", link(type, referenceType, referenceId, rs.getString("action_url")),
                "reference_type", referenceType,
                "is_read", rs.getBoolean("is_read"),
                "read_at", iso(rs.getTimestamp("read_at")),
                "created_at", iso(rs.getTimestamp("created_at")));
        if (referenceId != null) {
            notification.put("reference_id", referenceId);
        }
        return notification;
    }

    /**
     * Where clicking the notification goes. Links are rebuilt from the reference so old rows with outdated URLs
     * still open the right page; notifications stored without a URL stay without one.
     */
    private static String link(String type, String referenceType, Integer referenceId, String storedUrl) {
        if (storedUrl == null) {
            return null;
        }
        boolean hasRef = referenceId != null && referenceId > 0;
        if (type.equals("message")) {
            return hasRef ? "messages.jsp?user_id=" + referenceId : "messages.jsp";
        }
        if (!hasRef) {
            return storedUrl;
        }
        if (type.equals("order")) {
            return "order-detail.jsp?id=" + referenceId;
        }
        if (type.equals("review") || "product".equals(referenceType)) {
            return "product-detail.jsp?id=" + referenceId + "#review";
        }
        if (type.equals("rental") || "rental".equals(referenceType)) {
            return "rental-detail.jsp?id=" + referenceId;
        }
        return storedUrl;
    }

    private static String iso(Timestamp value) {
        return value == null ? null : value.toInstant().toString();
    }
}
