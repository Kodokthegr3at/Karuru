package servlet;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

import javax.servlet.annotation.WebServlet;

import util.Json;

/**
 * Tracking pings (GET or POST).
 * trackView: logs a product view and bumps products.views_count.
 * trackEvent: logs an arbitrary named event.
 */
@WebServlet({"/Analytics", "/AnalyticsServlet"})
public class AnalyticsServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;

    public AnalyticsServlet() {
        for (String method : new String[] {"GET", "POST"}) {
            route(method, "trackView", Access.PUBLIC, this::trackView);
            route(method, "trackEvent", Access.PUBLIC, this::trackEvent);
        }
    }

    private void trackView(Call call) throws IOException, SQLException {
        if (call.param("productId") == null) {
            call.error(SC_BAD_REQUEST, "productId is required");
            return;
        }
        Integer productId = call.intParam("productId");
        if (productId == null) {
            call.error(SC_BAD_REQUEST, "Invalid productId format");
            return;
        }
        String logSql = "INSERT INTO activity_logs (user_id, action, entity_type, entity_id, ip_address) "
                + "VALUES (?, 'product_view', 'product', ?, ?)";
        try (PreparedStatement log = call.db().prepareStatement(logSql)) {
            setNullableInt(log, 1, call.optUserId());
            log.setInt(2, productId);
            log.setString(3, call.request.getRemoteAddr());
            log.executeUpdate();
        }
        try (PreparedStatement count = call.db().prepareStatement(
                "UPDATE products SET views_count = views_count + 1 WHERE product_id = ?")) {
            count.setInt(1, productId);
            count.executeUpdate();
        }
        call.ok(Json.obj("success", true, "message", "View tracked"));
    }

    private void trackEvent(Call call) throws IOException, SQLException {
        String eventName = call.param("eventName");
        if (eventName == null) {
            call.error(SC_BAD_REQUEST, "eventName is required");
            return;
        }
        String sql = "INSERT INTO activity_logs (user_id, action, entity_type, entity_id, ip_address, details) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            setNullableInt(stmt, 1, call.optUserId());
            stmt.setString(2, eventName);
            stmt.setString(3, call.param("entityType"));
            setNullableInt(stmt, 4, call.intParam("entityId"));
            stmt.setString(5, call.request.getRemoteAddr());
            stmt.setString(6, call.param("details"));
            stmt.executeUpdate();
        }
        call.ok(Json.obj("success", true, "message", "Event tracked"));
    }

    private static void setNullableInt(PreparedStatement stmt, int index, Integer value) throws SQLException {
        if (value == null) {
            stmt.setNull(index, Types.INTEGER);
        } else {
            stmt.setInt(index, value);
        }
    }
}
