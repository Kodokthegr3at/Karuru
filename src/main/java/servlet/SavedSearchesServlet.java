package servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * Servlet untuk handle saved searches
 * Note: Menggunakan tabel activity_logs dengan action 'search_saved' sebagai temporary solution
 * Idealnya perlu tabel saved_searches terpisah
 */
@WebServlet("/SavedSearchesServlet")
public class SavedSearchesServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        FilterEncodingUTF8.configureUTF8ForJSON(request, response);
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        Integer userId = (Integer) session.getAttribute("user_id");
        String action = request.getParameter("action");
        
        try {
            if ("getSavedSearches".equals(action)) {
                getSavedSearches(request, response, userId);
            } else {
                sendError(response, "Invalid action");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage());
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        FilterEncodingUTF8.configureUTF8ForJSON(request, response);
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        Integer userId = (Integer) session.getAttribute("user_id");
        String action = request.getParameter("action");
        
        try {
            switch (action != null ? action : "") {
                case "saveSearch":
                    saveSearch(request, response, userId);
                    break;
                case "deleteSearch":
                    deleteSearch(request, response, userId);
                    break;
                case "clearAll":
                    clearAll(request, response, userId);
                    break;
                default:
                    sendError(response, "Invalid action");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage());
        }
    }
    
    // ==================== GET SAVED SEARCHES ====================
    private void getSavedSearches(HttpServletRequest request, HttpServletResponse response, int userId) 
            throws IOException {
        
        List<Map<String, Object>> searches = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            // Get saved searches from activity_logs
            String sql = """
                SELECT log_id, details, created_at
                FROM activity_logs
                WHERE user_id = ? AND action = 'search_saved' AND entity_type = 'search'
                ORDER BY created_at DESC
                LIMIT 50
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> search = new HashMap<>();
                search.put("log_id", rs.getLong("log_id"));
                search.put("details", rs.getString("details"));
                search.put("created_at", rs.getTimestamp("created_at"));
                searches.add(search);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("searches", searches);
            result.put("count", searches.size());
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== SAVE SEARCH ====================
    private void saveSearch(HttpServletRequest request, HttpServletResponse response, int userId) 
            throws IOException {
        
        String searchQuery = request.getParameter("search_query");
        String filters = request.getParameter("filters");
        
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            sendError(response, "検索条件が必要です");
            return;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String details = String.format("{\"query\": \"%s\", \"filters\": %s}", 
                searchQuery.replace("\"", "\\\""), 
                filters != null ? filters : "{}");
            
            String sql = """
                INSERT INTO activity_logs 
                (user_id, action, entity_type, details, ip_address, created_at)
                VALUES (?, 'search_saved', 'search', ?, 'system', NOW())
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            stmt.setString(2, details);
            
            int rowsAffected = stmt.executeUpdate();
            
            if (rowsAffected > 0) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "検索条件を保存しました");
                
                sendJsonResponse(response, result);
            } else {
                sendError(response, "検索条件の保存に失敗しました");
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(null, stmt, conn);
        }
    }
    
    // ==================== DELETE SEARCH ====================
    private void deleteSearch(HttpServletRequest request, HttpServletResponse response, int userId) 
            throws IOException {
        
        String logIdParam = request.getParameter("log_id");
        if (logIdParam == null || logIdParam.isEmpty()) {
            sendError(response, "検索IDが必要です");
            return;
        }
        
        long logId = Long.parseLong(logIdParam);
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = "DELETE FROM activity_logs WHERE log_id = ? AND user_id = ? AND action = 'search_saved'";
            stmt = conn.prepareStatement(sql);
            stmt.setLong(1, logId);
            stmt.setInt(2, userId);
            
            int rowsAffected = stmt.executeUpdate();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", rowsAffected > 0);
            result.put("message", rowsAffected > 0 ? "検索条件を削除しました" : "検索条件が見つかりません");
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(null, stmt, conn);
        }
    }
    
    // ==================== CLEAR ALL ====================
    private void clearAll(HttpServletRequest request, HttpServletResponse response, int userId) 
            throws IOException {
        
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = "DELETE FROM activity_logs WHERE user_id = ? AND action = 'search_saved' AND entity_type = 'search'";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            
            int rowsAffected = stmt.executeUpdate();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "すべての検索条件を削除しました");
            result.put("deleted_count", rowsAffected);
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(null, stmt, conn);
        }
    }
    
    // ==================== UTILITY METHODS ====================
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
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        sendJsonResponse(response, error);
    }
}

