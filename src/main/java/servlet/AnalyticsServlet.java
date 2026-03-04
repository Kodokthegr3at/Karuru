package servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.google.gson.Gson;

import util.DatabaseConnection;
import util.FilterEncodingUTF8;

/**
 * Lightweight analytics servlet to accept frontend tracking calls.
 * Supported actions:
 *  - trackView (productId) : logs a product view to activity_logs and increments products.views_count
 *  - trackEvent (eventName, entityType, entityId, details) : logs a generic event into activity_logs
 */
@WebServlet({"/Analytics", "/AnalyticsServlet"})
public class AnalyticsServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // support GET for simple tracking pings
        handleRequest(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        handleRequest(request, response);
    }

    private void handleRequest(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        FilterEncodingUTF8.configureUTF8ForJSON(request, response);

        String action = request.getParameter("action");
        if (action == null || action.isEmpty()) {
            sendError(response, "Action parameter is required");
            return;
        }

        try {
            switch (action) {
                case "trackView":
                    trackView(request, response);
                    break;
                case "trackEvent":
                    trackEvent(request, response);
                    break;
                default:
                    sendError(response, "Invalid action: " + action);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage());
        }
    }

    // Track a product view: log activity and increment views_count
    private void trackView(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String productIdStr = request.getParameter("productId");
        if (productIdStr == null || productIdStr.isEmpty()) {
            sendError(response, "productId is required");
            return;
        }

        Integer userId = null;
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("user_id") != null) {
            try {
                userId = (Integer) session.getAttribute("user_id");
            } catch (Exception ex) {
                userId = null;
            }
        }

        String ipAddress = request.getRemoteAddr();

        Connection conn = null;
        PreparedStatement logStmt = null;
        PreparedStatement updateStmt = null;

        try {
            int productId = Integer.parseInt(productIdStr);
            conn = DatabaseConnection.getConnection();

            // Insert into activity_logs
            String insertSql = "INSERT INTO activity_logs (user_id, action, entity_type, entity_id, ip_address, created_at) VALUES (?, 'product_view', 'product', ?, ?, NOW())";
            logStmt = conn.prepareStatement(insertSql);
            if (userId != null) {
                logStmt.setInt(1, userId);
            } else {
                logStmt.setNull(1, java.sql.Types.INTEGER);
            }
            logStmt.setInt(2, productId);
            logStmt.setString(3, ipAddress);
            logStmt.executeUpdate();

            // Update product views_count
            String updateSql = "UPDATE products SET views_count = COALESCE(views_count, 0) + 1 WHERE product_id = ?";
            updateStmt = conn.prepareStatement(updateSql);
            updateStmt.setInt(1, productId);
            updateStmt.executeUpdate();

            sendJsonResponse(response, java.util.Map.of("success", true, "message", "View tracked"));

        } catch (NumberFormatException e) {
            sendError(response, "Invalid productId format");
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(null, logStmt, null);
            DatabaseConnection.closeResources(null, updateStmt, conn);
        }
    }

    // Track a generic event: eventName required
    private void trackEvent(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String eventName = request.getParameter("eventName");
        String entityType = request.getParameter("entityType");
        String entityIdStr = request.getParameter("entityId");
        String details = request.getParameter("details");

        if (eventName == null || eventName.isEmpty()) {
            sendError(response, "eventName is required");
            return;
        }

        Integer userId = null;
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("user_id") != null) {
            try {
                userId = (Integer) session.getAttribute("user_id");
            } catch (Exception ex) {
                userId = null;
            }
        }

        String ipAddress = request.getRemoteAddr();

        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = DatabaseConnection.getConnection();

            String insertSql = "INSERT INTO activity_logs (user_id, action, entity_type, entity_id, ip_address, details, created_at) VALUES (?, ?, ?, ?, ?, ?, NOW())";
            stmt = conn.prepareStatement(insertSql);

            if (userId != null) {
                stmt.setInt(1, userId);
            } else {
                stmt.setNull(1, java.sql.Types.INTEGER);
            }

            stmt.setString(2, eventName);

            if (entityType != null && !entityType.isEmpty()) {
                stmt.setString(3, entityType);
            } else {
                stmt.setNull(3, java.sql.Types.VARCHAR);
            }

            if (entityIdStr != null && !entityIdStr.isEmpty()) {
                try {
                    stmt.setInt(4, Integer.parseInt(entityIdStr));
                } catch (NumberFormatException ex) {
                    stmt.setNull(4, java.sql.Types.INTEGER);
                }
            } else {
                stmt.setNull(4, java.sql.Types.INTEGER);
            }

            stmt.setString(5, ipAddress);
            if (details != null && !details.isEmpty()) {
                stmt.setString(6, details);
            } else {
                stmt.setNull(6, java.sql.Types.VARCHAR);
            }

            stmt.executeUpdate();

            sendJsonResponse(response, java.util.Map.of("success", true, "message", "Event tracked"));

        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(null, stmt, conn);
        }
    }

    private void sendJsonResponse(HttpServletResponse response, Object data) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        out.print(gson.toJson(data));
        out.flush();
    }

    private void sendError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        out.print(gson.toJson(java.util.Map.of("success", false, "error", message)));
        out.flush();
    }
}
