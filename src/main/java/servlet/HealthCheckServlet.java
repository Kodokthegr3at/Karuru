package servlet;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import util.DatabaseConnection;
import util.Json;

/** Liveness check for monitoring: 200 when the database is reachable, 503 otherwise. */
@WebServlet("/HealthCheckServlet")
public class HealthCheckServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(HealthCheckServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            Json.ok(response, Json.obj("status", "ok", "database", "connected"));
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Health check: database unreachable", e);
            Json.send(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    Json.obj("status", "error", "database", "failed"));
        }
    }
}
