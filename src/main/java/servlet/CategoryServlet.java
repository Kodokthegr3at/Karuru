package servlet;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.annotation.WebServlet;

import util.Json;

/**
 * Active product categories.
 * ?action=getCategories      → JSON array of categories
 * ?action=getCategoryById&id → one category object
 */
@WebServlet({"/CategoryServlet", "/Category"})
public class CategoryServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;

    private static final String ACTIVE_CATEGORIES =
            "SELECT category_id, category_name, slug, description, icon_url, image_url, display_order, is_active, created_at "
            + "FROM categories WHERE is_active = 1";

    public CategoryServlet() {
        for (String method : new String[] {"GET", "POST"}) {
            route(method, "getCategories", Access.PUBLIC, this::list);
            route(method, "getCategoryById", Access.PUBLIC, this::show);
        }
    }

    private void list(Call call) throws IOException, SQLException {
        List<Map<String, Object>> categories = new ArrayList<>();
        try (PreparedStatement stmt = call.db().prepareStatement(ACTIVE_CATEGORIES + " ORDER BY display_order, category_name");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                categories.add(toCategory(rs));
            }
        }
        call.ok(categories);
    }

    private void show(Call call) throws IOException, SQLException {
        String raw = call.param("id");
        if (raw == null) {
            call.error(SC_BAD_REQUEST, "Category ID is required");
            return;
        }
        Integer id = call.intParam("id");
        if (id == null) {
            call.error(SC_BAD_REQUEST, "Invalid category ID: " + raw);
            return;
        }
        try (PreparedStatement stmt = call.db().prepareStatement(ACTIVE_CATEGORIES + " AND category_id = ?")) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    call.ok(toCategory(rs));
                } else {
                    call.error(SC_NOT_FOUND, "Category not found");
                }
            }
        }
    }

    private static Map<String, Object> toCategory(ResultSet rs) throws SQLException {
        return Json.obj(
                "category_id", rs.getInt("category_id"),
                "category_name", rs.getString("category_name"),
                "slug", rs.getString("slug"),
                "description", rs.getString("description"),
                "icon_url", rs.getString("icon_url"),
                "image_url", rs.getString("image_url"),
                "display_order", rs.getInt("display_order"),
                "is_active", rs.getBoolean("is_active"),
                "created_at", rs.getTimestamp("created_at"));
    }
}
