package servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
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
 * Servlet untuk handle product reviews
 */
@WebServlet("/ReviewServlet")
public class ReviewServlet extends HttpServlet {
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
            if ("getReviewFormData".equals(action)) {
                getReviewFormData(request, response, userId);
            } else if ("checkPurchaseStatus".equals(action)) {
                checkPurchaseStatus(request, response, userId);
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
            if ("submitReview".equals(action)) {
                submitReview(request, response, userId);
            } else {
                sendError(response, "Invalid action");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage());
        }
    }
    
    // ==================== GET REVIEW FORM DATA ====================
    private void getReviewFormData(HttpServletRequest request, HttpServletResponse response, int userId) 
            throws IOException {
        
        String productIdParam = request.getParameter("product_id");
        String orderIdParam = request.getParameter("order_id");
        
        if (productIdParam == null || productIdParam.isEmpty()) {
            sendError(response, "商品IDが必要です");
            return;
        }
        
        int productId = Integer.parseInt(productIdParam);
        Integer orderId = (orderIdParam != null && !orderIdParam.isEmpty()) ? Integer.parseInt(orderIdParam) : null;
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            // Get product info
            String productSql = "SELECT product_id, product_name, image_url FROM products WHERE product_id = ?";
            stmt = conn.prepareStatement(productSql);
            stmt.setInt(1, productId);
            rs = stmt.executeQuery();
            
            if (!rs.next()) {
                sendError(response, "商品が見つかりません");
                return;
            }
            
            Map<String, Object> product = new HashMap<>();
            product.put("product_id", rs.getInt("product_id"));
            product.put("product_name", rs.getString("product_name"));
            product.put("image_url", rs.getString("image_url"));
            
            rs.close();
            stmt.close();
            
            // Check if user already reviewed this product
            String checkSql = "SELECT review_id FROM product_reviews WHERE product_id = ? AND user_id = ?";
            if (orderId != null) {
                checkSql += " AND order_id = ?";
            }
            stmt = conn.prepareStatement(checkSql);
            stmt.setInt(1, productId);
            stmt.setInt(2, userId);
            if (orderId != null) {
                stmt.setInt(3, orderId);
            }
            rs = stmt.executeQuery();
            
            boolean hasReview = rs.next();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("product", product);
            result.put("order_id", orderId);
            result.put("has_review", hasReview);
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== CHECK PURCHASE STATUS ====================
    private void checkPurchaseStatus(HttpServletRequest request, HttpServletResponse response, int userId) 
            throws IOException {
        
        String productIdParam = request.getParameter("product_id");
        
        if (productIdParam == null || productIdParam.isEmpty()) {
            sendError(response, "商品IDが必要です");
            return;
        }
        
        int productId = Integer.parseInt(productIdParam);
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            // Check if user has purchased this product
            String purchaseCheckSql = """
                SELECT oi.order_id 
                FROM order_items oi
                INNER JOIN orders o ON oi.order_id = o.order_id
                WHERE oi.product_id = ? 
                AND o.user_id = ?
                AND o.order_status NOT IN ('cancelled', 'pending')
                LIMIT 1
            """;
            stmt = conn.prepareStatement(purchaseCheckSql);
            stmt.setInt(1, productId);
            stmt.setInt(2, userId);
            rs = stmt.executeQuery();
            
            boolean hasPurchased = rs.next();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("has_purchased", hasPurchased);
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== SUBMIT REVIEW ====================
    private void submitReview(HttpServletRequest request, HttpServletResponse response, int userId) 
            throws IOException {
        
        String productIdParam = request.getParameter("product_id");
        String orderIdParam = request.getParameter("order_id");
        String ratingParam = request.getParameter("rating");
        String reviewText = request.getParameter("review_text");
        
        if (productIdParam == null || productIdParam.isEmpty()) {
            sendError(response, "商品IDが必要です");
            return;
        }
        
        if (ratingParam == null || ratingParam.isEmpty()) {
            sendError(response, "評価が必要です");
            return;
        }
        
        int productId = Integer.parseInt(productIdParam);
        int rating = Integer.parseInt(ratingParam);
        Integer orderId = (orderIdParam != null && !orderIdParam.isEmpty()) ? Integer.parseInt(orderIdParam) : null;
        
        if (rating < 1 || rating > 5) {
            sendError(response, "評価は1から5の間である必要があります");
            return;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            // Check if user already reviewed
            String checkSql = "SELECT review_id FROM product_reviews WHERE product_id = ? AND user_id = ?";
            if (orderId != null) {
                checkSql += " AND order_id = ?";
            }
            stmt = conn.prepareStatement(checkSql);
            stmt.setInt(1, productId);
            stmt.setInt(2, userId);
            if (orderId != null) {
                stmt.setInt(3, orderId);
            }
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                sendError(response, "この商品のレビューは既に投稿されています");
                return;
            }
            
            rs.close();
            stmt.close();
            
            // Check if user has purchased this product (REQUIRED for review)
            // Check if there's an order with this product for this user
            String purchaseCheckSql = """
                SELECT oi.order_id 
                FROM order_items oi
                INNER JOIN orders o ON oi.order_id = o.order_id
                WHERE oi.product_id = ? 
                AND o.user_id = ?
                AND o.order_status NOT IN ('cancelled', 'pending')
                LIMIT 1
            """;
            stmt = conn.prepareStatement(purchaseCheckSql);
            stmt.setInt(1, productId);
            stmt.setInt(2, userId);
            rs = stmt.executeQuery();
            
            boolean hasPurchased = rs.next();
            Integer foundOrderId = null;
            if (hasPurchased) {
                foundOrderId = rs.getInt("order_id");
            }
            rs.close();
            stmt.close();
            
            // If user hasn't purchased, reject the review
            if (!hasPurchased) {
                sendError(response, "この商品を購入したユーザーのみレビューを投稿できます");
                return;
            }
            
            // Use found order_id if orderId was not provided
            if (orderId == null) {
                orderId = foundOrderId;
            }
            
            // Verify that the provided order_id (if any) belongs to user and contains this product
            boolean isVerifiedPurchase = false;
            if (orderId != null) {
                String orderSql = """
                    SELECT oi.order_id 
                    FROM order_items oi
                    INNER JOIN orders o ON oi.order_id = o.order_id
                    WHERE oi.order_id = ? 
                    AND oi.product_id = ?
                    AND o.user_id = ?
                    AND o.order_status NOT IN ('cancelled', 'pending')
                """;
                stmt = conn.prepareStatement(orderSql);
                stmt.setInt(1, orderId);
                stmt.setInt(2, productId);
                stmt.setInt(3, userId);
                rs = stmt.executeQuery();
                isVerifiedPurchase = rs.next();
                rs.close();
                stmt.close();
                
                // If provided order_id doesn't match, use the found one
                if (!isVerifiedPurchase && foundOrderId != null) {
                    orderId = foundOrderId;
                    isVerifiedPurchase = true;
                }
            } else {
                isVerifiedPurchase = true; // We already verified purchase above
            }
            
            // Insert review
            String insertSql = """
                INSERT INTO product_reviews 
                (product_id, user_id, order_id, rating, review_text, is_verified_purchase, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'pending', NOW(), NOW())
                """;
            
            stmt = conn.prepareStatement(insertSql);
            stmt.setInt(1, productId);
            stmt.setInt(2, userId);
            if (orderId != null) {
                stmt.setInt(3, orderId);
            } else {
                stmt.setNull(3, java.sql.Types.INTEGER);
            }
            stmt.setInt(4, rating);
            stmt.setString(5, reviewText != null ? reviewText.trim() : null);
            stmt.setBoolean(6, isVerifiedPurchase);
            
            int rowsAffected = stmt.executeUpdate();
            
            if (rowsAffected > 0) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "レビューを投稿しました。承認後に公開されます。");
                
                sendJsonResponse(response, result);
            } else {
                sendError(response, "レビューの投稿に失敗しました");
            }
            
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

