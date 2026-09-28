package servlet;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.annotation.WebServlet;

import dao.ProductImages;
import util.Json;

/** The buyer's "garage": products from their orders that are still active. */
@WebServlet("/GarageServlet")
public class GarageServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;

    private static final String PURCHASES_SQL = """
            SELECT DISTINCT p.product_id, p.product_name, p.slug, p.image_url, p.price, p.is_rental, p.condition,
                   oi.order_id, oi.quantity, oi.status AS order_item_status,
                   o.order_number, o.order_status, o.created_at AS purchase_date,
                   u.username AS seller_username
            FROM order_items oi
            JOIN orders o ON oi.order_id = o.order_id
            JOIN products p ON oi.product_id = p.product_id
            JOIN users u ON p.user_id = u.user_id
            WHERE o.user_id = ?
              AND oi.status IN ('pending', 'confirmed', 'processing', 'shipped', 'delivered')
            ORDER BY o.created_at DESC
            """;

    public GarageServlet() {
        route("GET", null, Access.USER, this::list);
    }

    private void list(Call call) throws IOException, SQLException {
        List<Map<String, Object>> products = new ArrayList<>();
        try (PreparedStatement stmt = call.db().prepareStatement(PURCHASES_SQL)) {
            stmt.setInt(1, call.userId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    products.add(toProduct(call, rs));
                }
            }
        }
        call.ok(Json.obj("success", true, "products", products, "count", products.size()));
    }

    private Map<String, Object> toProduct(Call call, ResultSet rs) throws SQLException {
        int productId = rs.getInt("product_id");
        String imageUrl = rs.getString("image_url");
        if (imageUrl != null && imageUrl.isBlank()) {
            imageUrl = null;
        }
        Timestamp purchased = rs.getTimestamp("purchase_date");
        String purchaseDate = purchased == null ? null : purchased.toInstant().toString();

        Map<String, Object> product = Json.obj(
                "product_id", productId,
                "product_name", rs.getString("product_name"),
                "slug", rs.getString("slug"),
                "image_url", imageUrl,
                "price", rs.getDouble("price"),
                "is_rental", rs.getBoolean("is_rental"),
                "condition", rs.getString("condition"),
                "order_id", rs.getInt("order_id"),
                "order_number", rs.getString("order_number"),
                "quantity", rs.getInt("quantity"),
                "order_item_status", rs.getString("order_item_status"),
                "order_status", rs.getString("order_status"),
                "purchase_date", purchaseDate,
                "created_at", purchaseDate,
                "seller_username", rs.getString("seller_username"));

        List<String> images = ProductImages.urls(call.db(), productId);
        if (!images.isEmpty()) {
            product.put("images", images);
            if (imageUrl == null) {
                product.put("image_url", images.get(0));
            }
        }
        return product;
    }
}
