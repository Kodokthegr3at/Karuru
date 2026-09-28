package dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import dao.Wallets.Entry;

/**
 * Stock and money movements of an order. Stock is taken when the order is paid and given back
 * (together with the money) when a paid order is cancelled.
 */
public final class Orders {

    private Orders() {
    }

    /**
     * Takes the stock for every item of the order. Returns false, leaving the caller to roll back,
     * when some product no longer has enough stock.
     */
    public static boolean takeStock(Connection conn, int orderId) throws SQLException {
        String sql = "UPDATE products p JOIN order_items oi ON oi.product_id = p.product_id "
                + "SET p.stock_quantity = p.stock_quantity - oi.quantity "
                + "WHERE oi.order_id = ? AND oi.item_id = ? AND p.stock_quantity >= oi.quantity";
        try (PreparedStatement items = conn.prepareStatement("SELECT item_id FROM order_items WHERE order_id = ?");
             PreparedStatement take = conn.prepareStatement(sql)) {
            items.setInt(1, orderId);
            try (ResultSet rs = items.executeQuery()) {
                while (rs.next()) {
                    take.setInt(1, orderId);
                    take.setInt(2, rs.getInt(1));
                    if (take.executeUpdate() == 0) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public static void releaseStock(Connection conn, int orderId) throws SQLException {
        String sql = "UPDATE products p JOIN order_items oi ON oi.product_id = p.product_id "
                + "SET p.stock_quantity = p.stock_quantity + oi.quantity WHERE oi.order_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, orderId);
            stmt.executeUpdate();
        }
    }

    /** Returns the order total to the buyer's wallet and marks the order refunded. */
    public static void refund(Connection conn, int orderId, int buyerId, BigDecimal total) throws SQLException {
        Wallets.Wallet wallet = Wallets.lock(conn, buyerId);
        Wallets.credit(conn, wallet, total, Entry.completed("refund", "注文 #" + orderId + " のキャンセル返金", "order", orderId));
        try (PreparedStatement stmt = conn.prepareStatement("UPDATE orders SET payment_status = 'refunded' WHERE order_id = ?")) {
            stmt.setInt(1, orderId);
            stmt.executeUpdate();
        }
    }
}
