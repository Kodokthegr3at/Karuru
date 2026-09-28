package servlet;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.servlet.annotation.WebServlet;

import dao.ProductCards;
import util.Json;

/**
 * Product search for products.jsp.
 * No action: filtered, paged product search (plus matching categories and sellers on the first page).
 * action=suggestions: autocomplete entries for the search box.
 */
@WebServlet("/SearchServlet")
public class SearchServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;
    private static final int PAGE_SIZE = 20;
    private static final int RELATED_LIMIT = 10;
    private static final int DEFAULT_SUGGESTIONS = 8;
    private static final int MAX_SUGGESTIONS = 20;

    private static final Map<String, String> SORT_ORDERS = Map.of(
            "price-low", "p.price ASC",
            "price-high", "p.price DESC",
            "popular", "(p.views_count + p.likes_count * 2) DESC",
            "newest", "p.created_at DESC");

    public SearchServlet() {
        for (String method : new String[] {"GET", "POST"}) {
            route(method, null, Access.PUBLIC, this::search);
            route(method, "suggestions", Access.PUBLIC, this::suggestions);
        }
    }

    /** WHERE clause and its parameters, shared by the result query and the count query. */
    private static final class ProductFilter {
        final StringBuilder where = new StringBuilder("WHERE p.status = 'available'");
        final List<Object> params = new ArrayList<>();

        void add(String condition, Object... values) {
            where.append(" AND ").append(condition);
            params.addAll(List.of(values));
        }

        int bind(PreparedStatement stmt) throws SQLException {
            int i = 1;
            for (Object value : params) {
                stmt.setObject(i++, value);
            }
            return i;
        }
    }

    private void search(Call call) throws IOException, SQLException {
        String query = call.param("query");
        Integer requestedPage = call.intParam("page");
        int page = requestedPage == null || requestedPage < 1 ? 1 : requestedPage;
        ProductFilter filter = buildFilter(call, query);
        String order = SORT_ORDERS.getOrDefault(call.param("sort", "newest"), SORT_ORDERS.get("newest"));

        String sql = "SELECT " + ProductCards.COLUMNS + ProductCards.FROM
                + filter.where + " ORDER BY " + order + " LIMIT ? OFFSET ?";
        Connection db = call.db();
        List<Map<String, Object>> products = new ArrayList<>();
        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            int i = filter.bind(stmt);
            stmt.setInt(i++, PAGE_SIZE);
            stmt.setInt(i, (page - 1) * PAGE_SIZE);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    products.add(ProductCards.toCard(db, rs));
                }
            }
        }

        int totalCount = 0;
        List<Map<String, Object>> categories = List.of();
        List<Map<String, Object>> sellers = List.of();
        if (page == 1) {
            try (PreparedStatement stmt = db.prepareStatement("SELECT COUNT(*) FROM products p " + filter.where)) {
                filter.bind(stmt);
                try (ResultSet rs = stmt.executeQuery()) {
                    rs.next();
                    totalCount = rs.getInt(1);
                }
            }
        }
        if (query != null) {
            categories = matchingCategories(db, query, RELATED_LIMIT);
            sellers = matchingSellers(db, query, RELATED_LIMIT);
        }
        call.ok(Json.obj(
                "products", products,
                "totalCount", totalCount,
                "page", page,
                "hasMore", products.size() >= PAGE_SIZE,
                "categories", categories,
                "sellers", sellers,
                "categoriesCount", categories.size(),
                "sellersCount", sellers.size()));
    }

    private static ProductFilter buildFilter(Call call, String query) {
        ProductFilter filter = new ProductFilter();
        if (query != null) {
            String pattern = likePattern(query);
            filter.add("(p.product_name LIKE ? OR p.description LIKE ?)", pattern, pattern);
        }
        String categories = call.param("categories");
        if (categories != null) {
            // EXISTS instead of a JOIN, so a product in two matching categories is listed once.
            List<String> ors = new ArrayList<>();
            List<Object> values = new ArrayList<>();
            for (String category : categories.split(",")) {
                String value = category.trim();
                if (value.matches("\\d+")) {
                    ors.add("c.category_id = ?");
                    values.add(Integer.valueOf(value));
                } else if (!value.isEmpty()) {
                    ors.add("c.category_name = ?");
                    values.add(value);
                }
            }
            if (!ors.isEmpty()) {
                filter.add("EXISTS (SELECT 1 FROM product_categories pc JOIN categories c ON pc.category_id = c.category_id "
                        + "WHERE pc.product_id = p.product_id AND (" + String.join(" OR ", ors) + "))", values.toArray());
            }
        }
        String price = call.param("price");
        if (price != null) {
            String[] bounds = price.split("-");
            try {
                if (bounds.length == 2 && bounds[1].equals("+")) {
                    filter.add("p.price >= ?", new BigDecimal(bounds[0]));
                } else if (bounds.length == 2) {
                    filter.add("p.price BETWEEN ? AND ?", new BigDecimal(bounds[0]), new BigDecimal(bounds[1]));
                }
            } catch (NumberFormatException e) {
                // an unreadable price range is ignored rather than failing the whole search
            }
        }
        String conditions = call.param("conditions");
        if (conditions != null) {
            List<Object> values = new ArrayList<>();
            for (String condition : conditions.split(",")) {
                values.add(normalizeCondition(condition.trim()));
            }
            filter.add("p.condition IN (" + String.join(",", Collections.nCopies(values.size(), "?")) + ")",
                    values.toArray());
        }
        if ("1".equals(call.param("rental")) || "true".equals(call.param("rental"))) {
            filter.add("p.is_rental = 1");
        }
        return filter;
    }

    private void suggestions(Call call) throws IOException, SQLException {
        String query = call.param("query");
        Integer requested = call.intParam("limit");
        int limit = requested == null ? DEFAULT_SUGGESTIONS : Math.max(1, Math.min(requested, MAX_SUGGESTIONS));
        List<Map<String, Object>> suggestions = new ArrayList<>();
        if (query == null || query.length() < 2) {
            call.ok(Json.obj("suggestions", suggestions));
            return;
        }
        Connection db = call.db();
        String type = call.param("type");
        if ("category".equals(type)) {
            addCategorySuggestions(suggestions, matchingCategories(db, query, limit), query);
        } else if ("seller".equals(type) || "user".equals(type)) {
            addSellerSuggestions(suggestions, matchingSellers(db, query, limit), query);
        } else {
            String sql = "SELECT product_id, product_name FROM products WHERE status = 'available' AND product_name LIKE ? "
                    + "ORDER BY views_count DESC, likes_count DESC, product_name LIMIT ?";
            try (PreparedStatement stmt = db.prepareStatement(sql)) {
                stmt.setString(1, likePattern(query));
                stmt.setInt(2, Math.max(3, limit / 3));
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString("product_name");
                        suggestions.add(Json.obj("type", "product", "text", name, "name", name,
                                "id", rs.getInt("product_id"), "highlight", highlight(name, query)));
                    }
                }
            }
            if (suggestions.size() < limit) {
                addCategorySuggestions(suggestions, matchingCategories(db, query, limit - suggestions.size()), query);
            }
            if (suggestions.size() < limit) {
                addSellerSuggestions(suggestions, matchingSellers(db, query, limit - suggestions.size()), query);
            }
        }
        call.ok(Json.obj("suggestions", suggestions));
    }

    private static void addCategorySuggestions(List<Map<String, Object>> out, List<Map<String, Object>> categories, String query) {
        for (Map<String, Object> category : categories) {
            String name = (String) category.get("category_name");
            out.add(Json.obj("type", "category", "text", name, "name", name, "id", category.get("category_id"),
                    "category_id", category.get("category_id"), "highlight", highlight(name, query)));
        }
    }

    private static void addSellerSuggestions(List<Map<String, Object>> out, List<Map<String, Object>> sellers, String query) {
        for (Map<String, Object> seller : sellers) {
            String name = (String) seller.get("username");
            out.add(Json.obj("type", "seller", "text", name, "name", name, "id", seller.get("user_id"),
                    "seller_id", seller.get("user_id"), "highlight", highlight(name, query)));
        }
    }

    private static List<Map<String, Object>> matchingCategories(Connection db, String query, int limit) throws SQLException {
        String sql = "SELECT category_id, category_name, description, image_url, icon_url FROM categories "
                + "WHERE is_active = 1 AND category_name LIKE ? ORDER BY display_order, category_name LIMIT ?";
        List<Map<String, Object>> categories = new ArrayList<>();
        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            stmt.setString(1, likePattern(query));
            stmt.setInt(2, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    categories.add(Json.obj(
                            "category_id", rs.getInt("category_id"),
                            "category_name", rs.getString("category_name"),
                            "description", rs.getString("description"),
                            "image_url", rs.getString("image_url"),
                            "icon_url", rs.getString("icon_url")));
                }
            }
        }
        return categories;
    }

    /** Sellers with available products whose username matches. Emails are never searchable. */
    private static List<Map<String, Object>> matchingSellers(Connection db, String query, int limit) throws SQLException {
        String sql = "SELECT u.user_id, u.username, COUNT(*) AS product_count FROM users u "
                + "JOIN products p ON u.user_id = p.user_id "
                + "WHERE p.status = 'available' AND u.deleted_at IS NULL AND u.username LIKE ? "
                + "GROUP BY u.user_id, u.username ORDER BY product_count DESC, u.username LIMIT ?";
        List<Map<String, Object>> sellers = new ArrayList<>();
        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            stmt.setString(1, likePattern(query));
            stmt.setInt(2, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    sellers.add(Json.obj(
                            "seller_id", rs.getInt("user_id"),
                            "user_id", rs.getInt("user_id"),
                            "username", rs.getString("username"),
                            "product_count", rs.getInt("product_count")));
                }
            }
        }
        return sellers;
    }

    /** "%query%" with LIKE wildcards in the user's text escaped, so "50%" searches for a literal percent sign. */
    private static String likePattern(String query) {
        return "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static String normalizeCondition(String condition) {
        return switch (condition.toLowerCase()) {
            case "like-new", "like_new" -> "like_new";
            case "acceptable", "fair" -> "fair";
            default -> condition.toLowerCase();
        };
    }

    /** The text with the first case-insensitive match wrapped in &lt;strong&gt;; everything else HTML-escaped. */
    private static String highlight(String text, String query) {
        int at = text.toLowerCase().indexOf(query.toLowerCase());
        if (at < 0) {
            return escapeHtml(text);
        }
        int end = at + query.length();
        return escapeHtml(text.substring(0, at)) + "<strong>" + escapeHtml(text.substring(at, end)) + "</strong>"
                + escapeHtml(text.substring(end));
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
