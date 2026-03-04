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

import com.google.gson.Gson;

import util.DatabaseConnection;
import util.FilterEncodingUTF8;

@WebServlet({"/CategoryServlet", "/Category"})
public class CategoryServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        FilterEncodingUTF8.configureUTF8ForJSON(request, response);
        
        String action = request.getParameter("action");
        
        System.out.println("=== CategoryServlet Called ===");
        System.out.println("Action: " + action);
        
        try {
            if ("getCategories".equals(action)) {
                getCategories(request, response);
            } else if ("getCategoryById".equals(action)) {
                getCategoryById(request, response);
            } else {
                sendError(response, "Invalid action: " + action);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage());
        }
    }
    
    // ==================== GET CATEGORIES ====================
    private void getCategories(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        System.out.println("=== Getting Categories from Database ===");
        
        List<Map<String, Object>> categories = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = """
                SELECT category_id, category_name, slug, description, 
                       icon_url, image_url, display_order, is_active, created_at 
                FROM categories 
                WHERE is_active = 1 
                ORDER BY display_order ASC, category_name ASC
                """;
            
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> category = new HashMap<>();
                category.put("category_id", rs.getInt("category_id"));
                category.put("category_name", rs.getString("category_name"));
                category.put("slug", rs.getString("slug"));
                category.put("description", rs.getString("description"));
                category.put("icon_url", rs.getString("icon_url"));
                category.put("image_url", rs.getString("image_url"));
                category.put("display_order", rs.getInt("display_order"));
                category.put("is_active", rs.getBoolean("is_active"));
                category.put("created_at", rs.getTimestamp("created_at"));
                
                categories.add(category);
            }
            
            System.out.println("✅ Categories found: " + categories.size());
            
            // Log categories
            for (Map<String, Object> cat : categories) {
                System.out.println("📌 Category: " + cat.get("category_name") + 
                                 " (slug: " + cat.get("slug") + ")");
            }
            
            // Jika tidak ada kategori, return default
            if (categories.isEmpty()) {
                System.out.println("⚠️ No categories in database, returning defaults");
                categories = createDefaultCategories();
            }
            
            sendJsonResponse(response, categories);
            
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("❌ SQL Error: " + e.getMessage());
            
            // Return default categories on error
            categories = createDefaultCategories();
            sendJsonResponse(response, categories);
            
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== GET CATEGORY BY ID ====================
    private void getCategoryById(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        String idParam = request.getParameter("id");
        if (idParam == null || idParam.trim().isEmpty()) {
            sendError(response, "Category ID is required");
            return;
        }
        
        int categoryId;
        try {
            categoryId = Integer.parseInt(idParam);
        } catch (NumberFormatException e) {
            sendError(response, "Invalid category ID: " + idParam);
            return;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = """
                SELECT category_id, category_name, slug, description, 
                       icon_url, image_url, display_order, is_active, created_at 
                FROM categories 
                WHERE category_id = ? AND is_active = 1
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, categoryId);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                Map<String, Object> category = new HashMap<>();
                category.put("category_id", rs.getInt("category_id"));
                category.put("category_name", rs.getString("category_name"));
                category.put("slug", rs.getString("slug"));
                category.put("description", rs.getString("description"));
                category.put("icon_url", rs.getString("icon_url"));
                category.put("image_url", rs.getString("image_url"));
                category.put("display_order", rs.getInt("display_order"));
                category.put("is_active", rs.getBoolean("is_active"));
                category.put("created_at", rs.getTimestamp("created_at"));
                
                sendJsonResponse(response, category);
            } else {
                sendError(response, "Category not found");
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== CREATE DEFAULT CATEGORIES ====================
    private List<Map<String, Object>> createDefaultCategories() {
        List<Map<String, Object>> categories = new ArrayList<>();
        
        // Default categories - Updated 2025
        String[][] defaultCats = {
            {"fashion-accessories", "ファッション・アクセサリー"},
            {"electronics-digital", "家電・デジタル"},
            {"books-music-movies", "本・音楽・映画"},
            {"hobby-entertainment", "ホビー・エンタメ"},
            {"furniture-interior", "家具・インテリア"},
            {"daily-necessities", "生活用品・日用品"},
            {"beauty-health", "美容・健康"},
            {"sports-outdoor", "スポーツ・アウトドア"},
            {"baby-kids", "ベビー・キッズ"},
            {"automotive", "車・バイク・自転車"},
            {"handmade-original", "ハンドメイド・オリジナル"}
        };
        
        for (int i = 0; i < defaultCats.length; i++) {
            Map<String, Object> category = new HashMap<>();
            category.put("category_id", i + 1);
            category.put("category_name", defaultCats[i][1]);
            category.put("slug", defaultCats[i][0]);
            category.put("description", "カテゴリ " + defaultCats[i][1]);
            category.put("icon_url", null);
            category.put("image_url", null);
            category.put("display_order", i + 1);
            category.put("is_active", true);
            category.put("created_at", new java.util.Date());
            
            categories.add(category);
        }
        
        return categories;
    }
    
    // ==================== UTILITY METHODS ====================
    private void sendJsonResponse(HttpServletResponse response, Object data) 
            throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        PrintWriter out = response.getWriter();
        out.print(gson.toJson(data));
        out.flush();
    }
    
    private void sendError(HttpServletResponse response, String message) 
            throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        PrintWriter out = response.getWriter();
        out.print(gson.toJson(error));
        out.flush();
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        doGet(request, response);
    }
}