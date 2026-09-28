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

import dao.ActivityLog;
import dao.ProductCards;
import util.Json;

/** The user's favourite products (heart button, favorites.jsp and the header badge). */
@WebServlet({"/FavoriteServlet", "/Favorite"})
public class FavoriteServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;


    public FavoriteServlet() {
        route("GET", "getFavorites", Access.PUBLIC, this::list);
        route("GET", "getCount", Access.PUBLIC, this::count);
        route("GET", "check", Access.PUBLIC, this::check);
        route("POST", "toggle", Access.USER, this::toggle);
        route("POST", "remove", Access.USER, this::remove);
    }

    /** JSON array of favourite products that can still be bought. Anonymous visitors get an empty list. */
    private void list(Call call) throws IOException, SQLException {
        List<Map<String, Object>> favorites = new ArrayList<>();
        if (call.optUserId() == null) {
            call.ok(favorites);
            return;
        }
        String sql = "SELECT " + ProductCards.COLUMNS + ", f.added_at AS favorite_date FROM user_favorites f "
                + "JOIN products p ON f.product_id = p.product_id LEFT JOIN users u ON p.user_id = u.user_id "
                + "WHERE f.user_id = ? AND p.status IN ('available', 'reserved') ORDER BY f.added_at DESC LIMIT 100";
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            stmt.setInt(1, call.userId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> product = ProductCards.toCard(call.db(), rs);
                    product.put("favorite_date", rs.getTimestamp("favorite_date"));
                    favorites.add(product);
                }
            }
        }
        call.ok(favorites);
    }

    private void count(Call call) throws IOException, SQLException {
        if (call.optUserId() == null) {
            call.ok(Json.obj("count", 0));
            return;
        }
        String sql = "SELECT COUNT(*) FROM user_favorites f JOIN products p ON f.product_id = p.product_id "
                + "WHERE f.user_id = ? AND p.status IN ('available', 'reserved')";
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            stmt.setInt(1, call.userId());
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                call.ok(Json.obj("count", rs.getInt(1)));
            }
        }
    }

    private void check(Call call) throws IOException, SQLException {
        Integer productId = productId(call);
        if (productId == null) {
            return;
        }
        boolean favorite = call.optUserId() != null && isFavorite(call.db(), call.userId(), productId);
        call.ok(Json.obj("isFavorite", favorite, "productId", productId));
    }

    private void toggle(Call call) throws IOException, SQLException {
        Integer productId = productId(call);
        if (productId == null) {
            return;
        }
        Connection db = call.beginTransaction();
        if (!productExists(db, productId)) {
            call.error(SC_NOT_FOUND, "商品が見つかりません");
            return;
        }
        // Delete first: if nothing was deleted it was not a favourite, so add it. Safe against double clicks.
        boolean removed = deleteFavorite(db, call.userId(), productId);
        boolean added = false;
        if (!removed) {
            try (PreparedStatement stmt = db.prepareStatement("INSERT IGNORE INTO user_favorites (user_id, product_id) VALUES (?, ?)")) {
                stmt.setInt(1, call.userId());
                stmt.setInt(2, productId);
                added = stmt.executeUpdate() == 1;
            }
            if (added) {
                changeLikes(db, productId, 1);
                ActivityLog.log(db, call.userId(), "favorite_add", "product", productId, "{\"action\":\"add\"}");
            }
        }
        int likes = likes(db, productId);
        db.commit();
        call.ok(Json.obj(
                "success", true,
                "isFavorite", !removed,
                "action", removed ? "removed" : "added",
                "message", removed ? "お気に入りから削除しました" : "お気に入りに追加しました",
                "likesCount", likes,
                "productId", productId));
    }

    private void remove(Call call) throws IOException, SQLException {
        Integer productId = productId(call);
        if (productId == null) {
            return;
        }
        Connection db = call.beginTransaction();
        if (!deleteFavorite(db, call.userId(), productId)) {
            call.error(SC_NOT_FOUND, "お気に入りに登録されていません");
            return;
        }
        db.commit();
        call.ok(Json.obj("success", true, "message", "お気に入りから削除しました"));
    }

    /** Removes the favourite and adjusts the like counter; false if it was not a favourite. */
    private static boolean deleteFavorite(Connection db, int userId, int productId) throws SQLException {
        try (PreparedStatement stmt = db.prepareStatement("DELETE FROM user_favorites WHERE user_id = ? AND product_id = ?")) {
            stmt.setInt(1, userId);
            stmt.setInt(2, productId);
            if (stmt.executeUpdate() == 0) {
                return false;
            }
        }
        changeLikes(db, productId, -1);
        ActivityLog.log(db, userId, "favorite_remove", "product", productId, "{\"action\":\"remove\"}");
        return true;
    }

    /** Accepts both spellings the pages use (productId, product_id). */
    private static Integer productId(Call call) throws IOException {
        String name = call.param("productId") != null ? "productId" : "product_id";
        if (call.param(name) == null) {
            call.error(SC_BAD_REQUEST, "Product ID is required");
            return null;
        }
        Integer id = call.intParam(name);
        if (id == null) {
            call.error(SC_BAD_REQUEST, "Invalid product ID format");
        }
        return id;
    }

    private static boolean isFavorite(Connection db, int userId, int productId) throws SQLException {
        try (PreparedStatement stmt = db.prepareStatement("SELECT 1 FROM user_favorites WHERE user_id = ? AND product_id = ?")) {
            stmt.setInt(1, userId);
            stmt.setInt(2, productId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean productExists(Connection db, int productId) throws SQLException {
        try (PreparedStatement stmt = db.prepareStatement("SELECT 1 FROM products WHERE product_id = ?")) {
            stmt.setInt(1, productId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void changeLikes(Connection db, int productId, int change) throws SQLException {
        try (PreparedStatement stmt = db.prepareStatement(
                "UPDATE products SET likes_count = GREATEST(0, likes_count + ?) WHERE product_id = ?")) {
            stmt.setInt(1, change);
            stmt.setInt(2, productId);
            stmt.executeUpdate();
        }
    }

    private static int likes(Connection db, int productId) throws SQLException {
        try (PreparedStatement stmt = db.prepareStatement("SELECT likes_count FROM products WHERE product_id = ?")) {
            stmt.setInt(1, productId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
