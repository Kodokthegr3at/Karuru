package servlet;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.annotation.WebServlet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import util.Json;
import util.Params;

/** Saved search conditions, stored in activity_logs (action = 'search_saved', details = JSON). */
@WebServlet("/SavedSearchesServlet")
public class SavedSearchesServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;

    public SavedSearchesServlet() {
        route("GET", "getSavedSearches", Access.USER, this::list);
        route("POST", "saveSearch", Access.USER, this::save);
        route("POST", "deleteSearch", Access.USER, this::delete);
        route("POST", "clearAll", Access.USER, this::clearAll);
    }

    private void list(Call call) throws IOException, SQLException {
        String sql = "SELECT log_id, details, created_at FROM activity_logs "
                + "WHERE user_id = ? AND action = 'search_saved' AND entity_type = 'search' "
                + "ORDER BY created_at DESC LIMIT 50";
        List<Map<String, Object>> searches = new ArrayList<>();
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            stmt.setInt(1, call.userId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    searches.add(Json.obj(
                            "log_id", rs.getLong("log_id"),
                            "details", rs.getString("details"),
                            "created_at", rs.getTimestamp("created_at")));
                }
            }
        }
        call.ok(Json.obj("success", true, "searches", searches, "count", searches.size()));
    }

    private void save(Call call) throws IOException, SQLException {
        String query = call.param("search_query");
        if (query == null) {
            call.error(SC_BAD_REQUEST, "検索条件が必要です");
            return;
        }
        JsonObject details = new JsonObject();
        details.addProperty("query", query);
        details.add("filters", parseFilters(call.request.getParameter("filters")));

        String sql = "INSERT INTO activity_logs (user_id, action, entity_type, details, ip_address) "
                + "VALUES (?, 'search_saved', 'search', ?, 'system')";
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            stmt.setInt(1, call.userId());
            stmt.setString(2, details.toString());
            stmt.executeUpdate();
        }
        call.ok(Json.obj("success", true, "message", "検索条件を保存しました"));
    }

    private void delete(Call call) throws IOException, SQLException {
        Long logId = Params.optLong(call.request, "log_id");
        if (logId == null) {
            call.error(SC_BAD_REQUEST, "検索IDが必要です");
            return;
        }
        String sql = "DELETE FROM activity_logs WHERE log_id = ? AND user_id = ? AND action = 'search_saved'";
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            stmt.setLong(1, logId);
            stmt.setInt(2, call.userId());
            boolean deleted = stmt.executeUpdate() > 0;
            call.ok(Json.obj("success", deleted, "message", deleted ? "検索条件を削除しました" : "検索条件が見つかりません"));
        }
    }

    private void clearAll(Call call) throws IOException, SQLException {
        String sql = "DELETE FROM activity_logs WHERE user_id = ? AND action = 'search_saved' AND entity_type = 'search'";
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            stmt.setInt(1, call.userId());
            int deleted = stmt.executeUpdate();
            call.ok(Json.obj("success", true, "message", "すべての検索条件を削除しました", "deleted_count", deleted));
        }
    }

    /** The filters parameter as JSON; anything missing or malformed becomes {}. */
    private static JsonElement parseFilters(String raw) {
        if (raw == null || raw.isBlank()) {
            return new JsonObject();
        }
        try {
            return JsonParser.parseString(raw);
        } catch (JsonParseException e) {
            return new JsonObject();
        }
    }
}
