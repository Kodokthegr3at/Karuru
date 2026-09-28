package servlet;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.annotation.WebServlet;

import dao.ProductCards;
import util.Json;

/** Public seller page: profile stats plus the seller's 12 newest available products. */
@WebServlet("/SellerProfileServlet")
public class SellerProfileServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;

    private static final String SELLER_SQL = """
            SELECT u.user_id, u.username, u.full_name, u.avatar_url, u.bio, u.is_seller, u.is_verified, u.created_at,
                   COUNT(DISTINCT p.product_id) AS total_products,
                   COUNT(DISTINCT oi.order_id) AS total_sales,
                   AVG(pr.rating) AS avg_rating,
                   COUNT(DISTINCT pr.review_id) AS total_reviews
            FROM users u
            LEFT JOIN products p ON u.user_id = p.user_id
            LEFT JOIN order_items oi ON p.product_id = oi.product_id AND oi.status = 'delivered'
            LEFT JOIN product_reviews pr ON p.product_id = pr.product_id AND pr.status = 'approved'
            WHERE u.user_id = ? AND u.deleted_at IS NULL
            GROUP BY u.user_id, u.username, u.full_name, u.avatar_url, u.bio, u.is_seller, u.is_verified, u.created_at
            """;

    private static final String PRODUCTS_SQL = "SELECT " + ProductCards.COLUMNS + ProductCards.FROM
            + "WHERE p.user_id = ? AND p.status = 'available' ORDER BY p.created_at DESC LIMIT 24";

    public SellerProfileServlet() {
        route("GET", null, Access.PUBLIC, this::show);
    }

    private void show(Call call) throws IOException, SQLException {
        if (call.param("seller_id") == null) {
            call.error(SC_BAD_REQUEST, "出品者IDが必要です");
            return;
        }
        Integer sellerId = call.intParam("seller_id");
        if (sellerId == null) {
            call.error(SC_BAD_REQUEST, "無効な出品者IDです");
            return;
        }
        Map<String, Object> seller = findSeller(call.db(), sellerId);
        if (seller == null) {
            call.error(SC_NOT_FOUND, "出品者が見つかりません");
            return;
        }
        call.ok(Json.obj("success", true, "seller", seller, "products", findProducts(call.db(), sellerId)));
    }

    private Map<String, Object> findSeller(Connection conn, int sellerId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(SELLER_SQL)) {
            stmt.setInt(1, sellerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                // Email is deliberately never exposed on the public profile.
                return Json.obj(
                        "user_id", rs.getInt("user_id"),
                        "username", rs.getString("username"),
                        "full_name", rs.getString("full_name"),
                        "avatar_url", blankToNull(rs.getString("avatar_url")),
                        "bio", rs.getString("bio"),
                        "is_seller", rs.getBoolean("is_seller"),
                        "is_verified", rs.getBoolean("is_verified"),
                        "created_at", rs.getTimestamp("created_at"),
                        "total_products", rs.getLong("total_products"),
                        "total_sales", rs.getLong("total_sales"),
                        "avg_rating", rs.getDouble("avg_rating"),
                        "total_reviews", rs.getLong("total_reviews"));
            }
        }
    }

    private List<Map<String, Object>> findProducts(Connection conn, int sellerId) throws SQLException {
        List<Map<String, Object>> products = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(PRODUCTS_SQL)) {
            stmt.setInt(1, sellerId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    products.add(ProductCards.toCard(conn, rs));
                }
            }
        }
        return products;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
