package servlet;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.annotation.WebServlet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import util.Json;

/**
 * Admin dashboard API (admin/dashboard.jsp). Every route is admin-only.
 *
 * The record editor (getRecord/create/update/delete) works on a fixed set of tables, and only on the
 * columns listed for each table: table and column names are put into SQL, so nothing else is accepted,
 * and sensitive columns such as password hashes and tokens are never read or written here.
 */
@WebServlet("/AdminServlet")
public class AdminServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;
    private static final int DEFAULT_LOG_LIMIT = 50;
    private static final int MAX_LOG_LIMIT = 1000;

    /** A table the record editor may touch. {@code softDelete} is the UPDATE used instead of DELETE, if any. */
    private record EditableTable(String idColumn, Set<String> columns, String softDelete) {
    }

    private static final Map<String, EditableTable> TABLES = Map.of(
            "users", new EditableTable("user_id",
                    Set.of("username", "email", "full_name", "phone", "bio", "role", "is_verified", "is_seller"),
                    "UPDATE users SET deleted_at = NOW() WHERE user_id = ? AND deleted_at IS NULL"),
            "products", new EditableTable("product_id",
                    Set.of("user_id", "product_name", "description", "price", "original_price", "discount_percentage",
                            "stock_quantity", "condition", "status", "is_rental", "rental_price_daily",
                            "rental_price_weekly", "rental_price_monthly", "is_negotiable", "featured", "image_url"),
                    "UPDATE products SET status = 'deleted' WHERE product_id = ?"),
            "categories", new EditableTable("category_id",
                    Set.of("category_name", "slug", "description", "image_url", "icon_url", "display_order", "parent_id",
                            "is_active"),
                    null),
            "orders", new EditableTable("order_id",
                    Set.of("order_number", "user_id", "total_amount", "payment_status", "order_status",
                            "tracking_number", "courier", "notes"),
                    null),
            "offers", new EditableTable("offer_id", Set.of(), null));

    public AdminServlet() {
        route("GET", "getStats", Access.ADMIN, this::stats);
        route("GET", "getActivityLogs", Access.ADMIN, this::activityLogs);
        route("GET", "getUsers", Access.ADMIN, call -> list(call,
                "SELECT user_id, username, email, role, is_verified, is_seller, created_at FROM users "
                        + "WHERE deleted_at IS NULL ORDER BY user_id DESC"));
        route("GET", "getProducts", Access.ADMIN, call -> list(call,
                "SELECT p.product_id, p.product_name, p.price, p.stock_quantity, p.status, p.is_rental, p.condition, "
                        + "p.created_at, COALESCE(u.username, 'Unknown') AS seller_name, p.user_id AS seller_id "
                        + "FROM products p LEFT JOIN users u ON p.user_id = u.user_id ORDER BY p.product_id DESC"));
        route("GET", "getCategories", Access.ADMIN, call -> list(call,
                "SELECT c.category_id, c.category_name, c.slug, c.display_order, c.is_active, c.image_url, c.icon_url, "
                        + "c.description, COALESCE(parent.category_name, 'No Parent') AS parent_name "
                        + "FROM categories c LEFT JOIN categories parent ON c.parent_id = parent.category_id "
                        + "ORDER BY c.display_order"));
        route("GET", "getOrders", Access.ADMIN, call -> list(call,
                "SELECT o.order_id, o.order_number, o.total_amount, o.payment_status, o.order_status, o.created_at, "
                        + "COALESCE(u.username, 'Unknown') AS username "
                        + "FROM orders o LEFT JOIN users u ON o.user_id = u.user_id ORDER BY o.order_id DESC"));
        route("GET", "getOffers", Access.ADMIN, this::offers);
        route("GET", "getRecord", Access.ADMIN, this::getRecord);
        route("POST", "create", Access.ADMIN, this::create);
        route("POST", "update", Access.ADMIN, this::update);
        route("DELETE", "delete", Access.ADMIN, this::delete);
    }

    private void stats(Call call) throws IOException, SQLException {
        String sql = "SELECT (SELECT COUNT(*) FROM users) AS users, (SELECT COUNT(*) FROM products) AS products, "
                + "(SELECT COUNT(*) FROM orders) AS orders, (SELECT COUNT(*) FROM offers) AS offers, "
                + "(SELECT COALESCE(SUM(total_amount), 0) FROM orders WHERE payment_status = 'paid') AS revenue";
        try (PreparedStatement stmt = call.db().prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
            rs.next();
            call.ok(Json.obj("success", true, "stats", Json.obj(
                    "totalUsers", rs.getInt("users"),
                    "totalProducts", rs.getInt("products"),
                    "totalOrders", rs.getInt("orders"),
                    "totalOffers", rs.getInt("offers"),
                    "totalRevenue", rs.getBigDecimal("revenue"))));
        }
    }

    private void activityLogs(Call call) throws IOException, SQLException {
        Integer requested = call.intParam("limit");
        int limit = requested == null || requested < 1 || requested > MAX_LOG_LIMIT ? DEFAULT_LOG_LIMIT : requested;
        String sql = "SELECT al.log_id, al.user_id, al.action, al.entity_type, al.entity_id, al.ip_address, al.details, "
                + "al.created_at, u.username, u.full_name FROM activity_logs al LEFT JOIN users u ON al.user_id = u.user_id "
                + "ORDER BY al.created_at DESC, al.log_id DESC LIMIT ?";
        List<Map<String, Object>> logs = new ArrayList<>();
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    logs.add(Json.obj(
                            "log_id", rs.getLong("log_id"),
                            "user_id", rs.getObject("user_id"),
                            "action", orEmpty(rs.getString("action")),
                            "entity_type", orEmpty(rs.getString("entity_type")),
                            "entity_id", rs.getObject("entity_id"),
                            "ip_address", orEmpty(rs.getString("ip_address")),
                            "details", orEmpty(rs.getString("details")),
                            "created_at", rs.getTimestamp("created_at"),
                            "username", orEmpty(rs.getString("username")),
                            "full_name", orEmpty(rs.getString("full_name"))));
                }
            }
        }
        call.ok(Json.obj("success", true, "logs", logs, "total", logs.size()));
    }

    /** {"success": true, "data": [rows]} where each row maps column label → value (null text becomes ""). */
    private void list(Call call, String sql) throws IOException, SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement stmt = call.db().prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
            int columns = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = Json.obj();
                for (int i = 1; i <= columns; i++) {
                    row.put(rs.getMetaData().getColumnLabel(i), displayValue(rs.getObject(i)));
                }
                rows.add(row);
            }
        }
        call.ok(Json.obj("success", true, "data", rows));
    }

    private void offers(Call call) throws IOException, SQLException {
        String sql = """
                SELECT o.offer_id, o.product_id, o.buyer_id, o.seller_id, o.offer_price, o.message, o.status,
                       o.created_at, o.updated_at, p.product_name, p.price AS product_price, p.image_url,
                       p.status AS product_status, ub.username AS buyer_username, ub.full_name AS buyer_name,
                       ub.avatar_url AS buyer_avatar, us.username AS seller_username, us.full_name AS seller_name,
                       us.avatar_url AS seller_avatar
                FROM offers o
                LEFT JOIN products p ON o.product_id = p.product_id
                LEFT JOIN users ub ON o.buyer_id = ub.user_id
                LEFT JOIN users us ON o.seller_id = us.user_id
                ORDER BY o.created_at DESC
                """;
        List<Map<String, Object>> offers = new ArrayList<>();
        try (PreparedStatement stmt = call.db().prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
            int columns = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                Map<String, Object> offer = Json.obj();
                for (int i = 1; i <= columns; i++) {
                    Object value = rs.getObject(i);
                    offer.put(rs.getMetaData().getColumnLabel(i), value instanceof Timestamp ? value.toString() : value);
                }
                offers.add(offer);
            }
        }
        call.ok(Json.obj("success", true, "offers", offers));
    }

    private void getRecord(Call call) throws IOException, SQLException {
        String name = call.param("table", "");
        EditableTable table = TABLES.get(name);
        Integer id = call.intParam("id");
        if (table == null || table.columns().isEmpty()) {
            call.error(SC_BAD_REQUEST, "無効なテーブルです");
            return;
        }
        if (id == null) {
            call.error(SC_BAD_REQUEST, "IDパラメータが必要です");
            return;
        }
        String sql = "SELECT " + table.idColumn() + ", " + String.join(", ", quoted(table.columns())) + " FROM " + name
                + " WHERE " + table.idColumn() + " = ?";
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    call.error(SC_NOT_FOUND, "レコードが見つかりません");
                    return;
                }
                Map<String, Object> record = Json.obj();
                for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                    record.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
                }
                call.ok(Json.obj("success", true, "record", record));
            }
        }
    }

    private void create(Call call) throws IOException, SQLException {
        String name = call.param("table", "");
        EditableTable table = TABLES.get(name);
        JsonObject data = call.body();
        if (!checkColumns(call, table, data)) {
            return;
        }
        if ("users".equals(name)) {
            // An account needs a password hash and verification; that is the registration page's job.
            call.error(SC_BAD_REQUEST, "ユーザーは登録ページから作成してください");
            return;
        }
        List<String> columns = new ArrayList<>(data.keySet());
        String sql = "INSERT INTO " + name + " (" + String.join(", ", quoted(columns)) + ") VALUES ("
                + String.join(", ", columns.stream().map(c -> "?").toList()) + ")";
        try (PreparedStatement stmt = call.db().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(stmt, columns, data);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                keys.next();
                call.ok(Json.obj("success", true, "message", "レコードが正常に作成されました", "generatedId", keys.getObject(1)));
            }
        }
    }

    private void update(Call call) throws IOException, SQLException {
        String name = call.param("table", "");
        EditableTable table = TABLES.get(name);
        JsonObject data = call.body();
        Integer id = call.intParam("id");
        if (!checkColumns(call, table, data)) {
            return;
        }
        if (id == null) {
            call.error(SC_BAD_REQUEST, "IDパラメータが必要です");
            return;
        }
        List<String> columns = new ArrayList<>(data.keySet());
        String sql = "UPDATE " + name + " SET " + String.join(", ", quoted(columns).stream().map(c -> c + " = ?").toList())
                + " WHERE " + table.idColumn() + " = ?";
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            bind(stmt, columns, data);
            stmt.setInt(columns.size() + 1, id);
            if (stmt.executeUpdate() == 0) {
                call.error(SC_NOT_FOUND, "レコードが見つかりません");
                return;
            }
        }
        call.ok(Json.obj("success", true, "message", "レコードが正常に更新されました"));
    }

    private void delete(Call call) throws IOException, SQLException {
        String name = call.param("table", "");
        EditableTable table = TABLES.get(name);
        Integer id = call.intParam("id");
        if (table == null) {
            call.error(SC_BAD_REQUEST, "無効なテーブルです");
            return;
        }
        if (id == null) {
            call.error(SC_BAD_REQUEST, "IDパラメータが必要です");
            return;
        }
        String sql = table.softDelete() != null ? table.softDelete()
                : "DELETE FROM " + name + " WHERE " + table.idColumn() + " = ?";
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            stmt.setInt(1, id);
            if (stmt.executeUpdate() == 0) {
                call.error(SC_NOT_FOUND, "削除するレコードが見つかりません");
                return;
            }
        }
        call.ok(Json.obj("success", true, "message", "レコードが正常に削除されました"));
    }

    /** Sends a 400 unless the table is editable and every key of the body is one of its columns. */
    private static boolean checkColumns(Call call, EditableTable table, JsonObject data) throws IOException {
        if (table == null || table.columns().isEmpty()) {
            call.error(SC_BAD_REQUEST, "無効なテーブルです");
            return false;
        }
        if (data.size() == 0) {
            call.error(SC_BAD_REQUEST, "データが提供されていません");
            return false;
        }
        for (String column : data.keySet()) {
            if (!table.columns().contains(column)) {
                call.error(SC_BAD_REQUEST, "編集できない項目です: " + column);
                return false;
            }
        }
        return true;
    }

    private static void bind(PreparedStatement stmt, List<String> columns, JsonObject data) throws SQLException {
        for (int i = 0; i < columns.size(); i++) {
            JsonElement value = data.get(columns.get(i));
            if (value == null || value.isJsonNull()) {
                stmt.setObject(i + 1, null);
            } else if (value.isJsonPrimitive()) {
                JsonPrimitive primitive = value.getAsJsonPrimitive();
                if (primitive.isBoolean()) {
                    stmt.setBoolean(i + 1, primitive.getAsBoolean());
                } else if (primitive.isNumber()) {
                    stmt.setBigDecimal(i + 1, primitive.getAsBigDecimal());
                } else {
                    stmt.setString(i + 1, primitive.getAsString());
                }
            } else {
                stmt.setString(i + 1, value.toString());
            }
        }
    }

    /** Column names come from the whitelist; backticks only guard against reserved words such as `condition`. */
    private static List<String> quoted(java.util.Collection<String> columns) {
        return columns.stream().map(c -> "`" + c + "`").toList();
    }

    private static Object displayValue(Object value) {
        if (value == null) {
            return "";
        }
        return value instanceof Timestamp ? value.toString() : value;
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
