package servlet;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.annotation.WebServlet;

import dao.Notifications;
import dao.Orders;
import util.Json;

/**
 * Orders for buyers (order history, cancelling) and sellers (moving the order forward).
 *
 * Status flow: pending → confirmed (paid) → processing → shipped → delivered.
 * The buyer may cancel while pending; the seller may cancel until it ships.
 * Cancelling a paid order refunds the buyer's wallet and puts the stock back.
 */
@WebServlet("/OrderServlet")
public class OrderServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;

    /** Seller transitions: current status → allowed next statuses. */
    private static final Map<String, Set<String>> SELLER_TRANSITIONS = Map.of(
            "pending", Set.of("confirmed", "cancelled"),
            "confirmed", Set.of("processing", "cancelled"),
            "processing", Set.of("shipped", "cancelled"),
            "shipped", Set.of("delivered"));

    private static final String ORDER_COLUMNS = "o.order_id, o.user_id, o.order_number, o.total_amount, o.order_status, "
            + "o.payment_status, o.payment_method, o.created_at, o.shipped_at, o.delivered_at, o.tracking_number, o.courier";

    public OrderServlet() {
        route("GET", "getUserOrders", Access.USER, this::list);
        route("GET", "getOrderDetails", Access.USER, this::detail);
        route("GET", "getOrderByProduct", Access.USER, this::byProduct);
        route("POST", "updateOrderStatus", Access.USER, this::updateStatus);
        route("POST", "confirmOrder", Access.USER, this::confirm);
    }

    /** The order row the status actions need, read under a row lock. */
    private record Locked(int buyerId, String status, String paymentStatus, BigDecimal total, boolean isSeller) {
    }

    private void list(Call call) throws IOException, SQLException {
        String status = call.param("status");
        String sql = "SELECT " + ORDER_COLUMNS + ", (SELECT COUNT(*) FROM order_items oi WHERE oi.order_id = o.order_id) AS item_count "
                + "FROM orders o WHERE o.user_id = ?" + (status == null ? "" : " AND o.order_status = ?")
                + " ORDER BY o.created_at DESC, o.order_id DESC";
        List<Map<String, Object>> orders = new ArrayList<>();
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            stmt.setInt(1, call.userId());
            if (status != null) {
                stmt.setString(2, status.toLowerCase());
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> order = toOrder(rs);
                    order.put("item_count", rs.getInt("item_count"));
                    order.put("items", items(call.db(), rs.getInt("order_id")));
                    orders.add(order);
                }
            }
        }
        call.ok(Json.obj("success", true, "orders", orders, "count", orders.size()));
    }

    /** Visible to the buyer and to any seller with an item in the order. */
    private void detail(Call call) throws IOException, SQLException {
        Integer orderId = call.intParam("order_id") != null ? call.intParam("order_id") : call.intParam("orderId");
        if (orderId == null) {
            call.error(SC_BAD_REQUEST, "Order ID is required");
            return;
        }
        String sql = "SELECT " + ORDER_COLUMNS + ", o.notes, o.subtotal, o.shipping_cost, o.discount_amount, o.tax_amount, "
                + "a.recipient_name, a.phone, a.postal_code, a.prefecture, a.city, a.address_line1, a.address_line2, a.building_name "
                + "FROM orders o LEFT JOIN user_addresses a ON o.shipping_address_id = a.address_id "
                + "WHERE o.order_id = ? AND (o.user_id = ? OR EXISTS "
                + "(SELECT 1 FROM order_items oi WHERE oi.order_id = o.order_id AND oi.seller_id = ?))";
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            stmt.setInt(1, orderId);
            stmt.setInt(2, call.userId());
            stmt.setInt(3, call.userId());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    call.error(SC_NOT_FOUND, "注文が見つかりません");
                    return;
                }
                Map<String, Object> order = toOrder(rs);
                order.put("notes", rs.getString("notes"));
                order.put("subtotal", rs.getBigDecimal("subtotal"));
                order.put("shipping_cost", rs.getBigDecimal("shipping_cost"));
                order.put("discount_amount", rs.getBigDecimal("discount_amount"));
                order.put("tax_amount", rs.getBigDecimal("tax_amount"));
                order.put("shipping_address", Json.obj(
                        "recipient_name", rs.getString("recipient_name"),
                        "phone", rs.getString("phone"),
                        "postal_code", rs.getString("postal_code"),
                        "prefecture", rs.getString("prefecture"),
                        "city", rs.getString("city"),
                        "address_line1", rs.getString("address_line1"),
                        "address_line2", rs.getString("address_line2"),
                        "building_name", rs.getString("building_name")));
                order.put("items", items(call.db(), orderId));
                call.ok(order);
            }
        }
    }

    /** The buyer's latest order containing the product (used to jump from a chat to the order). */
    private void byProduct(Call call) throws IOException, SQLException {
        Integer productId = call.intParam("product_id");
        if (productId == null) {
            call.error(SC_BAD_REQUEST, "商品IDが必要です");
            return;
        }
        String sql = "SELECT o.order_id FROM orders o JOIN order_items oi ON o.order_id = oi.order_id "
                + "WHERE o.user_id = ? AND oi.product_id = ? ORDER BY o.created_at DESC LIMIT 1";
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            stmt.setInt(1, call.userId());
            stmt.setInt(2, productId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    call.ok(Json.obj("success", true, "order_id", rs.getInt(1)));
                } else {
                    call.error(SC_NOT_FOUND, "注文が見つかりません");
                }
            }
        }
    }

    private void updateStatus(Call call) throws IOException, SQLException {
        Integer orderId = call.intParam("order_id");
        String newStatus = call.param("status");
        if (orderId == null || newStatus == null) {
            call.error(SC_BAD_REQUEST, "注文IDとステータスが必要です");
            return;
        }
        newStatus = newStatus.toLowerCase();
        Connection db = call.beginTransaction();
        Locked order = lock(db, orderId, call.userId());
        if (order == null) {
            call.error(SC_NOT_FOUND, "注文が見つかりません");
            return;
        }
        boolean isBuyer = order.buyerId() == call.userId();
        if (!isBuyer && !order.isSeller()) {
            call.error(SC_FORBIDDEN, "この注文を更新する権限がありません");
            return;
        }
        boolean allowed = order.isSeller()
                ? SELLER_TRANSITIONS.getOrDefault(order.status(), Set.of()).contains(newStatus)
                : "pending".equals(order.status()) && "cancelled".equals(newStatus);
        if (!allowed) {
            call.error(SC_CONFLICT, "無効なステータス遷移です: " + order.status() + " → " + newStatus);
            return;
        }
        if (newStatus.equals("confirmed") && !"paid".equals(order.paymentStatus())) {
            call.error(SC_CONFLICT, "支払いが完了していない注文は確定できません");
            return;
        }

        String tracking = call.param("tracking_number");
        String courier = call.param("courier");
        String sql = switch (newStatus) {
            case "shipped" -> "UPDATE orders SET order_status = ?, shipped_at = NOW(), "
                    + "tracking_number = COALESCE(?, tracking_number), courier = COALESCE(?, courier) WHERE order_id = ?";
            case "delivered" -> "UPDATE orders SET order_status = ?, delivered_at = NOW() WHERE order_id = ?";
            default -> "UPDATE orders SET order_status = ? WHERE order_id = ?";
        };
        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            int i = 1;
            stmt.setString(i++, newStatus);
            if (newStatus.equals("shipped")) {
                stmt.setString(i++, tracking);
                stmt.setString(i++, courier);
            }
            stmt.setInt(i, orderId);
            stmt.executeUpdate();
        }
        setItemStatus(db, orderId, newStatus);
        if (newStatus.equals("cancelled") && "paid".equals(order.paymentStatus())) {
            Orders.releaseStock(db, orderId);
            Orders.refund(db, orderId, order.buyerId(), order.total());
        }
        notifyStatus(db, orderId, order.buyerId(), newStatus, order.isSeller(), call.userId(), tracking);
        db.commit();
        call.ok(Json.obj("success", true, "message", "注文ステータスを更新しました", "order_id", orderId, "new_status", newStatus));
    }

    /** Seller confirms a paid order that is still pending (orders paid by bank transfer or cash on delivery). */
    private void confirm(Call call) throws IOException, SQLException {
        Integer orderId = call.intParam("order_id");
        if (orderId == null) {
            call.error(SC_BAD_REQUEST, "注文IDが必要です");
            return;
        }
        Connection db = call.beginTransaction();
        Locked order = lock(db, orderId, call.userId());
        if (order == null || !order.isSeller()) {
            call.error(SC_NOT_FOUND, "注文が見つかりません");
            return;
        }
        if (!"paid".equals(order.paymentStatus())) {
            call.error(SC_CONFLICT, "支払いが完了していない注文は確定できません");
            return;
        }
        if (!"pending".equals(order.status())) {
            call.error(SC_CONFLICT, "この注文は既に確定済みです");
            return;
        }
        try (PreparedStatement stmt = db.prepareStatement("UPDATE orders SET order_status = 'confirmed' WHERE order_id = ?")) {
            stmt.setInt(1, orderId);
            stmt.executeUpdate();
        }
        setItemStatus(db, orderId, "confirmed");
        Notifications.create(db, order.buyerId(), "order", "注文が確定しました", "注文 #" + orderId + " が売り手により確定しました。",
                "order-detail.jsp?id=" + orderId, "order", orderId);
        db.commit();
        call.ok(Json.obj("success", true, "message", "注文を確定しました", "order_id", orderId));
    }

    /** Locks the order row. Null when the order does not exist. */
    private static Locked lock(Connection db, int orderId, int userId) throws SQLException {
        String sql = "SELECT o.user_id, o.order_status, o.payment_status, o.total_amount, "
                + "EXISTS (SELECT 1 FROM order_items oi WHERE oi.order_id = o.order_id AND oi.seller_id = ?) AS is_seller "
                + "FROM orders o WHERE o.order_id = ? FOR UPDATE";
        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, orderId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new Locked(rs.getInt("user_id"), rs.getString("order_status").toLowerCase(),
                        rs.getString("payment_status").toLowerCase(), rs.getBigDecimal("total_amount"), rs.getBoolean("is_seller"));
            }
        }
    }

    private static void setItemStatus(Connection db, int orderId, String status) throws SQLException {
        try (PreparedStatement stmt = db.prepareStatement("UPDATE order_items SET status = ? WHERE order_id = ?")) {
            stmt.setString(1, status);
            stmt.setInt(2, orderId);
            stmt.executeUpdate();
        }
    }

    private static void notifyStatus(Connection db, int orderId, int buyerId, String status, boolean bySeller, int actorId,
            String tracking) throws SQLException {
        String link = "order-detail.jsp?id=" + orderId;
        if (bySeller) {
            String title = switch (status) {
                case "confirmed" -> "注文が確定しました";
                case "shipped" -> "商品が発送されました";
                case "delivered" -> "お届けが完了しました";
                case "cancelled" -> "注文がキャンセルされました";
                default -> null;
            };
            String message = switch (status) {
                case "confirmed" -> "注文 #" + orderId + " が売り手により確定しました。";
                case "shipped" -> "注文 #" + orderId + " の商品が発送されました。" + (tracking == null ? "" : "追跡番号: " + tracking);
                case "delivered" -> "注文 #" + orderId + " の商品がお届けされました。";
                case "cancelled" -> "注文 #" + orderId + " がキャンセルされました。";
                default -> null;
            };
            if (title != null) {
                Notifications.create(db, buyerId, "order", title, message, link, "order", orderId);
            }
        }
        if (status.equals("cancelled")) {
            try (PreparedStatement stmt = db.prepareStatement(
                    "SELECT DISTINCT seller_id FROM order_items WHERE order_id = ? AND seller_id <> ?")) {
                stmt.setInt(1, orderId);
                stmt.setInt(2, actorId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Notifications.create(db, rs.getInt(1), "order", "注文がキャンセルされました",
                                "注文 #" + orderId + " がキャンセルされました。", link, "order", orderId);
                    }
                }
            }
        }
    }

    private static List<Map<String, Object>> items(Connection db, int orderId) throws SQLException {
        String sql = "SELECT oi.item_id, oi.product_id, oi.product_name, oi.quantity, oi.price, oi.subtotal, oi.status, "
                + "oi.seller_id, p.image_url, p.is_rental FROM order_items oi LEFT JOIN products p ON oi.product_id = p.product_id "
                + "WHERE oi.order_id = ? ORDER BY oi.item_id";
        List<Map<String, Object>> items = new ArrayList<>();
        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            stmt.setInt(1, orderId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    items.add(Json.obj(
                            "item_id", rs.getInt("item_id"),
                            "product_id", rs.getInt("product_id"),
                            "product_name", rs.getString("product_name"),
                            "quantity", rs.getInt("quantity"),
                            "price", rs.getBigDecimal("price"),
                            "subtotal", rs.getBigDecimal("subtotal"),
                            "status", rs.getString("status"),
                            "seller_id", rs.getInt("seller_id"),
                            "image_url", rs.getString("image_url"),
                            "is_rental", rs.getBoolean("is_rental")));
                }
            }
        }
        return items;
    }

    private static Map<String, Object> toOrder(ResultSet rs) throws SQLException {
        return Json.obj(
                "order_id", rs.getInt("order_id"),
                "user_id", rs.getInt("user_id"),
                "order_number", rs.getString("order_number"),
                "total_amount", rs.getBigDecimal("total_amount"),
                "order_status", rs.getString("order_status"),
                "payment_status", rs.getString("payment_status"),
                "payment_method", rs.getString("payment_method"),
                "created_at", iso(rs.getTimestamp("created_at")),
                "shipped_at", iso(rs.getTimestamp("shipped_at")),
                "delivered_at", iso(rs.getTimestamp("delivered_at")),
                "tracking_number", rs.getString("tracking_number"),
                "courier", rs.getString("courier"));
    }

    private static String iso(Timestamp value) {
        return value == null ? null : value.toInstant().toString();
    }
}
