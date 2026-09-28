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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.annotation.WebServlet;

import com.google.gson.JsonObject;

import dao.ActivityLog;
import util.Json;

/**
 * Shopping cart. A cart row keeps the price at the time it was added (price_snapshot); for a product bought
 * through an accepted offer that is the offer price.
 */
@WebServlet({"/CartServlet", "/Cart"})
public class CartServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;
    private static final BigDecimal SHIPPING_ESTIMATE = new BigDecimal("500");

    /** Same visibility rule as checkout: available, or reserved for this buyer by an accepted offer. */
    private static final String ORDERABLE = """
            (p.status = 'available' OR (p.status = 'reserved' AND EXISTS (
                SELECT 1 FROM offers o WHERE o.product_id = p.product_id AND o.buyer_id = c.user_id AND o.status = 'accepted')))
            """;

    private static final String CART_SQL = """
            SELECT c.cart_id, c.quantity, c.price_snapshot, c.added_at,
                   p.product_id, p.product_name, p.description, p.price, p.original_price, p.discount_percentage,
                   p.stock_quantity, p.status, p.image_url, p.is_rental, p.condition,
                   p.rental_price_daily, p.rental_price_weekly, p.rental_price_monthly,
                   u.username AS seller_name, u.user_id AS seller_id
            FROM carts c
            JOIN products p ON c.product_id = p.product_id
            LEFT JOIN users u ON p.user_id = u.user_id
            WHERE c.user_id = ? AND """ + ORDERABLE + " ORDER BY c.added_at DESC";

    /** A cart row joined with its product's current stock and status. */
    private record CartRow(int cartId, int productId, int quantity, int stock, String status) {
    }

    public CartServlet() {
        route("GET", "getCart", Access.USER, this::show);
        route("GET", "getCartCount", Access.PUBLIC, this::count);
        route("POST", "add", Access.USER, this::add);
        route("POST", "update", Access.USER, this::update);
        route("POST", "remove", Access.USER, this::remove);
        route("POST", "clear", Access.USER, this::clear);
    }

    private void show(Call call) throws IOException, SQLException {
        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        try (PreparedStatement stmt = call.db().prepareStatement(CART_SQL)) {
            stmt.setInt(1, call.userId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    BigDecimal lineTotal = rs.getBigDecimal("price_snapshot").multiply(BigDecimal.valueOf(rs.getInt("quantity")));
                    subtotal = subtotal.add(lineTotal);
                    items.add(toItem(rs, lineTotal));
                }
            }
        }
        BigDecimal shipping = items.isEmpty() ? BigDecimal.ZERO : SHIPPING_ESTIMATE;
        call.ok(Json.obj(
                "items", items,
                "subtotal", subtotal,
                "shipping", shipping,
                "total", subtotal.add(shipping),
                "itemCount", items.size()));
    }

    /** Header badge. Anonymous visitors simply have an empty cart. */
    private void count(Call call) throws IOException, SQLException {
        if (call.optUserId() == null) {
            call.ok(Json.obj("count", 0, "cartCount", 0));
            return;
        }
        String sql = "SELECT COUNT(*) AS items, COALESCE(SUM(c.quantity), 0) AS units FROM carts c "
                + "JOIN products p ON c.product_id = p.product_id WHERE c.user_id = ? AND " + ORDERABLE;
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            stmt.setInt(1, call.userId());
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                call.ok(Json.obj("count", rs.getInt("items"), "cartCount", rs.getInt("units")));
            }
        }
    }

    private void add(Call call) throws IOException, SQLException {
        Integer quantity = call.param("quantity") == null ? Integer.valueOf(1) : call.intParam("quantity");
        if (quantity == null || quantity < 1) {
            call.error(SC_BAD_REQUEST, "数量は1以上である必要があります");
            return;
        }
        Connection db = call.beginTransaction();

        // With an accepted offer, the product and price both come from the offer, never from the request.
        int productId;
        BigDecimal price;
        int stock;
        if (call.param("offer_id") != null) {
            Integer offerId = call.intParam("offer_id");
            String sql = "SELECT o.product_id, o.offer_price, p.stock_quantity, p.status FROM offers o "
                    + "JOIN products p ON o.product_id = p.product_id "
                    + "WHERE o.offer_id = ? AND o.buyer_id = ? AND o.status = 'accepted'";
            try (PreparedStatement stmt = db.prepareStatement(sql)) {
                stmt.setInt(1, offerId == null ? -1 : offerId);
                stmt.setInt(2, call.userId());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        call.error(SC_NOT_FOUND, "オファーが見つからないか、承認されていません");
                        return;
                    }
                    String status = rs.getString("status");
                    if (!"reserved".equals(status) && !"available".equals(status)) {
                        call.error(SC_CONFLICT, "この商品は現在購入できません");
                        return;
                    }
                    productId = rs.getInt("product_id");
                    price = rs.getBigDecimal("offer_price");
                    stock = rs.getInt("stock_quantity");
                }
            }
            // The offer price replaces any earlier cart row for the same product.
            try (PreparedStatement stmt = db.prepareStatement("DELETE FROM carts WHERE user_id = ? AND product_id = ?")) {
                stmt.setInt(1, call.userId());
                stmt.setInt(2, productId);
                stmt.executeUpdate();
            }
        } else {
            Integer requested = call.intParam("productId") != null ? call.intParam("productId") : call.intParam("product_id");
            if (requested == null) {
                call.error(SC_BAD_REQUEST, "商品IDが必要です");
                return;
            }
            try (PreparedStatement stmt = db.prepareStatement(
                    "SELECT price, stock_quantity, status FROM products WHERE product_id = ?")) {
                stmt.setInt(1, requested);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        call.error(SC_NOT_FOUND, "商品が見つかりません");
                        return;
                    }
                    if (!"available".equals(rs.getString("status"))) {
                        call.error(SC_CONFLICT, "この商品は現在購入できません");
                        return;
                    }
                    productId = requested;
                    price = rs.getBigDecimal("price");
                    stock = rs.getInt("stock_quantity");
                }
            }
        }

        int existing = 0;
        try (PreparedStatement stmt = db.prepareStatement(
                "SELECT quantity FROM carts WHERE user_id = ? AND product_id = ? FOR UPDATE")) {
            stmt.setInt(1, call.userId());
            stmt.setInt(2, productId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    existing = rs.getInt(1);
                }
            }
        }
        int newQuantity = existing + quantity;
        if (newQuantity > stock) {
            call.error(SC_CONFLICT, "在庫が不足しています");
            return;
        }
        if (existing > 0) {
            try (PreparedStatement stmt = db.prepareStatement(
                    "UPDATE carts SET quantity = ?, updated_at = NOW() WHERE user_id = ? AND product_id = ?")) {
                stmt.setInt(1, newQuantity);
                stmt.setInt(2, call.userId());
                stmt.setInt(3, productId);
                stmt.executeUpdate();
            }
        } else {
            try (PreparedStatement stmt = db.prepareStatement(
                    "INSERT INTO carts (user_id, product_id, quantity, price_snapshot) VALUES (?, ?, ?, ?)")) {
                stmt.setInt(1, call.userId());
                stmt.setInt(2, productId);
                stmt.setInt(3, quantity);
                stmt.setBigDecimal(4, price);
                stmt.executeUpdate();
            }
        }
        logCart(db, call.userId(), productId, existing > 0 ? "update" : "add", newQuantity);
        db.commit();
        call.ok(Json.obj("success", true,
                "message", existing > 0 ? "カートを更新しました" : "カートに追加しました",
                "quantity", newQuantity));
    }

    private void update(Call call) throws IOException, SQLException {
        if (call.param("quantity") == null) {
            call.error(SC_BAD_REQUEST, "数量が必要です");
            return;
        }
        Integer quantity = call.intParam("quantity");
        if (quantity == null || quantity < 1) {
            call.error(SC_BAD_REQUEST, "数量は1以上である必要があります");
            return;
        }
        CartRow row = findRow(call);
        if (row == null) {
            return;
        }
        if (!"available".equals(row.status()) && !"reserved".equals(row.status())) {
            call.error(SC_CONFLICT, "この商品は現在購入できません");
            return;
        }
        if (quantity > row.stock()) {
            call.error(SC_CONFLICT, "在庫が不足しています");
            return;
        }
        try (PreparedStatement stmt = call.db().prepareStatement(
                "UPDATE carts SET quantity = ?, updated_at = NOW() WHERE cart_id = ? AND user_id = ?")) {
            stmt.setInt(1, quantity);
            stmt.setInt(2, row.cartId());
            stmt.setInt(3, call.userId());
            stmt.executeUpdate();
        }
        logCart(call.db(), call.userId(), row.productId(), "update", quantity);
        call.ok(Json.obj("success", true, "message", "カートを更新しました"));
    }

    private void remove(Call call) throws IOException, SQLException {
        CartRow row = findRow(call);
        if (row == null) {
            return;
        }
        try (PreparedStatement stmt = call.db().prepareStatement("DELETE FROM carts WHERE cart_id = ? AND user_id = ?")) {
            stmt.setInt(1, row.cartId());
            stmt.setInt(2, call.userId());
            stmt.executeUpdate();
        }
        logCart(call.db(), call.userId(), row.productId(), "remove", row.quantity());
        call.ok(Json.obj("success", true, "message", "カートから削除しました"));
    }

    private void clear(Call call) throws IOException, SQLException {
        Connection db = call.beginTransaction();
        List<int[]> removed = new ArrayList<>();
        try (PreparedStatement stmt = db.prepareStatement("SELECT product_id, quantity FROM carts WHERE user_id = ?")) {
            stmt.setInt(1, call.userId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    removed.add(new int[] {rs.getInt(1), rs.getInt(2)});
                }
            }
        }
        try (PreparedStatement stmt = db.prepareStatement("DELETE FROM carts WHERE user_id = ?")) {
            stmt.setInt(1, call.userId());
            stmt.executeUpdate();
        }
        for (int[] item : removed) {
            logCart(db, call.userId(), item[0], "remove", item[1]);
        }
        db.commit();
        call.ok(Json.obj("success", true,
                "message", removed.isEmpty() ? "カートは既に空です" : "カートを空にしました",
                "count", removed.size()));
    }

    /** The user's cart row, addressed by cartId or productId. Sends the error response and returns null if absent. */
    private static CartRow findRow(Call call) throws IOException, SQLException {
        if (call.param("cartId") == null && call.param("productId") == null) {
            call.error(SC_BAD_REQUEST, "カートIDまたは商品IDが必要です");
            return null;
        }
        Integer cartId = call.intParam("cartId");
        Integer productId = call.intParam("productId");
        if (cartId == null && productId == null) {
            call.error(SC_BAD_REQUEST, "無効な数値形式です");
            return null;
        }
        String key = cartId != null ? "c.cart_id" : "c.product_id";
        String sql = "SELECT c.cart_id, c.product_id, c.quantity, p.stock_quantity, p.status FROM carts c "
                + "JOIN products p ON c.product_id = p.product_id WHERE " + key + " = ? AND c.user_id = ?";
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            stmt.setInt(1, cartId != null ? cartId : productId);
            stmt.setInt(2, call.userId());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    call.error(SC_NOT_FOUND, "カートアイテムが見つかりません");
                    return null;
                }
                return new CartRow(rs.getInt("cart_id"), rs.getInt("product_id"), rs.getInt("quantity"),
                        rs.getInt("stock_quantity"), rs.getString("status"));
            }
        }
    }

    private static void logCart(Connection db, int userId, int productId, String action, int quantity) throws SQLException {
        JsonObject details = new JsonObject();
        details.addProperty("action", action);
        details.addProperty("product_id", productId);
        details.addProperty("quantity", quantity);
        ActivityLog.log(db, userId, "cart_" + action, "product", productId, details.toString());
    }

    private static Map<String, Object> toItem(ResultSet rs, BigDecimal lineTotal) throws SQLException {
        return Json.obj(
                "cart_id", rs.getInt("cart_id"),
                "quantity", rs.getInt("quantity"),
                "price_snapshot", rs.getBigDecimal("price_snapshot"),
                "subtotal", lineTotal,
                "added_at", rs.getTimestamp("added_at"),
                "product_id", rs.getInt("product_id"),
                "product_name", rs.getString("product_name"),
                "description", rs.getString("description"),
                "price", rs.getBigDecimal("price"),
                "original_price", rs.getBigDecimal("original_price"),
                "discount_percentage", rs.getInt("discount_percentage"),
                "stock_quantity", rs.getInt("stock_quantity"),
                "status", rs.getString("status"),
                "image_url", rs.getString("image_url"),
                "is_rental", rs.getBoolean("is_rental"),
                "condition", rs.getString("condition"),
                "rental_price_daily", rs.getBigDecimal("rental_price_daily"),
                "rental_price_weekly", rs.getBigDecimal("rental_price_weekly"),
                "rental_price_monthly", rs.getBigDecimal("rental_price_monthly"),
                "seller_name", rs.getString("seller_name"),
                "seller_id", rs.getInt("seller_id"));
    }
}
