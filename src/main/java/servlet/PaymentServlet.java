package servlet;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import javax.servlet.annotation.WebServlet;

import dao.Orders;
import dao.Wallets;
import dao.Wallets.Entry;
import dao.Wallets.Wallet;
import dao.Notifications;
import util.Json;

/**
 * Order payment.
 * processPayment (buyer): wallet, credit_card and ewallet pay immediately; bank_transfer and cod stay pending.
 * confirmPayment (admin): marks a pending bank transfer / cash-on-delivery order as paid.
 *
 * credit_card and ewallet are simulated: there is no payment gateway yet, they always succeed.
 */
@WebServlet({"/Payment", "/PaymentServlet"})
public class PaymentServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;

    public PaymentServlet() {
        route("POST", "processPayment", Access.USER, this::pay);
        route("POST", "confirmPayment", Access.ADMIN, this::confirm);
    }

    private void pay(Call call) throws IOException, SQLException {
        String method = call.param("payment_method");
        if (call.param("order_id") == null || method == null) {
            call.error(SC_BAD_REQUEST, "注文IDと支払い方法が必要です");
            return;
        }
        Integer orderId = call.intParam("order_id");
        if (orderId == null) {
            call.error(SC_BAD_REQUEST, "無効な注文IDです");
            return;
        }
        Connection db = call.beginTransaction();

        // Locking the order row makes two concurrent payments for the same order run one after the other,
        // so the second one sees "paid" instead of charging the wallet again.
        String orderSql = "SELECT total_amount, payment_status, order_status FROM orders "
                + "WHERE order_id = ? AND user_id = ? FOR UPDATE";
        BigDecimal total;
        String orderStatus;
        try (PreparedStatement stmt = db.prepareStatement(orderSql)) {
            stmt.setInt(1, orderId);
            stmt.setInt(2, call.userId());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    call.error(SC_NOT_FOUND, "注文が見つかりません");
                    return;
                }
                if ("paid".equals(rs.getString("payment_status"))) {
                    call.error(SC_CONFLICT, "この注文は既に支払い済みです");
                    return;
                }
                total = rs.getBigDecimal("total_amount");
                orderStatus = rs.getString("order_status");
            }
        }

        if (!List.of("wallet", "credit_card", "ewallet", "bank_transfer", "cod").contains(method)) {
            call.error(SC_BAD_REQUEST, "無効な支払い方法です");
            return;
        }
        boolean paysNow = !method.equals("bank_transfer") && !method.equals("cod");
        // Stock is taken at the moment the order is paid; if someone else bought the last one first, nothing is charged.
        if (paysNow && !Orders.takeStock(db, orderId)) {
            call.error(SC_CONFLICT, "在庫が不足しているため支払いできません");
            return;
        }
        String paymentStatus;
        String transactionId = null;
        switch (method) {
            case "wallet" -> {
                Wallet wallet = Wallets.lock(db, call.userId());
                if (wallet.available().compareTo(total) < 0) {
                    call.error(SC_BAD_REQUEST, String.format(
                            "ウォレットの残高が不足しています。残高: ¥%,.0f、必要: ¥%,.0f。ウォレットにチャージしてください。",
                            wallet.available(), total));
                    return;
                }
                Wallets.debit(db, wallet, total, Entry.completed("purchase", "注文 #" + orderId + " の支払い", "order", orderId));
                paymentStatus = "paid";
            }
            case "credit_card", "ewallet" -> {
                paymentStatus = "paid";
                transactionId = "TXN-" + System.currentTimeMillis();
            }
            default -> paymentStatus = "pending"; // bank_transfer, cod: an admin confirms once the money arrives
        }

        boolean paid = paymentStatus.equals("paid");
        String updateSql = "UPDATE orders SET payment_status = ?, payment_method = ?, "
                + "paid_at = IF(? = 'paid', NOW(), paid_at), "
                + "order_status = IF(? = 'paid', 'confirmed', order_status), updated_at = NOW() WHERE order_id = ?";
        try (PreparedStatement stmt = db.prepareStatement(updateSql)) {
            stmt.setString(1, paymentStatus);
            stmt.setString(2, method);
            stmt.setString(3, paymentStatus);
            stmt.setString(4, paymentStatus);
            stmt.setInt(5, orderId);
            stmt.executeUpdate();
        }
        if (paid) {
            confirmItemsAndNotifySellers(db, orderId, call.userId());
        }
        db.commit();

        Map<String, Object> result = Json.obj(
                "success", true,
                "message", paid ? "お支払いが完了しました" : "支払い処理が完了しました（確認待ち）",
                "order_id", orderId,
                "payment_status", paymentStatus,
                "order_status", paid ? "confirmed" : orderStatus);
        if (transactionId != null) {
            result.put("transaction_id", transactionId);
        }
        call.ok(result);
    }

    private void confirm(Call call) throws IOException, SQLException {
        if (call.param("order_id") == null) {
            call.error(SC_BAD_REQUEST, "注文IDが必要です");
            return;
        }
        Integer orderId = call.intParam("order_id");
        if (orderId == null) {
            call.error(SC_BAD_REQUEST, "無効な注文IDです");
            return;
        }
        Connection db = call.beginTransaction();
        String sql = "UPDATE orders SET payment_status = 'paid', order_status = 'confirmed', paid_at = NOW(), "
                + "updated_at = NOW() WHERE order_id = ? AND payment_status = 'pending'";
        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            stmt.setInt(1, orderId);
            if (stmt.executeUpdate() == 0) {
                call.error(SC_CONFLICT, "この注文は確認待ちではありません");
                return;
            }
        }
        if (!Orders.takeStock(db, orderId)) {
            call.error(SC_CONFLICT, "在庫が不足しているため確定できません");
            return;
        }
        Integer buyerId = null;
        try (PreparedStatement stmt = db.prepareStatement("SELECT user_id FROM orders WHERE order_id = ?")) {
            stmt.setInt(1, orderId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    buyerId = rs.getInt(1);
                }
            }
        }
        confirmItemsAndNotifySellers(db, orderId, buyerId);
        db.commit();
        call.ok(Json.obj("success", true, "message", "支払いが確認されました", "order_id", orderId));
    }

    private static void confirmItemsAndNotifySellers(Connection db, int orderId, Integer buyerId) throws SQLException {
        try (PreparedStatement stmt = db.prepareStatement("UPDATE order_items SET status = 'confirmed' WHERE order_id = ?")) {
            stmt.setInt(1, orderId);
            stmt.executeUpdate();
        }
        try (PreparedStatement stmt = db.prepareStatement("SELECT DISTINCT seller_id FROM order_items WHERE order_id = ?")) {
            stmt.setInt(1, orderId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int sellerId = rs.getInt(1);
                    if (buyerId == null || sellerId != buyerId) {
                        Notifications.create(db, sellerId, "order", "支払いが完了しました",
                                "注文 #" + orderId + " の支払いが完了しました。発送の準備をしてください。",
                                "order-detail.jsp?id=" + orderId, "order", orderId);
                    }
                }
            }
        }
    }
}
