package servlet;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Map;

import javax.servlet.annotation.WebServlet;

import util.Json;

/** Product reviews. Only buyers with a non-pending, non-cancelled order for the product may review it. */
@WebServlet("/ReviewServlet")
public class ReviewServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;

    /** Picks the user's qualifying order for the product, preferring the order id given as the last parameter. */
    private static final String PURCHASE_SQL = """
            SELECT oi.order_id
            FROM order_items oi
            JOIN orders o ON oi.order_id = o.order_id
            WHERE oi.product_id = ? AND o.user_id = ? AND o.order_status NOT IN ('cancelled', 'pending')
            ORDER BY oi.order_id = ? DESC
            LIMIT 1
            """;

    public ReviewServlet() {
        route("GET", "getReviewFormData", Access.USER, this::formData);
        route("GET", "checkPurchaseStatus", Access.USER, this::purchaseStatus);
        route("POST", "submitReview", Access.USER, this::submit);
    }

    private void formData(Call call) throws IOException, SQLException {
        Integer productId = call.intParam("product_id");
        if (productId == null) {
            call.error(SC_BAD_REQUEST, "商品IDが必要です");
            return;
        }
        Integer orderId = call.intParam("order_id");
        Map<String, Object> product = findProduct(call.db(), productId);
        if (product == null) {
            call.error(SC_NOT_FOUND, "商品が見つかりません");
            return;
        }
        call.ok(Json.obj(
                "success", true,
                "product", product,
                "order_id", orderId,
                "has_review", hasReviewed(call.db(), productId, call.userId(), orderId)));
    }

    private void purchaseStatus(Call call) throws IOException, SQLException {
        Integer productId = call.intParam("product_id");
        if (productId == null) {
            call.error(SC_BAD_REQUEST, "商品IDが必要です");
            return;
        }
        boolean purchased = findPurchase(call.db(), productId, call.userId(), null) != null;
        call.ok(Json.obj("success", true, "has_purchased", purchased));
    }

    private void submit(Call call) throws IOException, SQLException {
        Integer productId = call.intParam("product_id");
        Integer rating = call.intParam("rating");
        Integer requestedOrderId = call.intParam("order_id");
        if (productId == null) {
            call.error(SC_BAD_REQUEST, "商品IDが必要です");
            return;
        }
        if (rating == null) {
            call.error(SC_BAD_REQUEST, "評価が必要です");
            return;
        }
        if (rating < 1 || rating > 5) {
            call.error(SC_BAD_REQUEST, "評価は1から5の間である必要があります");
            return;
        }
        Connection db = call.db();
        if (hasReviewed(db, productId, call.userId(), requestedOrderId)) {
            call.error(SC_CONFLICT, "この商品のレビューは既に投稿されています");
            return;
        }
        Integer orderId = findPurchase(db, productId, call.userId(), requestedOrderId);
        if (orderId == null) {
            call.error(SC_FORBIDDEN, "この商品を購入したユーザーのみレビューを投稿できます");
            return;
        }
        String sql = "INSERT INTO product_reviews (product_id, user_id, order_id, rating, review_text, is_verified_purchase, status) "
                + "VALUES (?, ?, ?, ?, ?, TRUE, 'pending')";
        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            stmt.setInt(1, productId);
            stmt.setInt(2, call.userId());
            stmt.setInt(3, orderId);
            stmt.setInt(4, rating);
            stmt.setString(5, call.param("review_text"));
            stmt.executeUpdate();
        }
        call.ok(Json.obj("success", true, "message", "レビューを投稿しました。承認後に公開されます。"));
    }

    private static Map<String, Object> findProduct(Connection db, int productId) throws SQLException {
        try (PreparedStatement stmt = db.prepareStatement(
                "SELECT product_id, product_name, image_url FROM products WHERE product_id = ?")) {
            stmt.setInt(1, productId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next()
                        ? Json.obj("product_id", rs.getInt("product_id"),
                                "product_name", rs.getString("product_name"),
                                "image_url", rs.getString("image_url"))
                        : null;
            }
        }
    }

    /** Whether the user already reviewed the product (for that order, when one is given). */
    private static boolean hasReviewed(Connection db, int productId, int userId, Integer orderId) throws SQLException {
        String sql = "SELECT 1 FROM product_reviews WHERE product_id = ? AND user_id = ?"
                + (orderId == null ? "" : " AND order_id = ?");
        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            stmt.setInt(1, productId);
            stmt.setInt(2, userId);
            if (orderId != null) {
                stmt.setInt(3, orderId);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** The order that entitles the user to review the product, or null if there is none. */
    private static Integer findPurchase(Connection db, int productId, int userId, Integer preferredOrderId)
            throws SQLException {
        try (PreparedStatement stmt = db.prepareStatement(PURCHASE_SQL)) {
            stmt.setInt(1, productId);
            stmt.setInt(2, userId);
            if (preferredOrderId == null) {
                stmt.setNull(3, Types.INTEGER);
            } else {
                stmt.setInt(3, preferredOrderId);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt("order_id") : null;
            }
        }
    }
}
