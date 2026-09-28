package servlet;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.annotation.WebServlet;

import util.Json;

/**
 * Home page banners. Reads are public (getActive, getByPosition, getById); listing every banner and
 * create/update/delete are admin-only (admin/banners.jsp).
 */
@WebServlet({"/BannerServlet", "/Banner"})
public class BannerServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;
    private static final String DEFAULT_POSITION = "home_top";

    private static final String COLUMNS = "SELECT banner_id, title, image_url, link_url, position, display_order, "
            + "is_active, start_date, end_date, created_at FROM banners ";
    private static final String CURRENTLY_SHOWN = "is_active = 1 AND (start_date IS NULL OR start_date <= NOW()) "
            + "AND (end_date IS NULL OR end_date >= NOW()) ";
    private static final String ORDER = "ORDER BY display_order, created_at DESC";

    public BannerServlet() {
        route("GET", null, Access.PUBLIC, this::active);
        route("GET", "getActive", Access.PUBLIC, this::active);
        route("GET", "getByPosition", Access.PUBLIC, this::byPosition);
        route("GET", "getById", Access.PUBLIC, this::byId);
        route("GET", "getAll", Access.ADMIN, this::all);
        route("POST", "create", Access.ADMIN, this::create);
        route("POST", "update", Access.ADMIN, this::update);
        route("POST", "delete", Access.ADMIN, this::delete);
    }

    private void active(Call call) throws IOException, SQLException {
        call.ok(query(call, COLUMNS + "WHERE " + CURRENTLY_SHOWN + ORDER, null));
    }

    private void byPosition(Call call) throws IOException, SQLException {
        String position = call.param("position");
        if (position == null) {
            call.error(SC_BAD_REQUEST, "Position parameter is required");
            return;
        }
        call.ok(query(call, COLUMNS + "WHERE position = ? AND " + CURRENTLY_SHOWN + ORDER, position));
    }

    private void all(Call call) throws IOException, SQLException {
        call.ok(Json.obj("success", true, "banners", query(call, COLUMNS + ORDER, null)));
    }

    private void byId(Call call) throws IOException, SQLException {
        Integer id = requireId(call);
        if (id == null) {
            return;
        }
        try (PreparedStatement stmt = call.db().prepareStatement(COLUMNS + "WHERE banner_id = ?")) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    call.ok(Json.obj("success", true, "banner", toBanner(rs)));
                } else {
                    call.error(SC_NOT_FOUND, "Banner not found");
                }
            }
        }
    }

    private void create(Call call) throws IOException, SQLException {
        if (call.param("title") == null) {
            call.error(SC_BAD_REQUEST, "Title is required");
            return;
        }
        if (call.param("image_url") == null) {
            call.error(SC_BAD_REQUEST, "Image URL is required");
            return;
        }
        String sql = "INSERT INTO banners (title, image_url, link_url, position, display_order, is_active, start_date, end_date) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            bindForm(call, stmt);
            stmt.executeUpdate();
        }
        call.ok(Json.obj("success", true, "message", "Banner created successfully"));
    }

    private void update(Call call) throws IOException, SQLException {
        Integer id = requireId(call);
        if (id == null) {
            return;
        }
        String sql = "UPDATE banners SET title = ?, image_url = ?, link_url = ?, position = ?, display_order = ?, "
                + "is_active = ?, start_date = ?, end_date = ? WHERE banner_id = ?";
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            bindForm(call, stmt);
            stmt.setInt(9, id);
            if (stmt.executeUpdate() == 0) {
                call.error(SC_NOT_FOUND, "Banner not found or no changes made");
                return;
            }
        }
        call.ok(Json.obj("success", true, "message", "Banner updated successfully"));
    }

    private void delete(Call call) throws IOException, SQLException {
        Integer id = requireId(call);
        if (id == null) {
            return;
        }
        try (PreparedStatement stmt = call.db().prepareStatement("DELETE FROM banners WHERE banner_id = ?")) {
            stmt.setInt(1, id);
            if (stmt.executeUpdate() == 0) {
                call.error(SC_NOT_FOUND, "Banner not found");
                return;
            }
        }
        call.ok(Json.obj("success", true, "message", "Banner deleted successfully"));
    }

    /** Reads banner_id; sends a 400 and returns null when it is missing or invalid. */
    private static Integer requireId(Call call) throws IOException {
        if (call.param("banner_id") == null) {
            call.error(SC_BAD_REQUEST, "Banner ID is required");
            return null;
        }
        Integer id = call.intParam("banner_id");
        if (id == null) {
            call.error(SC_BAD_REQUEST, "Invalid banner ID");
        }
        return id;
    }

    /** Binds the eight form fields shared by create and update to parameters 1–8. */
    private static void bindForm(Call call, PreparedStatement stmt) throws SQLException {
        String title = call.param("title");
        String imageUrl = call.param("image_url");
        String position = call.param("position");
        Integer displayOrder = call.intParam("display_order");
        String active = call.param("is_active");
        stmt.setString(1, title == null ? "" : title);
        stmt.setString(2, imageUrl == null ? "" : imageUrl);
        stmt.setString(3, call.param("link_url"));
        stmt.setString(4, position == null ? DEFAULT_POSITION : position);
        stmt.setInt(5, displayOrder == null ? 0 : displayOrder);
        stmt.setBoolean(6, active == null || "1".equals(active) || "true".equalsIgnoreCase(active));
        stmt.setTimestamp(7, parseDateTime(call.param("start_date")));
        stmt.setTimestamp(8, parseDateTime(call.param("end_date")));
    }

    /** Accepts "yyyy-MM-dd HH:mm:ss" and the datetime-local form "yyyy-MM-ddTHH:mm[:ss]"; anything else → null. */
    private static Timestamp parseDateTime(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace('T', ' ').replace("Z", "");
        if (normalized.length() == 16) {
            normalized += ":00";
        }
        try {
            return Timestamp.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static List<Map<String, Object>> query(Call call, String sql, String param) throws SQLException {
        List<Map<String, Object>> banners = new ArrayList<>();
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            if (param != null) {
                stmt.setString(1, param);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    banners.add(toBanner(rs));
                }
            }
        }
        return banners;
    }

    private static Map<String, Object> toBanner(ResultSet rs) throws SQLException {
        return Json.obj(
                "banner_id", rs.getInt("banner_id"),
                "title", orEmpty(rs.getString("title")),
                "image_url", orEmpty(rs.getString("image_url")),
                "link_url", orEmpty(rs.getString("link_url")),
                "position", orEmpty(rs.getString("position")),
                "display_order", rs.getInt("display_order"),
                "is_active", rs.getBoolean("is_active"),
                "start_date", text(rs.getTimestamp("start_date")),
                "end_date", text(rs.getTimestamp("end_date")),
                "created_at", text(rs.getTimestamp("created_at")));
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String text(Timestamp value) {
        return value == null ? null : value.toString();
    }
}
