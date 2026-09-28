package servlet;

import java.io.IOException;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.annotation.WebServlet;

import util.Json;

/** The seller dashboard: listing/sales/order counters, daily sales for the last 30 selling days and received orders. */
@WebServlet({"/DashboardServlet", "/Dashboard"})
public class DashboardServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;

    /** Every "?" is the current user's id. */
    private static final String STATS_SQL = """
            SELECT
              (SELECT COUNT(*) FROM products WHERE user_id = ? AND status = 'available') AS active_listings,
              (SELECT COUNT(*) FROM products WHERE user_id = ?) AS total_products,
              (SELECT COUNT(*) FROM products WHERE user_id = ? AND status = 'sold') AS sold_products,
              (SELECT COALESCE(SUM(oi.subtotal), 0) FROM order_items oi JOIN orders o ON oi.order_id = o.order_id
                 WHERE oi.seller_id = ? AND o.payment_status = 'paid') AS total_sales,
              (SELECT COALESCE(SUM(oi.subtotal), 0) FROM order_items oi JOIN orders o ON oi.order_id = o.order_id
                 WHERE oi.seller_id = ? AND o.payment_status = 'paid'
                   AND oi.created_at >= DATE_FORMAT(CURRENT_DATE, '%Y-%m-01')) AS sales_this_month,
              (SELECT COALESCE(SUM(oi.subtotal), 0) FROM order_items oi JOIN orders o ON oi.order_id = o.order_id
                 WHERE oi.seller_id = ? AND o.payment_status = 'paid'
                   AND oi.created_at >= DATE_FORMAT(CURRENT_DATE - INTERVAL 1 MONTH, '%Y-%m-01')
                   AND oi.created_at < DATE_FORMAT(CURRENT_DATE, '%Y-%m-01')) AS sales_last_month,
              (SELECT COUNT(DISTINCT oi.order_id) FROM order_items oi WHERE oi.seller_id = ?) AS received_orders,
              (SELECT COUNT(DISTINCT o.order_id) FROM order_items oi JOIN orders o ON oi.order_id = o.order_id
                 WHERE oi.seller_id = ? AND (o.order_status IN ('confirmed', 'processing')
                   OR (o.order_status = 'pending' AND o.payment_status = 'paid'))) AS orders_to_handle,
              (SELECT COUNT(*) FROM messages WHERE receiver_id = ? AND is_read = 0) AS unread_messages
            """;

    private static final String DAILY_SALES_SQL = """
            SELECT DATE(oi.created_at) AS sale_date, COUNT(DISTINCT oi.order_id) AS order_count, SUM(oi.subtotal) AS revenue
            FROM order_items oi
            JOIN orders o ON oi.order_id = o.order_id
            WHERE oi.seller_id = ? AND o.payment_status = 'paid'
            GROUP BY DATE(oi.created_at)
            ORDER BY sale_date DESC
            LIMIT 30
            """;

    /** Orders containing the seller's items, with the seller's own share of each order. */
    private static final String SELLER_ORDERS_SQL = """
            SELECT o.order_id, o.order_number, o.order_status, o.payment_status, o.created_at,
                   u.username AS buyer_name, MIN(oi.product_name) AS first_item, COUNT(*) AS item_count,
                   SUM(oi.subtotal) AS seller_subtotal
            FROM order_items oi
            JOIN orders o ON oi.order_id = o.order_id
            LEFT JOIN users u ON o.user_id = u.user_id
            WHERE oi.seller_id = ?
            GROUP BY o.order_id, o.order_number, o.order_status, o.payment_status, o.created_at, u.username
            ORDER BY o.created_at DESC
            LIMIT 50
            """;

    public DashboardServlet() {
        route("GET", null, Access.USER, this::stats);
        route("GET", "getStats", Access.USER, this::stats);
        route("GET", "getSales", Access.USER, this::sales);
        route("GET", "getSellerOrders", Access.USER, this::sellerOrders);
    }

    private void stats(Call call) throws IOException, SQLException {
        try (PreparedStatement stmt = call.db().prepareStatement(STATS_SQL)) {
            for (int i = 1; i <= stmt.getParameterMetaData().getParameterCount(); i++) {
                stmt.setInt(i, call.userId());
            }
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                call.ok(Json.obj(
                        "success", true,
                        "activeListings", rs.getInt("active_listings"),
                        "totalProducts", rs.getInt("total_products"),
                        "soldProducts", rs.getInt("sold_products"),
                        "totalSales", rs.getDouble("total_sales"),
                        "salesThisMonth", rs.getDouble("sales_this_month"),
                        "salesLastMonth", rs.getDouble("sales_last_month"),
                        "receivedOrders", rs.getInt("received_orders"),
                        "ordersToHandle", rs.getInt("orders_to_handle"),
                        "unreadMessages", rs.getInt("unread_messages")));
            }
        }
    }

    private void sales(Call call) throws IOException, SQLException {
        List<Map<String, Object>> sales = new ArrayList<>();
        try (PreparedStatement stmt = call.db().prepareStatement(DAILY_SALES_SQL)) {
            stmt.setInt(1, call.userId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Date saleDate = rs.getDate("sale_date");
                    sales.add(Json.obj(
                            "date", saleDate == null ? null : saleDate.toString(),
                            "orderCount", rs.getInt("order_count"),
                            "revenue", rs.getDouble("revenue")));
                }
            }
        }
        call.ok(Json.obj("success", true, "sales", sales));
    }

    private void sellerOrders(Call call) throws IOException, SQLException {
        List<Map<String, Object>> orders = new ArrayList<>();
        try (PreparedStatement stmt = call.db().prepareStatement(SELLER_ORDERS_SQL)) {
            stmt.setInt(1, call.userId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    orders.add(Json.obj(
                            "order_id", rs.getInt("order_id"),
                            "order_number", rs.getString("order_number"),
                            "order_status", rs.getString("order_status"),
                            "payment_status", rs.getString("payment_status"),
                            "created_at", rs.getTimestamp("created_at"),
                            "buyer_name", rs.getString("buyer_name"),
                            "first_item", rs.getString("first_item"),
                            "item_count", rs.getInt("item_count"),
                            "seller_subtotal", rs.getBigDecimal("seller_subtotal")));
                }
            }
        }
        call.ok(Json.obj("success", true, "orders", orders));
    }
}