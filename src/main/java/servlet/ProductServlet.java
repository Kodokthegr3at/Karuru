package servlet;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.servlet.annotation.WebServlet;

import dao.ActivityLog;
import dao.ProductCards;
import util.Auth;
import util.Json;

/** Product listings for the home page, products.jsp and the seller's dashboard, plus deleting a listing. */
@WebServlet({"/ProductServlet", "/Product"})
public class ProductServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;
    private static final int PAGE_SIZE = 20;
    private static final int MAX_LIMIT = 50;

    private static final String BUYABLE = "p.status = 'available' AND p.stock_quantity > 0";

    private static final Map<String, String> SORT_ORDERS = Map.of(
            "price_low", "p.price ASC",
            "price_high", "p.price DESC",
            "popular", "p.views_count DESC, p.likes_count DESC",
            "recently_sold", "p.status = 'sold' DESC, p.updated_at DESC",
            "newest", "p.created_at DESC");

    /** Named price buckets accepted besides "min-max" and "min-+". */
    private static final Map<String, String> PRICE_BUCKETS = Map.of(
            "under50k", "p.price < 50000",
            "50k-100k", "p.price BETWEEN 50000 AND 100000",
            "100k-500k", "p.price BETWEEN 100000 AND 500000",
            "over500k", "p.price >= 500000");

    public ProductServlet() {
        route("GET", "getProducts", Access.PUBLIC, this::filtered);
        route("GET", "getAvailableProducts", Access.PUBLIC, this::available);
        route("GET", "getPopular", Access.PUBLIC, call -> ranked(call,
                "p.views_count DESC, p.likes_count DESC, p.created_at DESC", ""));
        route("GET", "getFeatured", Access.PUBLIC, call -> ranked(call,
                "p.rating_avg DESC, p.views_count DESC, p.created_at DESC",
                " AND (p.rating_avg >= 4.0 OR p.discount_percentage > 0 OR p.views_count > 100)"));
        route("GET", "getProductById", Access.PUBLIC, this::byId);
        route("GET", "getRelated", Access.PUBLIC, this::related);
        route("GET", "getUserProducts", Access.USER, this::mine);
        route("DELETE", "delete", Access.USER, this::delete);
    }

    /** products.jsp listing with category, price, condition, status and rental filters. */
    private void filtered(Call call) throws IOException, SQLException {
        List<String> where = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        boolean rentalOnly = "true".equals(call.param("is_rental"));
        String status = call.param("status");
        if (rentalOnly) {
            where.add("p.is_rental = 1 AND " + BUYABLE);
        } else if (status != null) {
            where.add("p.status = ?");
            params.add(status);
        } else if ("true".equals(call.param("includeSold"))) {
            where.add("p.status IN ('available', 'reserved', 'sold', 'rented', 'inactive')");
        } else {
            where.add(BUYABLE);
        }

        String categories = call.param("categories");
        if (categories != null) {
            List<String> values = List.of(categories.split("\\s*,\\s*"));
            boolean byId = values.stream().allMatch(v -> v.matches("\\d+"));
            where.add("EXISTS (SELECT 1 FROM product_categories pc JOIN categories c ON pc.category_id = c.category_id "
                    + "WHERE pc.product_id = p.product_id AND " + (byId ? "c.category_id" : "c.slug") + " IN ("
                    + placeholders(values.size()) + "))");
            for (String value : values) {
                params.add(byId ? Integer.valueOf(value) : value);
            }
        }
        addPriceFilter(call.param("price"), where, params);
        String conditions = call.param("conditions");
        if (conditions != null) {
            List<String> values = List.of(conditions.split("\\s*,\\s*"));
            where.add("p.condition IN (" + placeholders(values.size()) + ")");
            params.addAll(values);
        }

        Integer page = call.intParam("page");
        String sql = "SELECT " + ProductCards.COLUMNS + ProductCards.FROM + "WHERE " + String.join(" AND ", where)
                + " ORDER BY " + SORT_ORDERS.getOrDefault(call.param("sort", "newest"), SORT_ORDERS.get("newest"))
                + " LIMIT ? OFFSET ?";
        params.add(PAGE_SIZE);
        params.add((page == null || page < 1 ? 0 : page - 1) * PAGE_SIZE);

        List<Map<String, Object>> products = cards(call.db(), sql, params);
        if (rentalOnly) {
            for (Map<String, Object> product : products) {
                addRentalStatus(call.db(), product);
            }
        }
        call.ok(Json.obj("success", true, "products", products, "count", products.size(), "total", products.size()));
    }

    /** JSON array of buyable products, paged. */
    private void available(Call call) throws IOException, SQLException {
        Integer page = call.intParam("page");
        int limit = limit(call, PAGE_SIZE);
        String sql = "SELECT " + ProductCards.COLUMNS + ProductCards.FROM + "WHERE " + BUYABLE
                + " ORDER BY " + SORT_ORDERS.getOrDefault(call.param("sort", "newest"), SORT_ORDERS.get("newest"))
                + " LIMIT ? OFFSET ?";
        call.ok(cards(call.db(), sql, List.of(limit, (page == null || page < 1 ? 0 : page - 1) * limit)));
    }

    /** JSON array of the top buyable products for a home page section. */
    private void ranked(Call call, String order, String extraCondition) throws IOException, SQLException {
        String sql = "SELECT " + ProductCards.COLUMNS + ProductCards.FROM + "WHERE " + BUYABLE + extraCondition
                + " ORDER BY " + order + " LIMIT ?";
        call.ok(cards(call.db(), sql, List.of(limit(call, 8))));
    }

    private void byId(Call call) throws IOException, SQLException {
        if (call.param("id") == null) {
            call.error(SC_BAD_REQUEST, "Product ID is required");
            return;
        }
        Integer productId = call.intParam("id");
        if (productId == null) {
            call.error(SC_BAD_REQUEST, "Invalid product ID format");
            return;
        }
        String sql = "SELECT " + ProductCards.COLUMNS + ", p.updated_at, u.full_name AS seller_full_name, "
                + "u.avatar_url AS seller_avatar, u.is_verified AS seller_verified, u.created_at AS seller_created_at"
                + ProductCards.FROM + "WHERE p.product_id = ? AND p.status <> 'deleted'";
        Connection db = call.db();
        Map<String, Object> product;
        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            stmt.setInt(1, productId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    call.error(SC_NOT_FOUND, "Product not found");
                    return;
                }
                product = ProductCards.toCard(db, rs);
                product.put("updated_at", rs.getTimestamp("updated_at"));
                product.put("seller_full_name", rs.getString("seller_full_name"));
                product.put("seller_avatar", rs.getString("seller_avatar"));
                product.put("seller_verified", rs.getBoolean("seller_verified"));
                product.put("seller_created_at", rs.getTimestamp("seller_created_at"));
            }
        }
        product.put("categories", categories(db, productId));
        if (call.optUserId() != null) {
            ActivityLog.log(db, call.optUserId(), "product_view", "product", productId, null);
        }
        call.ok(Json.obj("success", true, "product", product));
    }

    /** Other buyable products in the same categories. */
    private void related(Call call) throws IOException, SQLException {
        if (call.param("product_id") == null) {
            call.error(SC_BAD_REQUEST, "Product ID is required");
            return;
        }
        Integer productId = call.intParam("product_id");
        if (productId == null) {
            call.error(SC_BAD_REQUEST, "Invalid product ID format");
            return;
        }
        String sql = "SELECT " + ProductCards.COLUMNS + ProductCards.FROM + "WHERE " + BUYABLE + " AND p.product_id <> ? "
                + "AND EXISTS (SELECT 1 FROM product_categories mine JOIN product_categories theirs "
                + "ON mine.category_id = theirs.category_id WHERE mine.product_id = ? AND theirs.product_id = p.product_id) "
                + "ORDER BY p.views_count DESC, p.rating_avg DESC, p.created_at DESC LIMIT ?";
        List<Map<String, Object>> products = cards(call.db(), sql, List.of(productId, productId, limit(call, 4)));
        call.ok(Json.obj("success", true, "products", products));
    }

    /** The seller's own listings: available, reserved, rented, sold, then anything else. Deleted ones are hidden. */
    private void mine(Call call) throws IOException, SQLException {
        String sql = "SELECT " + ProductCards.COLUMNS + ProductCards.FROM
                + "WHERE p.user_id = ? AND p.status <> 'deleted' "
                + "ORDER BY COALESCE(NULLIF(FIELD(p.status, 'available', 'reserved', 'rented', 'sold'), 0), 5), p.created_at DESC";
        List<Map<String, Object>> products = cards(call.db(), sql, List.of(call.userId()));
        call.ok(Json.obj("success", true, "products", products, "count", products.size()));
    }

    /** Soft delete by the owner (or an admin/moderator). */
    private void delete(Call call) throws IOException, SQLException {
        Integer productId = call.intParam("id") != null ? call.intParam("id") : call.intParam("product_id");
        if (productId == null) {
            call.error(SC_BAD_REQUEST, "Product ID is required");
            return;
        }
        Integer ownerId = null;
        try (PreparedStatement stmt = call.db().prepareStatement(
                "SELECT user_id FROM products WHERE product_id = ? AND status <> 'deleted'")) {
            stmt.setInt(1, productId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    ownerId = rs.getInt(1);
                }
            }
        }
        if (ownerId == null) {
            call.error(SC_NOT_FOUND, "Product not found");
            return;
        }
        boolean staff = Auth.isAdmin(call.request) || "moderator".equals(call.request.getSession().getAttribute("role"));
        if (ownerId != call.userId() && !staff) {
            call.error(SC_FORBIDDEN, "You are not authorized to delete this product");
            return;
        }
        try (PreparedStatement stmt = call.db().prepareStatement("UPDATE products SET status = 'deleted' WHERE product_id = ?")) {
            stmt.setInt(1, productId);
            stmt.executeUpdate();
        }
        call.ok(Json.obj("success", true, "message", "Product deleted successfully"));
    }

    private static void addPriceFilter(String price, List<String> where, List<Object> params) {
        if (price == null) {
            return;
        }
        if (PRICE_BUCKETS.containsKey(price)) {
            where.add(PRICE_BUCKETS.get(price));
            return;
        }
        String[] bounds = price.split("\\s*-\\s*");
        try {
            if (bounds.length == 2 && bounds[1].equals("+")) {
                where.add("p.price >= ?");
                params.add(new BigDecimal(bounds[0]));
            } else if (bounds.length == 2) {
                where.add("p.price BETWEEN ? AND ?");
                params.add(new BigDecimal(bounds[0]));
                params.add(new BigDecimal(bounds[1]));
            }
        } catch (NumberFormatException e) {
            // an unreadable price range is ignored rather than failing the listing
        }
    }

    /** For rental listings: whether the product is currently out on an active rental, and until when. */
    private static void addRentalStatus(Connection db, Map<String, Object> product) throws SQLException {
        String sql = "SELECT end_date, status FROM rentals WHERE product_id = ? AND status IN ('pending', 'confirmed') "
                + "ORDER BY end_date DESC LIMIT 1";
        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            stmt.setInt(1, (Integer) product.get("product_id"));
            try (ResultSet rs = stmt.executeQuery()) {
                boolean rented = rs.next();
                product.put("is_currently_rented", rented);
                if (rented) {
                    Date end = rs.getDate("end_date");
                    product.put("rental_end_date", end);
                    product.put("rental_status", rs.getString("status"));
                }
            }
        }
    }

    private static List<Map<String, Object>> categories(Connection db, int productId) throws SQLException {
        String sql = "SELECT c.category_id, c.category_name, c.slug FROM categories c "
                + "JOIN product_categories pc ON c.category_id = pc.category_id WHERE pc.product_id = ?";
        List<Map<String, Object>> categories = new ArrayList<>();
        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            stmt.setInt(1, productId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    categories.add(Json.obj(
                            "category_id", rs.getInt("category_id"),
                            "category_name", rs.getString("category_name"),
                            "slug", rs.getString("slug")));
                }
            }
        }
        return categories;
    }

    private static List<Map<String, Object>> cards(Connection db, String sql, List<?> params) throws SQLException {
        List<Map<String, Object>> products = new ArrayList<>();
        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    products.add(ProductCards.toCard(db, rs));
                }
            }
        }
        return products;
    }

    private static int limit(Call call, int fallback) {
        Integer limit = call.intParam("limit");
        return limit == null ? fallback : Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    private static String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }
}
