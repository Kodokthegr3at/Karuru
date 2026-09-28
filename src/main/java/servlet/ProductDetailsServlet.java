package servlet;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.annotation.WebServlet;

import dao.ProductCards;
import util.Json;

/**
 * Read-only product page data.
 * ?id=N                                  → product, images, specs, categories, related products, reviews, stats
 * ?action=getReviews&productId=N|reviewId → approved reviews with their comments
 * Viewing a product records a view (activity log + views_count).
 */
@WebServlet("/ProductDetailsServlet")
public class ProductDetailsServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;

    private static final String PRODUCT_SQL = """
            SELECT p.product_id, p.product_name, p.slug, p.description, p.price, p.original_price, p.discount_percentage,
                   p.stock_quantity, p.min_order, p.weight, p.condition, p.is_rental, p.rental_price_daily,
                   p.rental_price_weekly, p.rental_price_monthly, p.rental_deposit, p.status, p.views_count,
                   p.likes_count, p.sold_count, p.rating_avg, p.rating_count, p.featured, p.featured_until, p.image_url,
                   p.is_negotiable, p.created_at, p.updated_at,
                   u.user_id AS seller_id, u.username AS seller_username, u.full_name AS seller_name,
                   u.avatar_url AS seller_avatar, u.bio AS seller_bio, u.is_verified AS seller_verified,
                   u.is_seller, u.created_at AS seller_joined_at
            FROM products p LEFT JOIN users u ON p.user_id = u.user_id
            WHERE p.product_id = ? AND p.status <> 'deleted'
            """;

    private static final String REVIEW_COLUMNS = """
            SELECT r.review_id, r.product_id, r.user_id, r.rating, r.review_text, r.is_verified_purchase,
                   r.seller_reply, r.seller_replied_at, r.helpful_count, r.comment_count, r.status, r.created_at,
                   r.updated_at, u.username, u.full_name, u.avatar_url
            FROM product_reviews r JOIN users u ON r.user_id = u.user_id
            """;

    public ProductDetailsServlet() {
        route("GET", null, Access.PUBLIC, this::details);
        route("GET", "getReviews", Access.PUBLIC, this::reviews);
    }

    private void details(Call call) throws IOException, SQLException {
        if (call.param("id") == null) {
            call.error(SC_BAD_REQUEST, "商品IDが必要です");
            return;
        }
        Integer productId = call.intParam("id");
        if (productId == null) {
            call.error(SC_BAD_REQUEST, "無効な商品ID形式です");
            return;
        }
        Connection db = call.db();
        Map<String, Object> product = product(db, productId);
        if (product == null) {
            call.error(SC_NOT_FOUND, "商品が見つかりません");
            return;
        }
        recordView(db, call.optUserId(), productId, call.request.getRemoteAddr());
        call.ok(Json.obj(
                "success", true,
                "product", product,
                "specifications", specifications(db, productId),
                "images", images(db, productId),
                "categories", categories(db, productId),
                "relatedProducts", related(db, productId),
                "reviews", reviewsOfProduct(db, productId),
                "reviewStats", reviewStats(db, productId),
                "sellerStats", sellerStats(db, (Integer) product.get("seller_id"))));
    }

    private void reviews(Call call) throws IOException, SQLException {
        Integer reviewId = call.intParam("reviewId");
        Integer productId = call.intParam("productId");
        if (reviewId != null) {
            List<Map<String, Object>> found = query(call.db(), REVIEW_COLUMNS + "WHERE r.review_id = ? AND r.status = 'approved'", reviewId);
            if (found.isEmpty()) {
                call.error(SC_NOT_FOUND, "レビューが見つかりません");
            } else {
                call.ok(Json.obj("success", true, "review", found.get(0)));
            }
        } else if (productId != null) {
            call.ok(Json.obj("success", true, "reviews", reviewsOfProduct(call.db(), productId)));
        } else {
            call.error(SC_BAD_REQUEST, "商品IDまたはレビューIDが必要です");
        }
    }

    private static Map<String, Object> product(Connection db, int productId) throws SQLException {
        try (PreparedStatement stmt = db.prepareStatement(PRODUCT_SQL)) {
            stmt.setInt(1, productId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return Json.obj(
                        "product_id", rs.getInt("product_id"),
                        "product_name", rs.getString("product_name"),
                        "slug", rs.getString("slug"),
                        "description", rs.getString("description"),
                        "price", rs.getBigDecimal("price"),
                        "original_price", rs.getBigDecimal("original_price"),
                        "discount_percentage", rs.getInt("discount_percentage"),
                        "stock_quantity", rs.getInt("stock_quantity"),
                        "min_order", rs.getInt("min_order"),
                        "weight", rs.getBigDecimal("weight"),
                        "condition", rs.getString("condition"),
                        "is_rental", rs.getBoolean("is_rental"),
                        "rental_price_daily", rs.getBigDecimal("rental_price_daily"),
                        "rental_price_weekly", rs.getBigDecimal("rental_price_weekly"),
                        "rental_price_monthly", rs.getBigDecimal("rental_price_monthly"),
                        "rental_deposit", rs.getBigDecimal("rental_deposit"),
                        "status", rs.getString("status"),
                        "views_count", rs.getInt("views_count"),
                        "likes_count", rs.getInt("likes_count"),
                        "sold_count", rs.getInt("sold_count"),
                        "rating_avg", rs.getDouble("rating_avg"),
                        "rating_count", rs.getInt("rating_count"),
                        "featured", rs.getBoolean("featured"),
                        "featured_until", rs.getTimestamp("featured_until"),
                        "image_url", rs.getString("image_url"),
                        "is_negotiable", rs.getBoolean("is_negotiable"),
                        "created_at", rs.getTimestamp("created_at"),
                        "updated_at", rs.getTimestamp("updated_at"),
                        "seller_id", rs.getInt("seller_id"),
                        "seller_name", rs.getString("seller_name"),
                        "seller_username", rs.getString("seller_username"),
                        "seller_avatar", rs.getString("seller_avatar"),
                        "seller_bio", rs.getString("seller_bio"),
                        "seller_verified", rs.getBoolean("seller_verified"),
                        "is_seller", rs.getBoolean("is_seller"),
                        "seller_joined_at", rs.getTimestamp("seller_joined_at"));
            }
        }
    }

    /** Verified purchases first, then the most helpful, then the newest. */
    private static List<Map<String, Object>> reviewsOfProduct(Connection db, int productId) throws SQLException {
        return query(db, REVIEW_COLUMNS + "WHERE r.product_id = ? AND r.status = 'approved' "
                + "ORDER BY r.is_verified_purchase DESC, r.helpful_count DESC, r.created_at DESC", productId);
    }

    private static List<Map<String, Object>> query(Connection db, String sql, int id) throws SQLException {
        List<Map<String, Object>> reviews = new ArrayList<>();
        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int reviewId = rs.getInt("review_id");
                    reviews.add(Json.obj(
                            "review_id", reviewId,
                            "product_id", rs.getInt("product_id"),
                            "user_id", rs.getInt("user_id"),
                            "rating", rs.getInt("rating"),
                            "review_text", rs.getString("review_text"),
                            "is_verified_purchase", rs.getBoolean("is_verified_purchase"),
                            "seller_reply", rs.getString("seller_reply"),
                            "seller_replied_at", rs.getTimestamp("seller_replied_at"),
                            "helpful_count", rs.getInt("helpful_count"),
                            "comment_count", rs.getInt("comment_count"),
                            "status", rs.getString("status"),
                            "created_at", rs.getTimestamp("created_at"),
                            "updated_at", rs.getTimestamp("updated_at"),
                            "reviewer", Json.obj(
                                    "user_id", rs.getInt("user_id"),
                                    "username", rs.getString("username"),
                                    "full_name", rs.getString("full_name"),
                                    "avatar_url", rs.getString("avatar_url")),
                            "comments", comments(db, reviewId)));
                }
            }
        }
        return reviews;
    }

    private static List<Map<String, Object>> comments(Connection db, int reviewId) throws SQLException {
        String sql = """
                SELECT c.comment_id, c.review_id, c.comment_text, c.parent_comment_id, c.status, c.created_at, c.updated_at,
                       u.user_id, u.username, u.full_name, u.avatar_url, u.user_id = p.user_id AS is_seller
                FROM review_comments c
                JOIN users u ON c.user_id = u.user_id
                JOIN product_reviews pr ON c.review_id = pr.review_id
                JOIN products p ON pr.product_id = p.product_id
                WHERE c.review_id = ? AND c.status = 'active'
                ORDER BY c.created_at
                """;
        List<Map<String, Object>> comments = new ArrayList<>();
        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            stmt.setInt(1, reviewId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    comments.add(Json.obj(
                            "comment_id", rs.getInt("comment_id"),
                            "review_id", rs.getInt("review_id"),
                            "comment_text", rs.getString("comment_text"),
                            "parent_comment_id", rs.getObject("parent_comment_id"),
                            "is_seller_reply", rs.getBoolean("is_seller"),
                            "status", rs.getString("status"),
                            "created_at", rs.getTimestamp("created_at"),
                            "updated_at", rs.getTimestamp("updated_at"),
                            "commenter", Json.obj(
                                    "user_id", rs.getInt("user_id"),
                                    "username", rs.getString("username"),
                                    "full_name", rs.getString("full_name"),
                                    "avatar_url", rs.getString("avatar_url"))));
                }
            }
        }
        return comments;
    }

    private static Map<String, Object> reviewStats(Connection db, int productId) throws SQLException {
        String sql = """
                SELECT COUNT(*) AS total, COALESCE(AVG(rating), 0) AS average,
                       COALESCE(SUM(rating = 5), 0) AS five, COALESCE(SUM(rating = 4), 0) AS four,
                       COALESCE(SUM(rating = 3), 0) AS three, COALESCE(SUM(rating = 2), 0) AS two,
                       COALESCE(SUM(rating = 1), 0) AS one, COALESCE(SUM(is_verified_purchase), 0) AS verified
                FROM product_reviews WHERE product_id = ? AND status = 'approved'
                """;
        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            stmt.setInt(1, productId);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return Json.obj(
                        "total", rs.getInt("total"),
                        "average", rs.getDouble("average"),
                        "fiveStar", rs.getInt("five"),
                        "fourStar", rs.getInt("four"),
                        "threeStar", rs.getInt("three"),
                        "twoStar", rs.getInt("two"),
                        "oneStar", rs.getInt("one"),
                        "verifiedPurchases", rs.getInt("verified"));
            }
        }
    }

    /** Real figures only: a seller without reviews has rating 0 and review_count 0. */
    private static Map<String, Object> sellerStats(Connection db, int sellerId) throws SQLException {
        String sql = "SELECT COALESCE(AVG(r.rating), 0) AS avg_rating, COUNT(r.review_id) AS review_count "
                + "FROM products p JOIN product_reviews r ON p.product_id = r.product_id AND r.status = 'approved' "
                + "WHERE p.user_id = ?";
        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            stmt.setInt(1, sellerId);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return Json.obj("avg_rating", rs.getDouble("avg_rating"), "review_count", rs.getInt("review_count"));
            }
        }
    }

    private static List<Map<String, Object>> specifications(Connection db, int productId) throws SQLException {
        String sql = "SELECT spec_id, spec_name, spec_value, display_order FROM product_specifications "
                + "WHERE product_id = ? ORDER BY display_order, spec_id";
        List<Map<String, Object>> specs = new ArrayList<>();
        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            stmt.setInt(1, productId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    specs.add(Json.obj(
                            "spec_id", rs.getInt("spec_id"),
                            "spec_name", rs.getString("spec_name"),
                            "spec_value", rs.getString("spec_value"),
                            "display_order", rs.getInt("display_order")));
                }
            }
        }
        return specs;
    }

    private static List<Map<String, Object>> images(Connection db, int productId) throws SQLException {
        String sql = "SELECT image_id, image_url, image_order, is_primary, created_at FROM product_images "
                + "WHERE product_id = ? ORDER BY is_primary DESC, image_order, image_id";
        List<Map<String, Object>> images = new ArrayList<>();
        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            stmt.setInt(1, productId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    images.add(Json.obj(
                            "image_id", rs.getInt("image_id"),
                            "image_url", rs.getString("image_url"),
                            "image_order", rs.getInt("image_order"),
                            "is_primary", rs.getBoolean("is_primary"),
                            "created_at", rs.getTimestamp("created_at")));
                }
            }
        }
        return images;
    }

    private static List<Map<String, Object>> categories(Connection db, int productId) throws SQLException {
        String sql = "SELECT c.category_id, c.category_name, c.slug, c.description, c.icon_url FROM categories c "
                + "JOIN product_categories pc ON c.category_id = pc.category_id WHERE pc.product_id = ? ORDER BY c.display_order";
        List<Map<String, Object>> categories = new ArrayList<>();
        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            stmt.setInt(1, productId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    categories.add(Json.obj(
                            "category_id", rs.getInt("category_id"),
                            "category_name", rs.getString("category_name"),
                            "slug", rs.getString("slug"),
                            "description", rs.getString("description"),
                            "icon_url", rs.getString("icon_url")));
                }
            }
        }
        return categories;
    }

    /** Up to 8 other available products sharing a category, most viewed first. */
    private static List<Map<String, Object>> related(Connection db, int productId) throws SQLException {
        String sql = "SELECT " + ProductCards.COLUMNS + ProductCards.FROM + """
                WHERE p.product_id <> ? AND p.status = 'available'
                  AND EXISTS (SELECT 1 FROM product_categories mine JOIN product_categories theirs
                                ON mine.category_id = theirs.category_id
                              WHERE mine.product_id = ? AND theirs.product_id = p.product_id)
                ORDER BY p.views_count DESC, p.created_at DESC
                LIMIT 6
                """;
        List<Map<String, Object>> products = new ArrayList<>();
        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            stmt.setInt(1, productId);
            stmt.setInt(2, productId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    products.add(ProductCards.toCard(db, rs));
                }
            }
        }
        return products;
    }

    private static void recordView(Connection db, Integer userId, int productId, String ip) throws SQLException {
        try (PreparedStatement log = db.prepareStatement("INSERT INTO activity_logs (user_id, action, entity_type, entity_id, "
                + "ip_address) VALUES (?, 'product_view', 'product', ?, ?)")) {
            if (userId == null) {
                log.setNull(1, Types.INTEGER);
            } else {
                log.setInt(1, userId);
            }
            log.setInt(2, productId);
            log.setString(3, ip);
            log.executeUpdate();
        }
        try (PreparedStatement count = db.prepareStatement("UPDATE products SET views_count = views_count + 1 WHERE product_id = ?")) {
            count.setInt(1, productId);
            count.executeUpdate();
        }
    }
}
