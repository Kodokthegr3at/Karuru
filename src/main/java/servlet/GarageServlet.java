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
 * Servlet untuk handle user garage (purchased/owned products)
 * Menampilkan produk yang sudah dibeli oleh user
 */
@WebServlet("/GarageServlet")
public class GarageServlet extends HttpServlet {
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
        
        try {
            getGarageProducts(request, response, userId);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage());
        }
    }
    
    // ==================== GET GARAGE PRODUCTS ====================
    private void getGarageProducts(HttpServletRequest request, HttpServletResponse response, int userId) 
            throws IOException {
        
        List<Map<String, Object>> products = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            // Get products purchased by user (all statuses: pending, confirmed, processing, shipped, delivered)
            // Buyer can see purchase status for all their orders
            String sql = "SELECT DISTINCT " +
                    "p.product_id, p.product_name, p.slug, p.image_url, p.price, " +
                    "p.is_rental, p.condition, " +
                    "oi.order_id, oi.quantity, oi.status as order_item_status, " +
                    "o.order_number, o.order_status as order_status, o.created_at as purchase_date, " +
                    "u.username as seller_username " +
                    "FROM order_items oi " +
                    "INNER JOIN orders o ON oi.order_id = o.order_id " +
                    "INNER JOIN products p ON oi.product_id = p.product_id " +
                    "INNER JOIN users u ON p.user_id = u.user_id " +
                    "WHERE o.user_id = ? " +
                    "AND oi.status IN ('pending', 'confirmed', 'processing', 'shipped', 'delivered') " +
                    "ORDER BY o.created_at DESC";
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> product = new HashMap<>();
                int productId = rs.getInt("product_id");
                
                product.put("product_id", productId);
                product.put("product_name", rs.getString("product_name"));
                product.put("slug", rs.getString("slug"));
                
                // Handle image_url - ensure it's not null
                String imageUrl = rs.getString("image_url");
                if (imageUrl == null || imageUrl.trim().isEmpty()) {
                    imageUrl = null;
                }
                product.put("image_url", imageUrl);
                
                product.put("price", rs.getDouble("price"));
                product.put("is_rental", rs.getBoolean("is_rental"));
                product.put("condition", rs.getString("condition"));
                product.put("order_id", rs.getInt("order_id"));
                product.put("order_number", rs.getString("order_number"));
                product.put("quantity", rs.getInt("quantity"));
                product.put("order_item_status", rs.getString("order_item_status"));
                product.put("order_status", rs.getString("order_status"));
                
                // Format timestamp as ISO string for JavaScript
                java.sql.Timestamp purchaseDate = rs.getTimestamp("purchase_date");
                if (purchaseDate != null) {
                    product.put("purchase_date", purchaseDate.toInstant().toString());
                    product.put("created_at", purchaseDate.toInstant().toString());
                } else {
                    product.put("purchase_date", null);
                    product.put("created_at", null);
                }
                
                product.put("seller_username", rs.getString("seller_username"));
                
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

