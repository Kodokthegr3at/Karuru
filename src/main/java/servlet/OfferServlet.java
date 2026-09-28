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
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.annotation.WebServlet;

import dao.Notifications;
import util.Json;

/**
 * Price offers (値下げ交渉). A buyer offers a price; the seller accepts or rejects; the buyer may cancel while pending.
 * Accepting reserves the product for that buyer and rejects every other pending offer on it.
 */
@WebServlet({"/OfferServlet", "/Offer"})
public class OfferServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;

    public OfferServlet() {
        route("GET", "getOffers", Access.USER, call -> list(call, Side.RECEIVED));
        route("GET", "getMyOffers", Access.USER, call -> list(call, Side.SENT));
        route("POST", "create", Access.USER, this::create);
        route("POST", "accept", Access.USER, this::accept);
        route("POST", "reject", Access.USER, this::reject);
        route("POST", "cancel", Access.USER, this::cancel);
    }

    /** Offers the user received as seller, or sent as buyer; each row shows the other party. */
    private enum Side {
        RECEIVED("seller_id", "buyer_id", "buyer"),
        SENT("buyer_id", "seller_id", "seller");

        final String self;
        final String other;
        final String label;

        Side(String self, String other, String label) {
            this.self = self;
            this.other = other;
            this.label = label;
        }
    }

    private void list(Call call, Side side) throws IOException, SQLException {
        String sql = "SELECT o.offer_id, o.product_id, o.buyer_id, o.seller_id, o.offer_price, o.message, o.status, "
                + "o.created_at, o.updated_at, p.product_name, p.price AS product_price, p.image_url, "
                + "u.username, u.full_name, u.avatar_url "
                + "FROM offers o JOIN products p ON o.product_id = p.product_id JOIN users u ON o." + side.other + " = u.user_id "
                + "WHERE o." + side.self + " = ?";
        Integer productId = call.intParam("product_id");
        String status = call.param("status");
        if (side == Side.RECEIVED && productId != null) {
            sql += " AND o.product_id = ?";
        }
        if (status != null) {
            sql += " AND o.status = ?";
        }
        sql += " ORDER BY o.created_at DESC";

        List<Map<String, Object>> offers = new ArrayList<>();
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            int i = 1;
            stmt.setInt(i++, call.userId());
            if (side == Side.RECEIVED && productId != null) {
                stmt.setInt(i++, productId);
            }
            if (status != null) {
                stmt.setString(i, status);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    offers.add(Json.obj(
                            "offer_id", rs.getInt("offer_id"),
                            "product_id", rs.getInt("product_id"),
                            "buyer_id", rs.getInt("buyer_id"),
                            "seller_id", rs.getInt("seller_id"),
                            "offer_price", rs.getBigDecimal("offer_price"),
                            "message", rs.getString("message"),
                            "status", rs.getString("status"),
                            "created_at", rs.getTimestamp("created_at").toString(),
                            "updated_at", rs.getTimestamp("updated_at").toString(),
                            "product_name", rs.getString("product_name"),
                            "product_price", rs.getBigDecimal("product_price"),
                            "image_url", rs.getString("image_url"),
                            side.label + "_username", rs.getString("username"),
                            side.label + "_name", rs.getString("full_name"),
                            side.label + "_avatar", rs.getString("avatar_url")));
                }
            }
        }
        call.ok(Json.obj("success", true, "offers", offers));
    }

    private void create(Call call) throws IOException, SQLException {
        Integer productId = call.intParam("product_id");
        String rawPrice = call.param("offer_price");
        if (call.param("product_id") == null || rawPrice == null) {
            call.error(SC_BAD_REQUEST, "商品IDとオファー価格が必要です");
            return;
        }
        BigDecimal price;
        try {
            price = new BigDecimal(rawPrice);
        } catch (NumberFormatException e) {
            price = null;
        }
        if (productId == null || price == null) {
            call.error(SC_BAD_REQUEST, "無効な数値形式です");
            return;
        }
        if (price.signum() <= 0) {
            call.error(SC_BAD_REQUEST, "オファー価格は0より大きい必要があります");
            return;
        }
        Connection db = call.db();
        int sellerId;
        try (PreparedStatement stmt = db.prepareStatement(
                "SELECT user_id, status, price, is_negotiable FROM products WHERE product_id = ?")) {
            stmt.setInt(1, productId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    call.error(SC_NOT_FOUND, "商品が見つかりません");
                    return;
                }
                if (!"available".equals(rs.getString("status"))) {
                    call.error(SC_CONFLICT, "この商品は現在利用できません");
                    return;
                }
                if (!rs.getBoolean("is_negotiable")) {
                    call.error(SC_BAD_REQUEST, "この商品は値下げ交渉を受け付けていません");
                    return;
                }
                if (price.compareTo(rs.getBigDecimal("price")) >= 0) {
                    call.error(SC_BAD_REQUEST, "オファー価格は販売価格より低くしてください");
                    return;
                }
                sellerId = rs.getInt("user_id");
            }
        }
        if (sellerId == call.userId()) {
            call.error(SC_BAD_REQUEST, "自分の商品にはオファーできません");
            return;
        }
        try (PreparedStatement stmt = db.prepareStatement(
                "SELECT 1 FROM offers WHERE product_id = ? AND buyer_id = ? AND status = 'pending'")) {
            stmt.setInt(1, productId);
            stmt.setInt(2, call.userId());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    call.error(SC_CONFLICT, "既にこの商品に保留中のオファーがあります");
                    return;
                }
            }
        }
        String sql = "INSERT INTO offers (product_id, buyer_id, seller_id, offer_price, message, status) VALUES (?, ?, ?, ?, ?, 'pending')";
        int offerId;
        try (PreparedStatement stmt = db.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, productId);
            stmt.setInt(2, call.userId());
            stmt.setInt(3, sellerId);
            stmt.setBigDecimal(4, price);
            stmt.setString(5, call.param("message"));
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                keys.next();
                offerId = keys.getInt(1);
            }
        }
        call.ok(Json.obj("success", true, "message", "オファーを送信しました",
                "offer_id", offerId, "seller_id", sellerId, "product_id", productId));
    }

    private void accept(Call call) throws IOException, SQLException {
        Integer offerId = requireOfferId(call);
        if (offerId == null) {
            return;
        }
        Connection db = call.beginTransaction();
        String sql = "SELECT o.product_id, o.buyer_id, p.status FROM offers o JOIN products p ON o.product_id = p.product_id "
                + "WHERE o.offer_id = ? AND o.seller_id = ? AND o.status = 'pending' FOR UPDATE";
        int productId;
        int buyerId;
        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            stmt.setInt(1, offerId);
            stmt.setInt(2, call.userId());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    call.error(SC_NOT_FOUND, "オファーが見つからないか、既に処理されています");
                    return;
                }
                if (!"available".equals(rs.getString("status"))) {
                    call.error(SC_CONFLICT, "この商品は現在利用できません");
                    return;
                }
                productId = rs.getInt("product_id");
                buyerId = rs.getInt("buyer_id");
            }
        }
        execute(db, "UPDATE offers SET status = 'accepted' WHERE offer_id = ?", offerId);
        try (PreparedStatement stmt = db.prepareStatement(
                "UPDATE offers SET status = 'rejected' WHERE product_id = ? AND offer_id <> ? AND status = 'pending'")) {
            stmt.setInt(1, productId);
            stmt.setInt(2, offerId);
            stmt.executeUpdate();
        }
        execute(db, "UPDATE products SET status = 'reserved' WHERE product_id = ?", productId);
        Notifications.create(db, buyerId, "offer", "オファーが承認されました",
                "あなたのオファーが承認されました。購入を続けるにはオファー管理ページから「購入する」をクリックしてください。",
                "offers.jsp", "offer", offerId);
        db.commit();
        call.ok(Json.obj("success", true, "message", "オファーを受け入れました"));
    }

    private void reject(Call call) throws IOException, SQLException {
        changePending(call, "seller_id", "rejected", "オファーを拒否しました");
    }

    private void cancel(Call call) throws IOException, SQLException {
        changePending(call, "buyer_id", "cancelled", "オファーをキャンセルしました");
    }

    /** Moves a pending offer owned by the user (as seller or buyer) to a final status. */
    private static void changePending(Call call, String ownerColumn, String newStatus, String message)
            throws IOException, SQLException {
        Integer offerId = requireOfferId(call);
        if (offerId == null) {
            return;
        }
        String sql = "UPDATE offers SET status = ? WHERE offer_id = ? AND " + ownerColumn + " = ? AND status = 'pending'";
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            stmt.setString(1, newStatus);
            stmt.setInt(2, offerId);
            stmt.setInt(3, call.userId());
            if (stmt.executeUpdate() == 0) {
                call.error(SC_NOT_FOUND, "オファーが見つからないか、既に処理されています");
                return;
            }
        }
        call.ok(Json.obj("success", true, "message", message));
    }

    private static Integer requireOfferId(Call call) throws IOException {
        if (call.param("offer_id") == null) {
            call.error(SC_BAD_REQUEST, "オファーIDが必要です");
            return null;
        }
        Integer id = call.intParam("offer_id");
        if (id == null) {
            call.error(SC_BAD_REQUEST, "無効な数値形式です");
        }
        return id;
    }

    private static void execute(Connection db, String sql, int id) throws SQLException {
        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
    }
}
