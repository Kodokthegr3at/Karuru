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

/**
 * Servlet untuk handle seller profile
 */
@WebServlet("/SellerProfileServlet")
public class SellerProfileServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        FilterEncodingUTF8.configureUTF8ForJSON(request, response);
        
        String sellerIdParam = request.getParameter("seller_id");
        if (sellerIdParam == null || sellerIdParam.isEmpty()) {
            sendError(response, "出品者IDが必要です");
            return;
        }
        
        try {
            int sellerId = Integer.parseInt(sellerIdParam);
            getSellerProfile(request, response, sellerId);
        } catch (NumberFormatException e) {
            sendError(response, "無効な出品者IDです");
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage());
        }
    }
    
    // ==================== GET SELLER PROFILE ====================
    private void getSellerProfile(HttpServletRequest request, HttpServletResponse response, int sellerId) 
            throws IOException {
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            // Get seller info
            String sellerSql = """
                SELECT u.user_id, u.username, u.full_name, u.avatar_url, u.bio,
                       u.is_seller, u.is_verified, u.created_at,
                       COUNT(DISTINCT p.product_id) as total_products,
                       COUNT(DISTINCT oi.order_id) as total_sales,
                       AVG(pr.rating) as avg_rating,
                       COUNT(DISTINCT pr.review_id) as total_reviews
                FROM users u
                LEFT JOIN products p ON u.user_id = p.user_id
                LEFT JOIN order_items oi ON p.product_id = oi.product_id AND oi.status = 'delivered'
                LEFT JOIN product_reviews pr ON p.product_id = pr.product_id AND pr.status = 'approved'
                WHERE u.user_id = ? AND u.deleted_at IS NULL
                GROUP BY u.user_id, u.username, u.full_name, u.avatar_url, u.bio,
                         u.is_seller, u.is_verified, u.created_at
                """;
            
            stmt = conn.prepareStatement(sellerSql);
            stmt.setInt(1, sellerId);
            rs = stmt.executeQuery();
            
            if (!rs.next()) {
                sendError(response, "出品者が見つかりません");
                return;
            }
            
            Map<String, Object> seller = new HashMap<>();
            seller.put("user_id", rs.getInt("user_id"));
            seller.put("username", rs.getString("username"));
            seller.put("full_name", rs.getString("full_name"));
            // Email never exposed to other users for privacy
            // Handle avatar_url - return null if empty/null so frontend can use default-avatar.png
            String avatarUrl = rs.getString("avatar_url");
            seller.put("avatar_url", (avatarUrl != null && !avatarUrl.trim().isEmpty()) ? avatarUrl : null);
            seller.put("bio", rs.getString("bio"));
            seller.put("is_seller", rs.getBoolean("is_seller"));
            seller.put("is_verified", rs.getBoolean("is_verified"));
            seller.put("created_at", rs.getTimestamp("created_at"));
            seller.put("total_products", rs.getLong("total_products"));
            seller.put("total_sales", rs.getLong("total_sales"));
            seller.put("avg_rating", rs.getDouble("avg_rating"));
            seller.put("total_reviews", rs.getLong("total_reviews"));
            
            rs.close();
            stmt.close();
            
            // Get seller's active products - mapping sesuai dengan schema database
            String productsSql = """
                SELECT product_id, product_name, slug, price, image_url, status,
                       views_count, likes_count, rating_avg, rating_count, created_at
                FROM products
                WHERE user_id = ? AND status = 'available'
                ORDER BY created_at DESC
                LIMIT 12
                """;
            
            stmt = conn.prepareStatement(productsSql);
            stmt.setInt(1, sellerId);
            rs = stmt.executeQuery();
            
            List<Map<String, Object>> products = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> product = new HashMap<>();
                int productId = rs.getInt("product_id");
                product.put("product_id", productId);
                product.put("product_name", rs.getString("product_name"));
                product.put("slug", rs.getString("slug"));
                product.put("price", rs.getDouble("price"));
                
                // Get image_url from products table first
                String imageUrl = rs.getString("image_url");
                
                // Get multiple images from product_images table
                List<String> imageUrls = new ArrayList<>();
                try (PreparedStatement imgStmt = conn.prepareStatement(
                        "SELECT image_url FROM product_images WHERE product_id = ? ORDER BY is_primary DESC, image_order ASC, image_id ASC")) {
                    imgStmt.setInt(1, productId);
                    try (ResultSet imgRs = imgStmt.executeQuery()) {
                        while (imgRs.next()) {
                            String imgUrl = imgRs.getString("image_url");
                            if (imgUrl != null && !imgUrl.trim().isEmpty()) {
                                imageUrls.add(imgUrl);
                                System.out.println("  - Found image: " + imgUrl);
                            }
                        }
                        System.out.println("  - Total images found: " + imageUrls.size());
                    }
                } catch (SQLException e) {
                    // If product_images table doesn't exist or error, fallback to image_url from products table
                    System.out.println("Warning: Could not fetch product images: " + e.getMessage());
                }
                
                // Use multiple images if available, otherwise fallback to image_url from products table
                String productName = rs.getString("product_name");
                if (!imageUrls.isEmpty()) {
                    product.put("images", imageUrls);
                    product.put("image_url", imageUrls.get(0)); // Keep image_url for backward compatibility
                    System.out.println("Product " + productId + " (" + productName + ") has " + imageUrls.size() + " images from product_images table");
                    System.out.println("  First image URL: " + imageUrls.get(0));
                } else if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                    // Use image_url from products table if available
                    product.put("image_url", imageUrl);
                    product.put("images", java.util.Arrays.asList(imageUrl));
                    System.out.println("Product " + productId + " (" + productName + ") using image_url from products table: " + imageUrl);
                } else {
                    // No image available - frontend will use default-product.png
                    product.put("image_url", null);
                    product.put("images", new ArrayList<>());
                    System.out.println("Product " + productId + " (" + productName + ") has no images, will use default");
                }
                
                product.put("status", rs.getString("status"));
                product.put("views_count", rs.getInt("views_count"));
                product.put("likes_count", rs.getInt("likes_count"));
                product.put("rating_avg", rs.getDouble("rating_avg"));
                product.put("rating_count", rs.getInt("rating_count"));
                product.put("created_at", rs.getTimestamp("created_at"));
                products.add(product);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("seller", seller);
            result.put("products", products);
            
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

