package servlet;

import java.io.IOException;
import java.sql.Connection;
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

/**
 * The user's product view history, read from activity_logs (action = 'product_view').
 * GET  ?action=getRecentlyViewed[&limit=n]
 * POST ?action=clearHistory
 */
@WebServlet("/RecentlyViewedServlet")
public class RecentlyViewedServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private static final String RECENT_SQL = """
            SELECT p.product_id, p.product_name, p.slug, p.price, p.image_url, p.status, p.stock_quantity,
                   p.condition, p.is_rental, p.views_count, p.likes_count, p.rating_avg,
                   u.username AS seller_username, MAX(al.created_at) AS last_viewed
            FROM activity_logs al
            JOIN products p ON al.entity_id = p.product_id
            JOIN users u ON p.user_id = u.user_id
            WHERE al.user_id = ? AND al.action = 'product_view' AND al.entity_type = 'product'
              AND p.status = 'available'
            GROUP BY p.product_id, p.product_name, p.slug, p.price, p.image_url, p.status, p.stock_quantity,
                     p.condition, p.is_rental, p.views_count, p.likes_count, p.rating_avg, u.username
            ORDER BY last_viewed DESC
            LIMIT ?
            """;

    public RecentlyViewedServlet() {
        route("GET", "getRecentlyViewed", Access.USER, this::list);
        route("POST", "clearHistory", Access.USER, this::clear);
    }

    private void list(Call call) throws IOException, SQLException {
        Integer limit = call.intParam("limit");
        int rows = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
        List<Map<String, Object>> products = findRecent(call.db(), call.userId(), rows);
        call.ok(Json.obj("success", true, "products", products, "count", products.size()));
    }

    private void clear(Call call) throws IOException, SQLException {
        int deleted = clearHistory(call.db(), call.userId());
        call.ok(Json.obj("success", true, "message", "閲覧履歴を削除しました", "deleted_count", deleted));
    }

    private List<Map<String, Object>> findRecent(Connection conn, int userId, int limit) throws SQLException {
        List<Map<String, Object>> products = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(RECENT_SQL)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int productId = rs.getInt("product_id");
                    String imageUrl = rs.getString("image_url");
                    if (imageUrl != null && imageUrl.isBlank()) {
                        imageUrl = null;
                    }
                    Timestamp viewed = rs.getTimestamp("last_viewed");
                    String lastViewed = viewed == null ? null : viewed.toInstant().toString();

                    Map<String, Object> product = Json.obj(
                            "product_id", productId,
                            "product_name", rs.getString("product_name"),
                            "slug", rs.getString("slug"),
                            "price", rs.getDouble("price"),
                            "image_url", imageUrl,
                            "status", rs.getString("status"),
                            "stock_quantity", rs.getInt("stock_quantity"),
                            "condition", rs.getString("condition"),
                            "is_rental", rs.getBoolean("is_rental"),
                            "views_count", rs.getInt("views_count"),
                            "likes_count", rs.getInt("likes_count"),
                            "rating_avg", rs.getDouble("rating_avg"),
                            "seller_username", rs.getString("seller_username"),
                            "last_viewed", lastViewed,
                            "viewed_at", lastViewed,
                            "created_at", lastViewed);

                    List<String> images = ProductImages.urls(conn, productId);
                    if (!images.isEmpty()) {
                        product.put("images", images);
                        if (imageUrl == null) {
                            product.put("image_url", images.get(0));
                        }
                    }
                    products.add(product);
                }
            }
        }
        return products;
    }

    private int clearHistory(Connection conn, int userId) throws SQLException {
        String sql = "DELETE FROM activity_logs WHERE user_id = ? AND action = 'product_view' AND entity_type = 'product'";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            return stmt.executeUpdate();
        }
    }
}
