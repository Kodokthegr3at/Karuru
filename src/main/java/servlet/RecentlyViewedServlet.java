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
 * Servlet untuk handle recently viewed products
 * Menggunakan activity_logs dengan action 'product_view'
 */
@WebServlet("/RecentlyViewedServlet")
public class RecentlyViewedServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        FilterEncodingUTF8.configureUTF8ForJSON(request, response);
        
        String action = request.getParameter("action");
        HttpSession session = request.getSession(false);
        
        if (session == null || session.getAttribute("user_id") == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        Integer userId = (Integer) session.getAttribute("user_id");
        
        try {
            if ("getRecentlyViewed".equals(action)) {
                getRecentlyViewed(request, response, userId);
            } else if ("clearHistory".equals(action)) {
                clearHistory(request, response, userId);
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
        doGet(request, response);
    }
    
    // ==================== GET RECENTLY VIEWED PRODUCTS ====================
    private void getRecentlyViewed(HttpServletRequest request, HttpServletResponse response, int userId) 
            throws IOException {
        
        String limitParam = request.getParameter("limit");
        int limit = (limitParam != null && !limitParam.isEmpty()) ? Integer.parseInt(limitParam) : 20;
        
        List<Map<String, Object>> products = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            // Get recently viewed products from activity_logs
            String sql = "SELECT DISTINCT " +
                    "p.product_id, p.product_name, p.slug, p.price, p.image_url, " +
                    "p.status, p.stock_quantity, p.condition, p.is_rental, " +
                    "p.views_count, p.likes_count, p.rating_avg, " +
                    "u.username as seller_username, " +
                    "MAX(al.created_at) as last_viewed " +
                    "FROM activity_logs al " +
                    "INNER JOIN products p ON al.entity_id = p.product_id " +
                    "INNER JOIN users u ON p.user_id = u.user_id " +
                    "WHERE al.user_id = ? " +
                    "AND al.action = 'product_view' " +
                    "AND al.entity_type = 'product' " +
                    "AND p.status = 'available' " +
                    "GROUP BY p.product_id, p.product_name, p.slug, p.price, p.image_url, " +
                    "p.status, p.stock_quantity, p.condition, p.is_rental, " +
                    "p.views_count, p.likes_count, p.rating_avg, u.username " +
                    "ORDER BY last_viewed DESC " +
                    "LIMIT ?";
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            stmt.setInt(2, limit);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> product = new HashMap<>();
                int productId = rs.getInt("product_id");
                
                product.put("product_id", productId);
                product.put("product_name", rs.getString("product_name"));
                product.put("slug", rs.getString("slug"));
                product.put("price", rs.getDouble("price"));
                
                // Handle image_url - ensure it's not null
                String imageUrl = rs.getString("image_url");
                if (imageUrl == null || imageUrl.trim().isEmpty()) {
                    imageUrl = null;
                }
                product.put("image_url", imageUrl);
                
                product.put("status", rs.getString("status"));
                product.put("stock_quantity", rs.getInt("stock_quantity"));
                product.put("condition", rs.getString("condition"));
                product.put("is_rental", rs.getBoolean("is_rental"));
                product.put("views_count", rs.getInt("views_count"));
                product.put("likes_count", rs.getInt("likes_count"));
                product.put("rating_avg", rs.getDouble("rating_avg"));
                product.put("seller_username", rs.getString("seller_username"));
                
                // Format timestamp as ISO string for JavaScript
                java.sql.Timestamp lastViewed = rs.getTimestamp("last_viewed");
                if (lastViewed != null) {
                    product.put("last_viewed", lastViewed.toInstant().toString());
                    product.put("viewed_at", lastViewed.toInstant().toString());
                    product.put("created_at", lastViewed.toInstant().toString());
                } else {
                    product.put("last_viewed", null);
                    product.put("viewed_at", null);
                    product.put("created_at", null);
                }
                
                // Get product images if available
                try {
                    String imagesSql = "SELECT image_url FROM product_images WHERE product_id = ? ORDER BY image_order, image_id";
                    try (PreparedStatement imagesStmt = conn.prepareStatement(imagesSql)) {
                        imagesStmt.setInt(1, productId);
                        try (ResultSet imagesRs = imagesStmt.executeQuery()) {
                            List<String> images = new ArrayList<>();
                            while (imagesRs.next()) {
                                String imgUrl = imagesRs.getString("image_url");
                                if (imgUrl != null && !imgUrl.trim().isEmpty()) {
                                    images.add(imgUrl);
                                }
                            }
                            
                            // If we have images, add them. Also ensure image_url is set if we have images but no image_url
                            if (!images.isEmpty()) {
                                product.put("images", images);
                                // If image_url is null but we have images, use the first one
                                if (imageUrl == null && !images.isEmpty()) {
                                    product.put("image_url", images.get(0));
                                }
                            }
                        }
                    }
                } catch (SQLException e) {
                    // If product_images table doesn't exist or error, just use image_url
                    System.out.println("Could not fetch product images for product " + productId + ": " + e.getMessage());
                }
                
                products.add(product);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("products", products);
            result.put("count", products.size());
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== CLEAR VIEWING HISTORY ====================
    private void clearHistory(HttpServletRequest request, HttpServletResponse response, int userId) 
            throws IOException {
        
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = "DELETE FROM activity_logs WHERE user_id = ? AND action = 'product_view' AND entity_type = 'product'";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            int rowsAffected = stmt.executeUpdate();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "閲覧履歴を削除しました");
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

