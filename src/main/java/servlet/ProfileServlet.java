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

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Part;

import util.Json;
import util.Uploads;

/**
 * The user's own profile page (profile.jsp): profile fields, avatar, statistics and shipping addresses.
 * The email address is changed on the settings page, where the password is required.
 */
@WebServlet("/ProfileServlet")
@MultipartConfig(maxFileSize = Uploads.MAX_IMAGE_BYTES, maxRequestSize = Uploads.MAX_IMAGE_BYTES + 1024 * 1024)
public class ProfileServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;

    public ProfileServlet() {
        route("GET", "getProfile", Access.USER, this::profile);
        route("GET", "getAddresses", Access.USER, this::addresses);
        route("GET", "getUserAddresses", Access.USER, this::addresses);
        route("GET", "getUserStats", Access.USER, this::stats);
        route("GET", "getUserByUsername", Access.USER, this::byUsername);
        route("POST", "updateProfile", Access.USER, this::updateProfile);
        route("POST", "uploadAvatar", Access.USER, this::uploadAvatar);
        route("POST", "addAddress", Access.USER, this::addAddress);
        route("POST", "updateAddress", Access.USER, this::updateAddress);
        route("POST", "deleteAddress", Access.USER, this::deleteAddress);
        route("POST", "setDefaultAddress", Access.USER, this::setDefaultAddress);
    }

    private void profile(Call call) throws IOException, SQLException {
        String sql = """
                SELECT u.user_id, u.username, u.email, u.full_name, u.phone, u.avatar_url, u.bio, u.role,
                       u.is_verified, u.is_seller, u.created_at, u.last_login,
                       w.balance, w.frozen_balance, w.total_earned, w.total_spent,
                       (SELECT COUNT(*) FROM products p WHERE p.user_id = u.user_id) AS products_count
                FROM users u LEFT JOIN user_wallets w ON u.user_id = w.user_id
                WHERE u.user_id = ?
                """;
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            stmt.setInt(1, call.userId());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    call.error(SC_NOT_FOUND, "ユーザーが見つかりません");
                    return;
                }
                call.ok(Json.obj(
                        "success", true,
                        "user", Json.obj(
                                "user_id", rs.getInt("user_id"),
                                "username", rs.getString("username"),
                                "email", rs.getString("email"),
                                "full_name", rs.getString("full_name"),
                                "phone", rs.getString("phone"),
                                "avatar_url", rs.getString("avatar_url"),
                                "bio", rs.getString("bio"),
                                "role", rs.getString("role"),
                                "is_verified", rs.getBoolean("is_verified"),
                                "is_seller", rs.getBoolean("is_seller"),
                                "created_at", rs.getTimestamp("created_at"),
                                "last_login", rs.getTimestamp("last_login"),
                                "products_count", rs.getInt("products_count")),
                        "wallet", Json.obj(
                                "balance", rs.getBigDecimal("balance"),
                                "frozen_balance", rs.getBigDecimal("frozen_balance"),
                                "total_earned", rs.getBigDecimal("total_earned"),
                                "total_spent", rs.getBigDecimal("total_spent"))));
            }
        }
    }

    /**
     * Counters for the profile page. Each figure is its own subquery: joining products, orders and reviews
     * together would multiply the sums by the number of joined rows.
     */
    private void stats(Call call) throws IOException, SQLException {
        String sql = """
                SELECT
                  (SELECT COUNT(*) FROM products WHERE user_id = ?) AS total_products,
                  (SELECT COUNT(*) FROM orders WHERE user_id = ?) AS total_orders,
                  (SELECT COUNT(DISTINCT order_id) FROM order_items WHERE seller_id = ?) AS total_sales,
                  (SELECT COALESCE(SUM(total_amount), 0) FROM orders WHERE user_id = ? AND payment_status = 'paid') AS total_spent,
                  (SELECT COALESCE(SUM(oi.subtotal), 0) FROM order_items oi JOIN orders o ON oi.order_id = o.order_id
                     WHERE oi.seller_id = ? AND o.order_status = 'delivered') AS total_earned,
                  (SELECT COUNT(*) FROM product_reviews WHERE user_id = ? AND status = 'approved') AS total_reviews,
                  (SELECT COALESCE(AVG(rating), 0) FROM product_reviews WHERE user_id = ? AND status = 'approved') AS average_rating
                """;
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            for (int i = 1; i <= 7; i++) {
                stmt.setInt(i, call.userId());
            }
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                call.ok(Json.obj("success", true, "stats", Json.obj(
                        "total_products", rs.getInt("total_products"),
                        "total_orders", rs.getInt("total_orders"),
                        "total_sales", rs.getInt("total_sales"),
                        "total_spent", rs.getBigDecimal("total_spent"),
                        "total_earned", rs.getBigDecimal("total_earned"),
                        "total_reviews", rs.getInt("total_reviews"),
                        "average_rating", rs.getDouble("average_rating"))));
            }
        }
    }

    /** Looks up a transfer recipient. Only public profile fields; never the email address. */
    private void byUsername(Call call) throws IOException, SQLException {
        String username = call.param("username");
        if (username == null) {
            call.error(SC_BAD_REQUEST, "ユーザー名が必要です");
            return;
        }
        String sql = "SELECT user_id, username, full_name, avatar_url, is_verified, is_seller FROM users "
                + "WHERE username = ? AND deleted_at IS NULL";
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    call.error(SC_NOT_FOUND, "ユーザーが見つかりません");
                    return;
                }
                call.ok(Json.obj("success", true, "user", Json.obj(
                        "user_id", rs.getInt("user_id"),
                        "username", rs.getString("username"),
                        "full_name", rs.getString("full_name"),
                        "avatar_url", rs.getString("avatar_url"),
                        "is_verified", rs.getBoolean("is_verified"),
                        "is_seller", rs.getBoolean("is_seller"))));
            }
        }
    }

    /** Only the fields present in the form are changed; bio may be cleared by sending it empty. */
    private void updateProfile(Call call) throws IOException, SQLException {
        String fullName = call.param("full_name");
        String phone = call.param("phone");
        String bio = call.request.getParameter("bio");
        String sql = "UPDATE users SET full_name = COALESCE(?, full_name), phone = COALESCE(?, phone), "
                + "bio = COALESCE(?, bio) WHERE user_id = ?";
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            stmt.setString(1, fullName);
            stmt.setString(2, phone);
            stmt.setString(3, bio);
            stmt.setInt(4, call.userId());
            stmt.executeUpdate();
        }
        call.ok(Json.obj("success", true, "message", "プロフィールを更新しました"));
    }

    private void uploadAvatar(Call call) throws IOException, SQLException {
        Part avatar;
        try {
            avatar = call.request.getPart("avatar");
        } catch (ServletException | IllegalStateException e) {
            call.error(SC_BAD_REQUEST, "画像サイズは10MB以下にしてください");
            return;
        }
        if (avatar == null || avatar.getSize() == 0) {
            call.error(SC_BAD_REQUEST, "画像ファイルを選択してください");
            return;
        }
        String url;
        try {
            url = Uploads.saveImage(avatar, "avatars");
        } catch (Uploads.RejectedUpload e) {
            call.error(SC_BAD_REQUEST, e.getMessage());
            return;
        }
        try (PreparedStatement stmt = call.db().prepareStatement("UPDATE users SET avatar_url = ? WHERE user_id = ?")) {
            stmt.setString(1, url);
            stmt.setInt(2, call.userId());
            stmt.executeUpdate();
        }
        call.ok(Json.obj("success", true, "message", "アバターを更新しました", "avatar_url", url));
    }

    private void addresses(Call call) throws IOException, SQLException {
        String sql = "SELECT address_id, address_label, recipient_name, phone, postal_code, prefecture, city, "
                + "address_line1, address_line2, building_name, is_default, created_at, updated_at "
                + "FROM user_addresses WHERE user_id = ? ORDER BY is_default DESC, created_at DESC";
        List<Map<String, Object>> addresses = new ArrayList<>();
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            stmt.setInt(1, call.userId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    addresses.add(Json.obj(
                            "address_id", rs.getInt("address_id"),
                            "address_label", rs.getString("address_label"),
                            "recipient_name", rs.getString("recipient_name"),
                            "phone", rs.getString("phone"),
                            "postal_code", rs.getString("postal_code"),
                            "prefecture", rs.getString("prefecture"),
                            "city", rs.getString("city"),
                            "address_line1", rs.getString("address_line1"),
                            "address_line2", rs.getString("address_line2"),
                            "building_name", rs.getString("building_name"),
                            "is_default", rs.getBoolean("is_default"),
                            "created_at", rs.getTimestamp("created_at"),
                            "updated_at", rs.getTimestamp("updated_at")));
                }
            }
        }
        call.ok(Json.obj("success", true, "addresses", addresses));
    }

    private void addAddress(Call call) throws IOException, SQLException {
        if (!hasRequiredAddressFields(call)) {
            return;
        }
        Connection db = call.beginTransaction();
        boolean makeDefault = "true".equals(call.param("is_default")) || addressCount(db, call.userId()) == 0;
        if (makeDefault) {
            clearDefault(db, call.userId());
        }
        String sql = "INSERT INTO user_addresses (address_label, recipient_name, phone, postal_code, prefecture, city, "
                + "address_line1, address_line2, building_name, is_default, user_id, country) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '日本')";
        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            bindAddress(call, stmt, makeDefault);
            stmt.setInt(11, call.userId());
            stmt.executeUpdate();
        }
        db.commit();
        call.ok(Json.obj("success", true, "message", "住所を追加しました"));
    }

    private void updateAddress(Call call) throws IOException, SQLException {
        Integer addressId = requireAddressId(call);
        if (addressId == null || !hasRequiredAddressFields(call)) {
            return;
        }
        Connection db = call.beginTransaction();
        boolean makeDefault = "true".equals(call.param("is_default"));
        if (makeDefault) {
            clearDefault(db, call.userId());
        }
        String sql = "UPDATE user_addresses SET address_label = ?, recipient_name = ?, phone = ?, postal_code = ?, "
                + "prefecture = ?, city = ?, address_line1 = ?, address_line2 = ?, building_name = ?, "
                + "is_default = (is_default OR ?) WHERE user_id = ? AND address_id = ?";
        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            bindAddress(call, stmt, makeDefault);
            stmt.setInt(11, call.userId());
            stmt.setInt(12, addressId);
            if (stmt.executeUpdate() == 0) {
                call.error(SC_NOT_FOUND, "住所が見つかりません");
                return;
            }
        }
        db.commit();
        call.ok(Json.obj("success", true, "message", "住所を更新しました"));
    }

    /** Deleting the default address makes the most recent remaining address the default. */
    private void deleteAddress(Call call) throws IOException, SQLException {
        Integer addressId = requireAddressId(call);
        if (addressId == null) {
            return;
        }
        Connection db = call.beginTransaction();
        try (PreparedStatement stmt = db.prepareStatement("DELETE FROM user_addresses WHERE address_id = ? AND user_id = ?")) {
            stmt.setInt(1, addressId);
            stmt.setInt(2, call.userId());
            if (stmt.executeUpdate() == 0) {
                call.error(SC_NOT_FOUND, "住所が見つかりません");
                return;
            }
        }
        String promote = "UPDATE user_addresses SET is_default = 1 WHERE user_id = ? "
                + "AND NOT EXISTS (SELECT 1 FROM (SELECT address_id FROM user_addresses WHERE user_id = ? AND is_default = 1) d) "
                + "ORDER BY created_at DESC LIMIT 1";
        try (PreparedStatement stmt = db.prepareStatement(promote)) {
            stmt.setInt(1, call.userId());
            stmt.setInt(2, call.userId());
            stmt.executeUpdate();
        }
        db.commit();
        call.ok(Json.obj("success", true, "message", "住所を削除しました"));
    }

    private void setDefaultAddress(Call call) throws IOException, SQLException {
        Integer addressId = requireAddressId(call);
        if (addressId == null) {
            return;
        }
        Connection db = call.beginTransaction();
        clearDefault(db, call.userId());
        try (PreparedStatement stmt = db.prepareStatement(
                "UPDATE user_addresses SET is_default = 1 WHERE address_id = ? AND user_id = ?")) {
            stmt.setInt(1, addressId);
            stmt.setInt(2, call.userId());
            if (stmt.executeUpdate() == 0) {
                call.error(SC_NOT_FOUND, "住所が見つかりません");
                return;
            }
        }
        db.commit();
        call.ok(Json.obj("success", true, "message", "デフォルト住所を設定しました"));
    }

    private static boolean hasRequiredAddressFields(Call call) throws IOException {
        for (String field : new String[] {"recipient_name", "postal_code", "prefecture", "city", "address_line1"}) {
            if (call.param(field) == null) {
                call.error(SC_BAD_REQUEST, "必須項目が入力されていません");
                return false;
            }
        }
        return true;
    }

    /** Binds the nine address fields and the default flag to parameters 1–10. */
    private static void bindAddress(Call call, PreparedStatement stmt, boolean isDefault) throws SQLException {
        String label = call.param("address_label");
        stmt.setString(1, label == null ? "自宅" : label);
        stmt.setString(2, call.param("recipient_name"));
        stmt.setString(3, call.param("phone"));
        stmt.setString(4, call.param("postal_code"));
        stmt.setString(5, call.param("prefecture"));
        stmt.setString(6, call.param("city"));
        stmt.setString(7, call.param("address_line1"));
        stmt.setString(8, call.param("address_line2"));
        stmt.setString(9, call.param("building_name"));
        stmt.setBoolean(10, isDefault);
    }

    private static Integer requireAddressId(Call call) throws IOException {
        Integer id = call.intParam("address_id");
        if (id == null) {
            call.error(SC_BAD_REQUEST, "住所IDが必要です");
        }
        return id;
    }

    private static void clearDefault(Connection db, int userId) throws SQLException {
        try (PreparedStatement stmt = db.prepareStatement("UPDATE user_addresses SET is_default = 0 WHERE user_id = ?")) {
            stmt.setInt(1, userId);
            stmt.executeUpdate();
        }
    }

    private static int addressCount(Connection db, int userId) throws SQLException {
        try (PreparedStatement stmt = db.prepareStatement("SELECT COUNT(*) FROM user_addresses WHERE user_id = ?")) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
