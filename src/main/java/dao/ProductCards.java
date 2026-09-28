package dao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import util.Json;

/** The "product card" shown in listings (home, search results, favourites, my listings). */
public final class ProductCards {

    /** SELECT list for a card; use with {@link #FROM}. */
    public static final String COLUMNS = "p.product_id, p.product_name, p.description, p.price, p.original_price, "
            + "p.discount_percentage, p.stock_quantity, p.status, p.image_url, p.is_rental, p.condition, "
            + "p.rental_price_daily, p.rental_price_weekly, p.rental_price_monthly, p.is_negotiable, p.views_count, "
            + "p.likes_count, p.sold_count, p.rating_avg, p.rating_count, p.created_at, "
            + "p.user_id AS seller_id, u.username AS seller_name";

    public static final String FROM = " FROM products p LEFT JOIN users u ON p.user_id = u.user_id ";

    private static final Map<String, String[]> STATUS_LABELS = Map.of(
            "available", new String[] {"販売中", "success"},
            "sold", new String[] {"売り切れ", "danger"},
            "rented", new String[] {"レンタル中", "warning"},
            "reserved", new String[] {"予約中", "info"});

    private ProductCards() {
    }

    /** A card for the current row of a query selecting {@link #COLUMNS}, including its images. */
    public static Map<String, Object> toCard(Connection db, ResultSet rs) throws SQLException {
        int productId = rs.getInt("product_id");
        String status = rs.getString("status");
        int stock = rs.getInt("stock_quantity");
        String[] label = STATUS_LABELS.getOrDefault(status, new String[] {status, "secondary"});
        boolean available = isAvailable(status, stock);
        return Json.obj(
                "product_id", productId,
                "product_name", rs.getString("product_name"),
                "description", rs.getString("description"),
                "price", rs.getBigDecimal("price"),
                "original_price", rs.getBigDecimal("original_price"),
                "discount_percentage", rs.getInt("discount_percentage"),
                "stock_quantity", stock,
                "status", status,
                "status_text", label[0],
                "status_color", label[1],
                "is_available", available,
                "is_disabled", !available,
                "image_url", rs.getString("image_url"),
                "images", ProductImages.urls(db, productId),
                "is_rental", rs.getBoolean("is_rental"),
                "condition", rs.getString("condition"),
                "rental_price_daily", rs.getBigDecimal("rental_price_daily"),
                "rental_price_weekly", rs.getBigDecimal("rental_price_weekly"),
                "rental_price_monthly", rs.getBigDecimal("rental_price_monthly"),
                "is_negotiable", rs.getBoolean("is_negotiable"),
                "views_count", rs.getInt("views_count"),
                "likes_count", rs.getInt("likes_count"),
                "sold_count", rs.getInt("sold_count"),
                "rating_avg", rs.getDouble("rating_avg"),
                "rating_count", rs.getInt("rating_count"),
                "seller_id", rs.getInt("seller_id"),
                "seller_name", rs.getString("seller_name"),
                "created_at", rs.getTimestamp("created_at"));
    }

    /** Whether the product can be bought right now. */
    public static boolean isAvailable(String status, int stock) {
        return !"sold".equals(status) && !"rented".equals(status) && !"deleted".equals(status) && stock > 0;
    }
}
