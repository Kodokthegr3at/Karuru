package servlet;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Part;

import dao.Notifications;
import util.Json;
import util.Uploads;
import util.WebSocketManager;

/**
 * Direct messages between users, optionally about a product. Sending accepts a JSON/form body, or
 * multipart/form-data with one image in the "attachments" part.
 */
@WebServlet({"/MessagesServlet", "/Message"})
@MultipartConfig(maxFileSize = Uploads.MAX_IMAGE_BYTES, maxRequestSize = Uploads.MAX_IMAGE_BYTES + 1024 * 1024)
public class MessagesServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;

    /** Each conversation partner with the latest message and how many of their messages are unread. */
    private static final String CONVERSATIONS_SQL = """
            SELECT u.user_id AS other_user_id, u.username, u.full_name, u.avatar_url,
                   m.message_text AS last_message, m.sent_at AS last_message_time,
                   (SELECT COUNT(*) FROM messages unread
                     WHERE unread.sender_id = u.user_id AND unread.receiver_id = ? AND unread.is_read = 0) AS unread_count
            FROM (SELECT IF(sender_id = ?, receiver_id, sender_id) AS other_user_id, MAX(message_id) AS last_id
                  FROM messages WHERE sender_id = ? OR receiver_id = ?
                  GROUP BY other_user_id) latest
            JOIN messages m ON m.message_id = latest.last_id
            JOIN users u ON u.user_id = latest.other_user_id
            ORDER BY m.sent_at DESC, m.message_id DESC
            """;

    private static final String THREAD_SQL = """
            SELECT m.message_id, m.sender_id, m.receiver_id, m.product_id, m.message_text, m.attachment_url,
                   m.is_read, m.read_at, m.sent_at,
                   s.username AS sender_name, s.avatar_url AS sender_avatar_url,
                   r.username AS receiver_name, r.avatar_url AS receiver_avatar_url,
                   p.product_name, p.image_url AS product_image_url
            FROM messages m
            JOIN users s ON m.sender_id = s.user_id
            JOIN users r ON m.receiver_id = r.user_id
            LEFT JOIN products p ON m.product_id = p.product_id
            WHERE ((m.sender_id = ? AND m.receiver_id = ?) OR (m.sender_id = ? AND m.receiver_id = ?))
              AND (? IS NULL OR m.product_id = ?)
            ORDER BY m.sent_at, m.message_id
            """;

    public MessagesServlet() {
        route("GET", "getConversations", Access.USER, this::conversations);
        route("GET", "getMessages", Access.USER, this::thread);
        route("GET", "getUnreadCount", Access.USER, this::unreadCount);
        route("POST", "sendMessage", Access.USER, this::send);
        route("POST", "send", Access.USER, this::send);
        route("POST", "markAsRead", Access.USER, this::markAsRead);
    }

    private void unreadCount(Call call) throws IOException, SQLException {
        try (PreparedStatement stmt = call.db().prepareStatement(
                "SELECT COUNT(*) FROM messages WHERE receiver_id = ? AND is_read = 0")) {
            stmt.setInt(1, call.userId());
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                call.ok(Json.obj("success", true, "count", rs.getInt(1)));
            }
        }
    }

    private void conversations(Call call) throws IOException, SQLException {
        List<Map<String, Object>> conversations = new ArrayList<>();
        try (PreparedStatement stmt = call.db().prepareStatement(CONVERSATIONS_SQL)) {
            for (int i = 1; i <= 4; i++) {
                stmt.setInt(i, call.userId());
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String avatar = rs.getString("avatar_url");
                    conversations.add(Json.obj(
                            "other_user_id", rs.getInt("other_user_id"),
                            "other_user_name", rs.getString("username"),
                            "other_user_full_name", rs.getString("full_name"),
                            "other_user_avatar_url", avatar == null ? "" : avatar,
                            "last_message", rs.getString("last_message"),
                            "last_message_time", rs.getTimestamp("last_message_time"),
                            "unread_count", rs.getInt("unread_count")));
                }
            }
        }
        call.ok(Json.obj("success", true, "conversations", conversations));
    }

    private void thread(Call call) throws IOException, SQLException {
        Integer otherUserId = requireUser(call, "other_user_id", "相手ユーザーIDが必要です", "無効なユーザーIDです");
        if (otherUserId == null) {
            return;
        }
        Integer productId = call.intParam("product_id");
        List<Map<String, Object>> messages = new ArrayList<>();
        try (PreparedStatement stmt = call.db().prepareStatement(THREAD_SQL)) {
            stmt.setInt(1, call.userId());
            stmt.setInt(2, otherUserId);
            stmt.setInt(3, otherUserId);
            stmt.setInt(4, call.userId());
            setNullableInt(stmt, 5, productId);
            setNullableInt(stmt, 6, productId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    messages.add(Json.obj(
                            "message_id", rs.getInt("message_id"),
                            "sender_id", rs.getInt("sender_id"),
                            "receiver_id", rs.getInt("receiver_id"),
                            "product_id", rs.getObject("product_id"),
                            "message_text", rs.getString("message_text"),
                            "attachment_url", rs.getString("attachment_url"),
                            "is_read", rs.getBoolean("is_read"),
                            "read_at", rs.getTimestamp("read_at"),
                            "sent_at", rs.getTimestamp("sent_at"),
                            "sender_name", rs.getString("sender_name"),
                            "sender_avatar_url", rs.getString("sender_avatar_url"),
                            "receiver_name", rs.getString("receiver_name"),
                            "receiver_avatar_url", rs.getString("receiver_avatar_url"),
                            "product_name", rs.getString("product_name"),
                            "product_image_url", rs.getString("product_image_url")));
                }
            }
        }
        call.ok(Json.obj("success", true, "messages", messages));
    }

    private void send(Call call) throws IOException, SQLException {
        Integer receiverId = requireUser(call, "receiver_id", "受信者IDが必要です", "無効な受信者IDです");
        if (receiverId == null) {
            return;
        }
        Integer productId = call.intParam("product_id");
        String text = call.param("message_text");

        String attachmentUrl = null;
        Part attachment = attachmentPart(call);
        if (attachment != null) {
            try {
                attachmentUrl = Uploads.saveImage(attachment, "attachments");
            } catch (Uploads.RejectedUpload e) {
                call.error(SC_BAD_REQUEST, e.getMessage());
                return;
            }
        }
        if (text == null && attachmentUrl == null) {
            call.error(SC_BAD_REQUEST, "メッセージテキストまたは添付ファイルが必要です");
            return;
        }
        Connection db = call.beginTransaction();
        if (productId != null && !productBelongsToConversation(call, db, productId, receiverId)) {
            return;
        }

        String sql = "INSERT INTO messages (sender_id, receiver_id, product_id, message_text, attachment_url) VALUES (?, ?, ?, ?, ?)";
        int messageId;
        try (PreparedStatement stmt = db.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, call.userId());
            stmt.setInt(2, receiverId);
            setNullableInt(stmt, 3, productId);
            stmt.setString(4, text == null ? "" : text);
            stmt.setString(5, attachmentUrl);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                keys.next();
                messageId = keys.getInt(1);
            }
        }
        String senderName = username(db, call.userId());
        Notifications.create(db, receiverId, "message", "新しいメッセージ", senderName + "さんからメッセージが届きました",
                "messages.jsp?user_id=" + call.userId(), "user", call.userId());
        db.commit();

        WebSocketManager.getInstance().sendMessageNotification(receiverId, call.userId(), text == null ? "" : text, productId);
        call.ok(Json.obj("success", true, "message_id", messageId));
    }

    private void markAsRead(Call call) throws IOException, SQLException {
        Integer otherUserId = requireUser(call, "other_user_id", "相手ユーザーIDが必要です", "無効なユーザーIDです");
        if (otherUserId == null) {
            return;
        }
        Integer productId = call.intParam("product_id");
        String sql = "UPDATE messages SET is_read = 1, read_at = NOW() WHERE receiver_id = ? AND sender_id = ? AND is_read = 0"
                + (productId == null ? "" : " AND product_id = ?");
        int updated;
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            stmt.setInt(1, call.userId());
            stmt.setInt(2, otherUserId);
            if (productId != null) {
                stmt.setInt(3, productId);
            }
            updated = stmt.executeUpdate();
        }
        if (updated > 0) {
            WebSocketManager.getInstance().sendMessagesReadNotification(otherUserId, call.userId(), productId);
        }
        call.ok(Json.obj("success", true, "updated_count", updated));
    }

    /**
     * A product may be attached to a conversation when one side is its seller, or when it was already
     * discussed in this conversation. Sends the error response and returns false otherwise.
     */
    private static boolean productBelongsToConversation(Call call, Connection db, int productId, int receiverId)
            throws IOException, SQLException {
        String sql = """
                SELECT p.user_id AS seller_id,
                       EXISTS (SELECT 1 FROM messages
                               WHERE ((sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?))
                                 AND product_id = p.product_id) AS discussed
                FROM products p WHERE p.product_id = ?
                """;
        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            stmt.setInt(1, call.userId());
            stmt.setInt(2, receiverId);
            stmt.setInt(3, receiverId);
            stmt.setInt(4, call.userId());
            stmt.setInt(5, productId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    call.error(SC_NOT_FOUND, "商品が見つかりません");
                    return false;
                }
                int sellerId = rs.getInt("seller_id");
                if (sellerId != call.userId() && sellerId != receiverId && !rs.getBoolean("discussed")) {
                    call.error(SC_FORBIDDEN, "この商品はこの会話に関連していません");
                    return false;
                }
                return true;
            }
        }
    }

    /** The first image in the "attachments" part of a multipart request, or null. */
    private static Part attachmentPart(Call call) throws IOException {
        String type = call.request.getContentType();
        if (type == null || !type.toLowerCase().startsWith("multipart/form-data")) {
            return null;
        }
        try {
            for (Part part : call.request.getParts()) {
                if ("attachments".equals(part.getName()) && part.getSize() > 0) {
                    return part;
                }
            }
        } catch (ServletException | IllegalStateException e) {
            return null; // not multipart after all, or over the size limit
        }
        return null;
    }

    private static Integer requireUser(Call call, String param, String missing, String invalid) throws IOException {
        if (call.param(param) == null) {
            call.error(SC_BAD_REQUEST, missing);
            return null;
        }
        Integer id = call.intParam(param);
        if (id == null) {
            call.error(SC_BAD_REQUEST, invalid);
        }
        return id;
    }

    private static String username(Connection db, int userId) throws SQLException {
        try (PreparedStatement stmt = db.prepareStatement("SELECT username FROM users WHERE user_id = ?")) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString(1) : "ユーザー";
            }
        }
    }

    private static void setNullableInt(PreparedStatement stmt, int index, Integer value) throws SQLException {
        if (value == null) {
            stmt.setNull(index, Types.INTEGER);
        } else {
            stmt.setInt(index, value);
        }
    }
}
