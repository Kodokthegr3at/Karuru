package servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;

import util.DatabaseConnection;

/**
 * Health check endpoint for debugging 500 errors.
 * Visit /HealthCheckServlet to verify database connection and diagnose issues.
 */
@WebServlet("/HealthCheckServlet")
public class HealthCheckServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> result = new HashMap<>();
        result.put("status", "ok");
        result.put("message", "Health check endpoint is reachable");

        // Test database connection
        try {
            DatabaseConnection.getConnection().close();
            result.put("database", "connected");
        } catch (SQLException e) {
            result.put("status", "error");
            result.put("database", "failed");
            result.put("error", e.getMessage());
            result.put("sqlState", e.getSQLState());
            result.put("message", "Database connection failed. Please check: 1) MySQL is running, 2) Database 'karuru_db' exists, 3) Credentials in DatabaseConnection.java (root/empty password by default)");
        }

        PrintWriter out = response.getWriter();
        out.print(gson.toJson(result));
        out.flush();
    }
}
