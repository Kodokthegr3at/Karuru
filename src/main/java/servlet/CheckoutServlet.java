package servlet;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import javax.servlet.annotation.WebServlet;

import dao.Notifications;
import util.Json;

/**
 * Checkout: shows the price quote for the cart and turns the cart into an order.
 * The same {@link Quote} is used for the page and for the order, so the buyer is charged exactly what was shown.
 */
@WebServlet("/CheckoutServlet")
public class CheckoutServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;

    private static final BigDecimal FEE_RATE = new BigDecimal("0.03");
    private static final BigDecimal EXTRA_SELLER_SHIPPING = new BigDecimal("200");
    private static final Map<String, BigDecimal> DELIVERY_PRICES = Map.of(
            "standard", new BigDecimal("500"),
            "express", new BigDecimal("1200"),
            "same_day", new BigDecimal("2500"));
    private static final Map<String, String> DELIVERY_NAMES = Map.of(
            "standard", "標準配送", "express", "速達配送", "same_day", "当日配送");
    private static final Map<String, String> COURIER_NAMES = Map.of(
            "yamato", "ヤマト運輸", "sagawa", "佐川急便", "japan_post", "日本郵便");

    /** Cart rows the buyer may order: available products, or reserved ones reserved for this buyer by an accepted offer. */
    private static final String CART_SQL = """
            SELECT c.cart_id, c.product_id, c.quantity, c.price_snapshot,
                   p.product_name, p.image_url, p.stock_quantity, p.original_price, p.discount_percentage,
                   p.user_id AS seller_id, u.username AS seller_name, u.full_name AS seller_full_name
            FROM carts c
            JOIN products p ON c.product_id = p.product_id
            JOIN users u ON p.user_id = u.user_id
            WHERE c.user_id = ?
              AND (p.status = 'available'
                   OR (p.status = 'reserved' AND EXISTS (SELECT 1 FROM offers o WHERE o.product_id = p.product_id
                                                         AND o.buyer_id = c.user_id AND o.status = 'accepted')))
            ORDER BY c.added_at DESC
            """;

    private record Item(int cartId, int productId, String name, String imageUrl, int quantity, BigDecimal price,
            BigDecimal originalPrice, int discountPercentage, int stock, int sellerId, String sellerName,
            String sellerFullName) {
        BigDecimal subtotal() {
            return price.multiply(BigDecimal.valueOf(quantity));
        }

        BigDecimal savings() {
            return originalPrice != null && originalPrice.compareTo(price) > 0
                    ? originalPrice.subtract(price).multiply(BigDecimal.valueOf(quantity))
                    : BigDecimal.ZERO;
        }
    }

    /** Orderable items plus the ones skipped for lack of stock. */
    private record Cart(List<Item> items, List<String> stockIssues) {
    }

    /** All amounts in yen. savings is informational: prices already include discounts. */
    private record Quote(BigDecimal subtotal, BigDecimal shipping, BigDecimal fee, BigDecimal savings, BigDecimal total) {
        static Quote of(List<Item> items, BigDecimal deliveryPrice) {
            BigDecimal subtotal = items.stream().map(Item::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal savings = items.stream().map(Item::savings).reduce(BigDecimal.ZERO, BigDecimal::add);
            long sellers = items.stream().map(Item::sellerId).distinct().count();
            BigDecimal shipping = deliveryPrice.add(EXTRA_SELLER_SHIPPING.multiply(BigDecimal.valueOf(Math.max(0, sellers - 1))));
            BigDecimal fee = subtotal.multiply(FEE_RATE).setScale(0, RoundingMode.HALF_UP);
            return new Quote(subtotal, shipping, fee, savings, subtotal.add(shipping).add(fee));
        }
    }

    public CheckoutServlet() {
        route("GET", "getCheckoutData", Access.USER, this::quote);
        route("GET", "getUserAddresses", Access.USER, this::addresses);
        route("POST", "saveAddress", Access.USER, this::saveAddress);
        route("POST", "placeOrder", Access.USER, this::placeOrder);
    }

    private void quote(Call call) throws IOException, SQLException {
        BigDecimal deliveryPrice = DELIVERY_PRICES.get(deliveryMethod(call));
        if (deliveryPrice == null) {
            call.error(SC_BAD_REQUEST, "無効な配送方法です");
            return;
        }
        Cart cart = loadCart(call.db(), call.userId());
        if (cart.items().isEmpty()) {
            call.error(SC_BAD_REQUEST, emptyCartMessage(cart));
            return;
        }
        Quote quote = Quote.of(cart.items(), deliveryPrice);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Item item : cart.items()) {
            Map<String, Object> json = Json.obj(
                    "cart_id", item.cartId(),
                    "product_id", item.productId(),
                    "product_name", item.name(),
                    "image_url", item.imageUrl(),
                    "quantity", item.quantity(),
                    "price", item.price(),
                    "subtotal", item.subtotal(),
                    "seller_id", item.sellerId(),
                    "seller_name", item.sellerName(),
                    "seller_full_name", item.sellerFullName(),
                    "stock_quantity", item.stock());
            if (item.originalPrice() != null && item.originalPrice().signum() > 0) {
                json.put("original_price", item.originalPrice());
            }
            if (item.discountPercentage() > 0) {
                json.put("discount_percentage", item.discountPercentage());
            }
            items.add(json);
        }
        Map<String, Object> result = Json.obj(
                "success", true,
                "items", items,
                "subtotal", quote.subtotal(),
                "shipping", quote.shipping(),
                "fee", quote.fee(),
                "discount", quote.savings(),
                "total", quote.total());
        if (!cart.stockIssues().isEmpty()) {
            result.put("warning", "一部の商品が在庫不足のため注文から除外されました: " + String.join(", ", cart.stockIssues()));
        }
        call.ok(result);
    }

    private void addresses(Call call) throws IOException, SQLException {
        String sql = """
                SELECT address_id, COALESCE(address_label, '自宅') AS address_label, recipient_name, postal_code,
                       prefecture, city, address_line1, building_name, phone, is_default
                FROM user_addresses
                WHERE user_id = ?
                ORDER BY is_default DESC, created_at DESC
                """;
        List<Map<String, Object>> addresses = new ArrayList<>();
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            stmt.setInt(1, call.userId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    addresses.add(Json.obj(
                            "address_id", rs.getInt("address_id"),
                            "address_label", rs.getString("address_label"),
                            "full_name", rs.getString("recipient_name"),
                            "postal_code", rs.getString("postal_code"),
                            "prefecture", rs.getString("prefecture"),
                            "city", rs.getString("city"),
                            "address_line", rs.getString("address_line1"),
                            "building", rs.getString("building_name"),
                            "phone", rs.getString("phone"),
                            "is_default", rs.getBoolean("is_default")));
                }
            }
        }
        call.ok(Json.obj("success", true, "addresses", addresses));
    }

    private void saveAddress(Call call) throws IOException, SQLException {
        String name = call.param("full_name");
        String postalCode = call.param("postal_code");
        String prefecture = call.param("prefecture");
        String city = call.param("city");
        String line1 = call.param("address_line");
        String phone = call.param("phone");
        if (name == null || postalCode == null || prefecture == null || city == null || line1 == null || phone == null) {
            call.error(SC_BAD_REQUEST, "必須項目が入力されていません");
            return;
        }
        String label = call.param("address_label");
        boolean isDefault = "true".equals(call.param("is_default"));

        Connection db = call.beginTransaction();
        if (isDefault) {
            try (PreparedStatement stmt = db.prepareStatement("UPDATE user_addresses SET is_default = 0 WHERE user_id = ?")) {
                stmt.setInt(1, call.userId());
                stmt.executeUpdate();
            }
        }
        String sql = "INSERT INTO user_addresses (user_id, address_label, recipient_name, postal_code, prefecture, city, "
                + "address_line1, address_line2, building_name, phone, is_default, country) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '日本')";
        int addressId;
        try (PreparedStatement stmt = db.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, call.userId());
            stmt.setString(2, label == null ? "自宅" : label);
            stmt.setString(3, name);
            stmt.setString(4, postalCode);
            stmt.setString(5, prefecture);
            stmt.setString(6, city);
            stmt.setString(7, line1);
            stmt.setString(8, call.param("address_line2"));
            stmt.setString(9, call.param("building"));
            stmt.setString(10, phone);
            stmt.setBoolean(11, isDefault);
            stmt.executeUpdate();
            addressId = generatedKey(stmt);
        }
        db.commit();
        call.ok(Json.obj("success", true, "address_id", addressId, "message", "住所を保存しました"));
    }

    private void placeOrder(Call call) throws IOException, SQLException {
        String paymentMethod = call.param("payment_method");
        if (call.param("address_id") == null || paymentMethod == null) {
            call.error(SC_BAD_REQUEST, "配送先住所と支払い方法を選択してください");
            return;
        }
        Integer addressId = call.intParam("address_id");
        if (addressId == null) {
            call.error(SC_BAD_REQUEST, "無効な住所IDです");
            return;
        }
        String delivery = deliveryMethod(call);
        BigDecimal deliveryPrice = DELIVERY_PRICES.get(delivery);
        if (deliveryPrice == null) {
            call.error(SC_BAD_REQUEST, "無効な配送方法です");
            return;
        }
        Connection db = call.beginTransaction();
        if (!ownsAddress(db, call.userId(), addressId)) {
            call.error(SC_BAD_REQUEST, "無効な配送先住所です");
            return;
        }
        Cart cart = loadCart(db, call.userId());
        if (cart.items().isEmpty()) {
            call.error(SC_BAD_REQUEST, emptyCartMessage(cart));
            return;
        }
        Quote quote = Quote.of(cart.items(), deliveryPrice);
        String orderNumber = "ORD-" + System.currentTimeMillis() + "-" + ThreadLocalRandom.current().nextInt(1000, 10000);
        String courier = COURIER_NAMES.getOrDefault(call.param("courier", ""), "指定なし");
        String notes = "配送方法: " + DELIVERY_NAMES.get(delivery) + " / 配送業者: " + courier;

        int orderId = insertOrder(db, call.userId(), orderNumber, quote, paymentMethod, addressId, notes);
        insertItems(db, orderId, cart.items());
        notifySellers(db, cart.items(), call.userId(), orderId, orderNumber);
        try (PreparedStatement stmt = db.prepareStatement("DELETE FROM carts WHERE user_id = ?")) {
            stmt.setInt(1, call.userId());
            stmt.executeUpdate();
        }
        db.commit();

        Map<String, Object> result = Json.obj(
                "success", true,
                "order_id", orderId,
                "order_number", orderNumber,
                "total", quote.total(),
                "message", "注文が完了しました");
        if (!cart.stockIssues().isEmpty()) {
            result.put("warning", "一部の商品が在庫不足のため注文から除外されました: " + String.join(", ", cart.stockIssues()));
        }
        call.ok(result);
    }

    private static String deliveryMethod(Call call) {
        String method = call.param("delivery_method");
        return method == null ? "standard" : method;
    }

    private static String emptyCartMessage(Cart cart) {
        return cart.stockIssues().isEmpty()
                ? "カートが空です"
                : "在庫不足の商品があります: " + String.join(", ", cart.stockIssues());
    }

    private static Cart loadCart(Connection db, int userId) throws SQLException {
        List<Item> items = new ArrayList<>();
        List<String> stockIssues = new ArrayList<>();
        try (PreparedStatement stmt = db.prepareStatement(CART_SQL)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int quantity = rs.getInt("quantity");
                    int stock = rs.getInt("stock_quantity");
                    String name = rs.getString("product_name");
                    if (stock < quantity) {
                        stockIssues.add(name + " (在庫: " + stock + "個, 注文数: " + quantity + "個)");
                        continue;
                    }
                    items.add(new Item(
                            rs.getInt("cart_id"), rs.getInt("product_id"), name, rs.getString("image_url"), quantity,
                            rs.getBigDecimal("price_snapshot"), rs.getBigDecimal("original_price"),
                            rs.getInt("discount_percentage"), stock, rs.getInt("seller_id"),
                            rs.getString("seller_name"), rs.getString("seller_full_name")));
                }
            }
        }
        return new Cart(items, stockIssues);
    }

    private static boolean ownsAddress(Connection db, int userId, int addressId) throws SQLException {
        try (PreparedStatement stmt = db.prepareStatement("SELECT 1 FROM user_addresses WHERE address_id = ? AND user_id = ?")) {
            stmt.setInt(1, addressId);
            stmt.setInt(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static int insertOrder(Connection db, int userId, String orderNumber, Quote quote, String paymentMethod,
            int addressId, String notes) throws SQLException {
        String sql = "INSERT INTO orders (user_id, order_number, subtotal, shipping_cost, discount_amount, tax_amount, "
                + "total_amount, payment_method, payment_status, order_status, shipping_address_id, notes) "
                + "VALUES (?, ?, ?, ?, 0, ?, ?, ?, 'pending', 'pending', ?, ?)";
        try (PreparedStatement stmt = db.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, userId);
            stmt.setString(2, orderNumber);
            stmt.setBigDecimal(3, quote.subtotal());
            stmt.setBigDecimal(4, quote.shipping());
            stmt.setBigDecimal(5, quote.fee());
            stmt.setBigDecimal(6, quote.total());
            stmt.setString(7, paymentMethod);
            stmt.setInt(8, addressId);
            stmt.setString(9, notes);
            stmt.executeUpdate();
            return generatedKey(stmt);
        }
    }

    private static void insertItems(Connection db, int orderId, List<Item> items) throws SQLException {
        String sql = "INSERT INTO order_items (order_id, product_id, seller_id, product_name, quantity, price, subtotal, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 'pending')";
        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            for (Item item : items) {
                stmt.setInt(1, orderId);
                stmt.setInt(2, item.productId());
                stmt.setInt(3, item.sellerId());
                stmt.setString(4, item.name());
                stmt.setInt(5, item.quantity());
                stmt.setBigDecimal(6, item.price());
                stmt.setBigDecimal(7, item.subtotal());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private static void notifySellers(Connection db, List<Item> items, int buyerId, int orderId, String orderNumber)
            throws SQLException {
        Set<Integer> notified = new HashSet<>();
        for (Item item : items) {
            if (item.sellerId() != buyerId && notified.add(item.sellerId())) {
                Notifications.create(db, item.sellerId(), "order", "新しい注文がありました",
                        "注文 #" + orderNumber + " が届きました。支払い待ちです。",
                        "order-detail.jsp?id=" + orderId, "order", orderId);
            }
        }
    }

    private static int generatedKey(Statement stmt) throws SQLException {
        try (ResultSet keys = stmt.getGeneratedKeys()) {
            keys.next();
            return keys.getInt(1);
        }
    }
}
