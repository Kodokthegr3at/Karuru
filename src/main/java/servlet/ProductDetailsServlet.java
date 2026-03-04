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
 * Servlet for product details page
 * Endpoint: /ProductDetailsServlet
 */
@WebServlet("/ProductDetailsServlet")
public class ProductDetailsServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        FilterEncodingUTF8.configureUTF8ForJSON(request, response);
        
        try {
            String action = request.getParameter("action");
            
            if ("getReviews".equals(action)) {
                getProductReviewsWithComments(request, response);
            } else if ("addReview".equals(action)) {
                addProductReview(request, response);
            } else if ("addComment".equals(action)) {
                addReviewComment(request, response);
            } else if ("helpful".equals(action)) {
                markReviewHelpful(request, response);
            } else {
                getProductDetails(request, response);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "サーバーエラー: " + e.getMessage());
        }
    }
    
    // ==================== GET PRODUCT DETAILS (UTAMA) ====================
    private void getProductDetails(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        String productIdStr = request.getParameter("id");
        
        if (productIdStr == null || productIdStr.trim().isEmpty()) {
            sendError(response, "商品IDが必要です");
            return;
        }
        
        int productId;
        try {
            productId = Integer.parseInt(productIdStr);
        } catch (NumberFormatException e) {
            sendError(response, "無効な商品ID形式です");
            return;
        }
        
        System.out.println("=== 商品詳細リクエスト ===");
        System.out.println("商品ID: " + productId);
        
        Connection conn = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            if (conn == null) {
                System.err.println("データベース接続に失敗しました！");
                sendError(response, "データベース接続エラー");
                return;
            }
            
            System.out.println("データベース接続確立");
            
            // Get product details
            Map<String, Object> product = getProduct(conn, productId);
            
            if (product == null) {
                System.err.println("商品が見つかりません: " + productId);
                sendError(response, "商品が見つかりません");
                return;
            }
            
            System.out.println("商品が見つかりました: " + product.get("product_name"));
            
            // Get product specifications
            List<Map<String, Object>> specifications = getProductSpecifications(conn, productId);
            System.out.println("仕様情報読み込み: " + specifications.size());
            
            // Get product images
            List<Map<String, Object>> images = getProductImages(conn, productId);
            System.out.println("画像読み込み: " + images.size());
            
            // Get product categories
            List<Map<String, Object>> categories = getProductCategories(conn, productId);
            System.out.println("カテゴリ読み込み: " + categories.size());
            
            // Get related products (same category)
            List<Map<String, Object>> relatedProducts = getRelatedProducts(conn, productId);
            System.out.println("関連商品読み込み: " + relatedProducts.size());
            
            // Get product reviews with comments
            List<Map<String, Object>> reviews = getProductReviewsWithComments(conn, productId);
            System.out.println("レビュー読み込み: " + reviews.size());
            
            // Get review statistics
            Map<String, Object> reviewStats = getReviewStatistics(conn, productId);
            System.out.println("レビュー統計読み込み");
            
            // Get seller statistics (average rating, total reviews)
            int sellerId = (int) product.get("seller_id");
            Map<String, Object> sellerStats = getSellerStatistics(conn, sellerId);
            
            // Track product view (async) - FIXED: Removed problematic thread
            trackProductViewSync(conn, request, productId);
            
            // Prepare response
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("success", true);
            responseData.put("product", product);
            responseData.put("specifications", specifications);
            responseData.put("images", images);
            responseData.put("categories", categories);
            responseData.put("relatedProducts", relatedProducts);
            responseData.put("reviews", reviews);
            responseData.put("reviewStats", reviewStats);
            responseData.put("sellerStats", sellerStats);
            
            System.out.println("レスポンス送信中...");
            sendJsonResponse(response, responseData);
            
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("SQLエラー: " + e.getMessage());
            sendError(response, "データベースエラー: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(conn);
        }
    }
    
    // ==================== GET SELLER STATISTICS ====================
    private Map<String, Object> getSellerStatistics(Connection conn, int sellerId) throws SQLException {
        Map<String, Object> stats = new HashMap<>();
        
        String sql = """
            SELECT 
                COALESCE(AVG(r.rating), 4.8) as avg_rating,
                COALESCE(COUNT(r.review_id), 120) as review_count
            FROM products p
            LEFT JOIN product_reviews r ON p.product_id = r.product_id 
                AND r.status = 'approved'
            WHERE p.user_id = ?
            """;
        
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, sellerId);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                stats.put("avg_rating", rs.getDouble("avg_rating"));
                stats.put("review_count", rs.getInt("review_count"));
            } else {
                stats.put("avg_rating", 4.8);
                stats.put("review_count", 120);
            }
            
        } finally {
            DatabaseConnection.closeResources(rs, stmt);
        }
        
        return stats;
    }
    
    // ==================== GET REVIEWS WITH COMMENTS ====================
    private void getProductReviewsWithComments(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        String productIdStr = request.getParameter("productId");
        String reviewIdStr = request.getParameter("reviewId");
        
        if (productIdStr == null && reviewIdStr == null) {
            sendError(response, "商品IDまたはレビューIDが必要です");
            return;
        }
        
        Connection conn = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            if (reviewIdStr != null) {
                // Get single review with comments
                int reviewId = Integer.parseInt(reviewIdStr);
                Map<String, Object> review = getReviewWithComments(conn, reviewId);
                
                if (review != null) {
                    Map<String, Object> responseData = new HashMap<>();
                    responseData.put("success", true);
                    responseData.put("review", review);
                    sendJsonResponse(response, responseData);
                } else {
                    sendError(response, "レビューが見つかりません");
                }
            } else {
                // Get all reviews for product with comments
                int productId = Integer.parseInt(productIdStr);
                List<Map<String, Object>> reviews = getProductReviewsWithComments(conn, productId);
                
                Map<String, Object> responseData = new HashMap<>();
                    responseData.put("success", true);
                    responseData.put("reviews", reviews);
                    sendJsonResponse(response, responseData);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "エラー: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(conn);
        }
    }
    
    // ==================== GET PRODUCT REVIEWS WITH COMMENTS ====================
    private List<Map<String, Object>> getProductReviewsWithComments(Connection conn, int productId) throws SQLException {
        List<Map<String, Object>> reviews = new ArrayList<>();
        
        String sql = """
            SELECT r.*, u.username, u.full_name, u.avatar_url
            FROM product_reviews r
            INNER JOIN users u ON r.user_id = u.user_id
            WHERE r.product_id = ? AND r.status = 'approved'
            ORDER BY 
                CASE WHEN r.is_verified_purchase = 1 THEN 0 ELSE 1 END,
                r.helpful_count DESC,
                r.created_at DESC
            """;
        
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, productId);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> review = mapReviewResultSet(rs);
                
                // Get comments for this review
                List<Map<String, Object>> comments = getReviewComments(conn, rs.getInt("review_id"));
                review.put("comments", comments);
                
                reviews.add(review);
            }
            
        } finally {
            DatabaseConnection.closeResources(rs, stmt);
        }
        
        return reviews;
    }
    
    // ==================== GET REVIEW WITH COMMENTS ====================
    private Map<String, Object> getReviewWithComments(Connection conn, int reviewId) throws SQLException {
        String reviewSql = """
            SELECT r.*, u.username, u.full_name, u.avatar_url
            FROM product_reviews r
            INNER JOIN users u ON r.user_id = u.user_id
            WHERE r.review_id = ? AND r.status = 'approved'
            """;
        
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            stmt = conn.prepareStatement(reviewSql);
            stmt.setInt(1, reviewId);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                Map<String, Object> review = mapReviewResultSet(rs);
                
                // Get comments for this review
                List<Map<String, Object>> comments = getReviewComments(conn, rs.getInt("review_id"));
                review.put("comments", comments);
                
                return review;
            }
            
            return null;
            
        } finally {
            DatabaseConnection.closeResources(rs, stmt);
        }
    }
    
    // ==================== GET REVIEW COMMENTS ====================
    private List<Map<String, Object>> getReviewComments(Connection conn, int reviewId) throws SQLException {
        List<Map<String, Object>> comments = new ArrayList<>();
        
        String sql = """
            SELECT c.*, u.username, u.full_name, u.avatar_url, 
                   CASE WHEN u.user_id = p.user_id THEN 1 ELSE 0 END as is_seller
            FROM review_comments c
            INNER JOIN users u ON c.user_id = u.user_id
            INNER JOIN product_reviews pr ON c.review_id = pr.review_id
            INNER JOIN products p ON pr.product_id = p.product_id
            WHERE c.review_id = ? AND c.status = 'active'
            ORDER BY c.created_at ASC
            """;
        
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, reviewId);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> comment = new HashMap<>();
                comment.put("comment_id", rs.getInt("comment_id"));
                comment.put("review_id", rs.getInt("review_id"));
                comment.put("comment_text", rs.getString("comment_text"));
                comment.put("parent_comment_id", rs.getObject("parent_comment_id"));
                comment.put("is_seller_reply", rs.getBoolean("is_seller"));
                comment.put("created_at", rs.getTimestamp("created_at"));
                comment.put("updated_at", rs.getTimestamp("updated_at"));
                comment.put("status", rs.getString("status"));
                
                Map<String, Object> commenter = new HashMap<>();
                commenter.put("user_id", rs.getInt("user_id"));
                commenter.put("username", rs.getString("username"));
                commenter.put("full_name", rs.getString("full_name"));
                commenter.put("avatar_url", rs.getString("avatar_url"));
                
                comment.put("commenter", commenter);
                comments.add(comment);
            }
            
        } finally {
            DatabaseConnection.closeResources(rs, stmt);
        }
        
        return comments;
    }
    
    // ==================== GET REVIEW STATISTICS ====================
    private Map<String, Object> getReviewStatistics(Connection conn, int productId) throws SQLException {
        Map<String, Object> stats = new HashMap<>();
        
        String sql = """
            SELECT 
                COUNT(*) as total_reviews,
                AVG(rating) as average_rating,
                SUM(CASE WHEN rating = 5 THEN 1 ELSE 0 END) as five_star,
                SUM(CASE WHEN rating = 4 THEN 1 ELSE 0 END) as four_star,
                SUM(CASE WHEN rating = 3 THEN 1 ELSE 0 END) as three_star,
                SUM(CASE WHEN rating = 2 THEN 1 ELSE 0 END) as two_star,
                SUM(CASE WHEN rating = 1 THEN 1 ELSE 0 END) as one_star,
                SUM(is_verified_purchase) as verified_purchases
            FROM product_reviews 
            WHERE product_id = ? AND status = 'approved'
            """;
        
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, productId);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                int total = rs.getInt("total_reviews");
                stats.put("total", total);
                
                double avgRating = rs.getDouble("average_rating");
                stats.put("average", rs.wasNull() ? 0.0 : avgRating);
                
                if (total > 0) {
                    stats.put("fiveStar", rs.getInt("five_star"));
                    stats.put("fourStar", rs.getInt("four_star"));
                    stats.put("threeStar", rs.getInt("three_star"));
                    stats.put("twoStar", rs.getInt("two_star"));
                    stats.put("oneStar", rs.getInt("one_star"));
                } else {
                    stats.put("fiveStar", 0);
                    stats.put("fourStar", 0);
                    stats.put("threeStar", 0);
                    stats.put("twoStar", 0);
                    stats.put("oneStar", 0);
                }
                
                stats.put("verifiedPurchases", rs.getInt("verified_purchases"));
            } else {
                // Default values if no reviews
                stats.put("total", 0);
                stats.put("average", 0.0);
                stats.put("fiveStar", 0);
                stats.put("fourStar", 0);
                stats.put("threeStar", 0);
                stats.put("twoStar", 0);
                stats.put("oneStar", 0);
                stats.put("verifiedPurchases", 0);
            }
            
        } finally {
            DatabaseConnection.closeResources(rs, stmt);
        }
        
        return stats;
    }
    
    // ==================== ADD PRODUCT REVIEW ====================
    private void addProductReview(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        int userId = (Integer) session.getAttribute("user_id");
        String productIdStr = request.getParameter("productId");
        String ratingStr = request.getParameter("rating");
        String reviewText = request.getParameter("reviewText");
        
        if (productIdStr == null || ratingStr == null || reviewText == null) {
            sendError(response, "必須項目が不足しています");
            return;
        }
        
        Connection conn = null;
        PreparedStatement checkStmt = null;
        PreparedStatement insertStmt = null;
        ResultSet rs = null;
        
        try {
            int productId = Integer.parseInt(productIdStr);
            int rating = Integer.parseInt(ratingStr);
            
            if (rating < 1 || rating > 5) {
                sendError(response, "評価は1〜5の間で指定してください");
                return;
            }
            
            if (reviewText.trim().length() < 10) {
                sendError(response, "レビューは10文字以上で入力してください");
                return;
            }
            
            if (reviewText.trim().length() > 2000) {
                sendError(response, "レビューは2000文字以内で入力してください");
                return;
            }
            
            conn = DatabaseConnection.getConnection();
            
            // Check if user already reviewed this product
            String checkSql = "SELECT review_id FROM product_reviews WHERE user_id = ? AND product_id = ?";
            checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setInt(1, userId);
            checkStmt.setInt(2, productId);
            rs = checkStmt.executeQuery();
            
            if (rs.next()) {
                sendError(response, "この商品には既にレビューを投稿しています");
                return;
            }
            
            // Check if user has purchased this product (for verified purchase)
            boolean isVerified = false;
            String purchaseCheckSql = """
                SELECT COUNT(*) FROM order_items oi
                INNER JOIN orders o ON oi.order_id = o.order_id
                WHERE o.user_id = ? AND oi.product_id = ? 
                AND o.order_status IN ('delivered', 'completed')
                """;
            try (PreparedStatement purchaseStmt = conn.prepareStatement(purchaseCheckSql)) {
                purchaseStmt.setInt(1, userId);
                purchaseStmt.setInt(2, productId);
                try (ResultSet purchaseRs = purchaseStmt.executeQuery()) {
                    if (purchaseRs.next() && purchaseRs.getInt(1) > 0) {
                        isVerified = true;
                    }
                }
            }
            
            // Insert new review
            String insertSql = """
                INSERT INTO product_reviews 
                (product_id, user_id, rating, review_text, is_verified_purchase, status, created_at) 
                VALUES (?, ?, ?, ?, ?, 'pending', NOW())
                """;
            
            insertStmt = conn.prepareStatement(insertSql);
            insertStmt.setInt(1, productId);
            insertStmt.setInt(2, userId);
            insertStmt.setInt(3, rating);
            insertStmt.setString(4, reviewText.trim());
            insertStmt.setBoolean(5, isVerified);
            
            int rowsAffected = insertStmt.executeUpdate();
            
            if (rowsAffected > 0) {
                // Update product rating
                updateProductRating(conn, productId);
                
                Map<String, Object> responseData = new HashMap<>();
                responseData.put("success", true);
                responseData.put("message", isVerified ? 
                    "レビューを投稿しました。購入者としてのレビューが承認待ちです。" : 
                    "レビューを投稿しました。承認待ちです。");
                sendJsonResponse(response, responseData);
            } else {
                sendError(response, "レビューの投稿に失敗しました");
            }
            
        } catch (NumberFormatException e) {
            sendError(response, "無効な数値形式です");
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "データベースエラー: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "エラー: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs);
            DatabaseConnection.closeResources(checkStmt);
            DatabaseConnection.closeResources(insertStmt);
            DatabaseConnection.closeResources(conn);
        }
    }
    
    // ==================== ADD REVIEW COMMENT ====================
    private void addReviewComment(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        int userId = (Integer) session.getAttribute("user_id");
        String reviewIdStr = request.getParameter("reviewId");
        String commentText = request.getParameter("commentText");
        
        // Validasi input
        if (reviewIdStr == null || reviewIdStr.trim().isEmpty()) {
            sendError(response, "レビューIDが必要です");
            return;
        }
        
        if (commentText == null || commentText.trim().isEmpty()) {
            sendError(response, "コメント内容を入力してください");
            return;
        }
        
        if (commentText.trim().length() > 1000) {
            sendError(response, "コメントは1000文字以内で入力してください");
            return;
        }
        
        Connection conn = null;
        PreparedStatement checkStmt = null;
        PreparedStatement sellerStmt = null;
        PreparedStatement insertStmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            int reviewId = Integer.parseInt(reviewIdStr);
            
            // 1. Check if review exists and get product info
            String checkSql = "SELECT pr.product_id, pr.user_id as review_user_id, p.user_id as seller_id " +
                             "FROM product_reviews pr " +
                             "INNER JOIN products p ON pr.product_id = p.product_id " +
                             "WHERE pr.review_id = ? AND pr.status = 'approved'";
            checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setInt(1, reviewId);
            rs = checkStmt.executeQuery();
            
            if (!rs.next()) {
                sendError(response, "レビューが見つかりません");
                return;
            }
            
            int productId = rs.getInt("product_id");
            int reviewUserId = rs.getInt("review_user_id");
            int sellerId = rs.getInt("seller_id");
            
            // 2. Check if user is seller for this product
            boolean isSeller = (userId == sellerId);
            
            // 3. Check if user is replying to themselves
            boolean isReplyingToSelf = (userId == reviewUserId);
            
            // 4. Insert comment
            String insertSql = """
                INSERT INTO review_comments 
                (review_id, user_id, comment_text, is_seller_reply, status, created_at, updated_at) 
                VALUES (?, ?, ?, ?, 'active', NOW(), NOW())
                """;
            
            insertStmt = conn.prepareStatement(insertSql);
            insertStmt.setInt(1, reviewId);
            insertStmt.setInt(2, userId);
            insertStmt.setString(3, commentText.trim());
            insertStmt.setBoolean(4, isSeller);
            
            int rowsAffected = insertStmt.executeUpdate();
            
            if (rowsAffected > 0) {
                // 5. Update comment count
                updateCommentCount(conn, reviewId);
                
                // 6. Send notification to review author if not self-comment
                if (!isReplyingToSelf) {
                    sendCommentNotification(conn, reviewId, userId, commentText.trim(), productId);
                }
                
                Map<String, Object> responseData = new HashMap<>();
                responseData.put("success", true);
                responseData.put("message", "コメントを投稿しました");
                responseData.put("isSeller", isSeller);
                sendJsonResponse(response, responseData);
            } else {
                sendError(response, "コメントの投稿に失敗しました");
            }
            
        } catch (NumberFormatException e) {
            sendError(response, "無効なレビューID形式です");
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "データベースエラー: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "エラー: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs);
            DatabaseConnection.closeResources(checkStmt);
            DatabaseConnection.closeResources(sellerStmt);
            DatabaseConnection.closeResources(insertStmt);
            DatabaseConnection.closeResources(conn);
        }
    }
    
    // ==================== SEND COMMENT NOTIFICATION ====================
    private void sendCommentNotification(Connection conn, int reviewId, int commenterId, 
                                         String commentText, int productId) throws SQLException {
        
        // Get commenter username
        String commenterName = "ユーザー";
        String userSql = "SELECT username FROM users WHERE user_id = ?";
        try (PreparedStatement userStmt = conn.prepareStatement(userSql)) {
            userStmt.setInt(1, commenterId);
            try (ResultSet rs = userStmt.executeQuery()) {
                if (rs.next()) {
                    commenterName = rs.getString("username");
                }
            }
        }
        
        // Get review author
        String reviewUserSql = "SELECT user_id FROM product_reviews WHERE review_id = ?";
        int reviewAuthorId = 0;
        try (PreparedStatement reviewStmt = conn.prepareStatement(reviewUserSql)) {
            reviewStmt.setInt(1, reviewId);
            try (ResultSet rs = reviewStmt.executeQuery()) {
                if (rs.next()) {
                    reviewAuthorId = rs.getInt("user_id");
                }
            }
        }
        
        // Send notification only if not commenting on own review
        if (reviewAuthorId != commenterId) {
            String notificationSql = """
                INSERT INTO notifications 
                (user_id, type, title, message, action_url, reference_type, reference_id, is_read, created_at)
                VALUES (?, 'review', 'レビューにコメントがつきました', ?, ?, 'review', ?, 0, NOW())
                """;
            
            String message = commenterName + "さんがあなたのレビューにコメントしました: " + 
                           (commentText.length() > 50 ? commentText.substring(0, 50) + "..." : commentText);
            String actionUrl = "/KaruruFleaMarket/product-details.jsp?id=" + productId;
            
            try (PreparedStatement stmt = conn.prepareStatement(notificationSql)) {
                stmt.setInt(1, reviewAuthorId);
                stmt.setString(2, message);
                stmt.setString(3, actionUrl);
                stmt.setInt(4, reviewId);
                stmt.executeUpdate();
            }
        }
    }
    
    // ==================== MARK REVIEW AS HELPFUL ====================
    private void markReviewHelpful(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        int userId = (Integer) session.getAttribute("user_id");
        String reviewIdStr = request.getParameter("reviewId");
        
        if (reviewIdStr == null) {
            sendError(response, "レビューIDが必要です");
            return;
        }
        
        Connection conn = null;
        PreparedStatement checkStmt = null;
        PreparedStatement updateStmt = null;
        PreparedStatement logStmt = null;
        ResultSet rs = null;
        
        try {
            int reviewId = Integer.parseInt(reviewIdStr);
            conn = DatabaseConnection.getConnection();
            
            // Check if user already voted
            String checkSql = """
                SELECT COUNT(*) FROM activity_logs 
                WHERE user_id = ? AND entity_type = 'review' 
                AND entity_id = ? AND action = 'helpful_vote'
                """;
            checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setInt(1, userId);
            checkStmt.setInt(2, reviewId);
            rs = checkStmt.executeQuery();
            
            if (rs.next() && rs.getInt(1) > 0) {
                sendError(response, "既に投票済みです");
                return;
            }
            
            // Update helpful count
            String updateSql = "UPDATE product_reviews SET helpful_count = helpful_count + 1 WHERE review_id = ?";
            updateStmt = conn.prepareStatement(updateSql);
            updateStmt.setInt(1, reviewId);
            updateStmt.executeUpdate();
            
            // Log the activity
            String logSql = """
                INSERT INTO activity_logs 
                (user_id, action, entity_type, entity_id, ip_address, created_at) 
                VALUES (?, 'helpful_vote', 'review', ?, ?, NOW())
                """;
            logStmt = conn.prepareStatement(logSql);
            logStmt.setInt(1, userId);
            logStmt.setInt(2, reviewId);
            logStmt.setString(3, request.getRemoteAddr());
            logStmt.executeUpdate();
            
            // Get updated count
            String countSql = "SELECT helpful_count FROM product_reviews WHERE review_id = ?";
            try (PreparedStatement countStmt = conn.prepareStatement(countSql)) {
                countStmt.setInt(1, reviewId);
                try (ResultSet countRs = countStmt.executeQuery()) {
                    int newCount = 0;
                    if (countRs.next()) {
                        newCount = countRs.getInt("helpful_count");
                    }
                    
                    Map<String, Object> responseData = new HashMap<>();
                    responseData.put("success", true);
                    responseData.put("helpfulCount", newCount);
                    responseData.put("message", "参考になったに投票しました");
                    sendJsonResponse(response, responseData);
                }
            }
            
        } catch (NumberFormatException e) {
            sendError(response, "無効なレビューID形式です");
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "データベースエラー: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "エラー: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs);
            DatabaseConnection.closeResources(checkStmt);
            DatabaseConnection.closeResources(updateStmt);
            DatabaseConnection.closeResources(logStmt);
            DatabaseConnection.closeResources(conn);
        }
    }
    
    // ==================== UPDATE PRODUCT RATING ====================
    private void updateProductRating(Connection conn, int productId) throws SQLException {
        String sql = """
            UPDATE products p
            SET 
                rating_avg = COALESCE((SELECT AVG(rating) FROM product_reviews WHERE product_id = ? AND status = 'approved'), 0),
                rating_count = COALESCE((SELECT COUNT(*) FROM product_reviews WHERE product_id = ? AND status = 'approved'), 0)
            WHERE p.product_id = ?
            """;
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, productId);
            stmt.setInt(2, productId);
            stmt.setInt(3, productId);
            stmt.executeUpdate();
        }
    }
    
    // ==================== UPDATE COMMENT COUNT ====================
    private void updateCommentCount(Connection conn, int reviewId) throws SQLException {
        String sql = """
            UPDATE product_reviews 
            SET comment_count = COALESCE(( 
                SELECT COUNT(*) FROM review_comments 
                WHERE review_id = ? AND status = 'active'
            ), 0)
            WHERE review_id = ?
            """;
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, reviewId);
            stmt.setInt(2, reviewId);
            stmt.executeUpdate();
        }
    }
    
    // ==================== HELPER METHOD: MAP REVIEW RESULTSET ====================
    private Map<String, Object> mapReviewResultSet(ResultSet rs) throws SQLException {
        Map<String, Object> review = new HashMap<>();
        review.put("review_id", rs.getInt("review_id"));
        review.put("product_id", rs.getInt("product_id"));
        review.put("user_id", rs.getInt("user_id"));
        review.put("rating", rs.getInt("rating"));
        review.put("review_text", rs.getString("review_text"));
        review.put("images", rs.getString("images"));
        review.put("is_verified_purchase", rs.getBoolean("is_verified_purchase"));
        review.put("seller_reply", rs.getString("seller_reply"));
        review.put("seller_replied_at", rs.getTimestamp("seller_replied_at"));
        review.put("helpful_count", rs.getInt("helpful_count"));
        review.put("comment_count", rs.getInt("comment_count"));
        review.put("status", rs.getString("status"));
        review.put("created_at", rs.getTimestamp("created_at"));
        review.put("updated_at", rs.getTimestamp("updated_at"));
        
        Map<String, Object> reviewer = new HashMap<>();
        reviewer.put("user_id", rs.getInt("user_id"));
        reviewer.put("username", rs.getString("username"));
        reviewer.put("full_name", rs.getString("full_name"));
        reviewer.put("avatar_url", rs.getString("avatar_url"));
        
        review.put("reviewer", reviewer);
        return review;
    }
    
    // ==================== GET PRODUCT ====================
    private Map<String, Object> getProduct(Connection conn, int productId) throws SQLException {
        String sql = """
            SELECT 
                p.product_id, p.product_name, p.slug, p.description, p.price, 
                p.original_price, p.discount_percentage, p.stock_quantity, 
                p.min_order, p.weight, p.condition, 
                p.is_rental, p.rental_price_daily, p.rental_price_weekly, 
                p.rental_price_monthly, p.rental_deposit, 
                p.status, p.views_count, p.likes_count, p.sold_count, 
                p.rating_avg, p.rating_count, p.featured, p.featured_until,
                p.image_url, p.is_negotiable, p.created_at, p.updated_at,
                u.user_id as seller_id, u.username as seller_username, 
                u.full_name as seller_name, 
                u.phone as seller_phone, u.avatar_url as seller_avatar,
                u.bio as seller_bio, u.is_verified as seller_verified,
                u.is_seller as is_seller,
                u.created_at as seller_joined_at
            FROM products p 
            LEFT JOIN users u ON p.user_id = u.user_id 
            WHERE p.product_id = ? AND p.status != 'deleted'
            """;
        
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, productId);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                Map<String, Object> product = new HashMap<>();
                
                // Product info
                product.put("product_id", rs.getInt("product_id"));
                product.put("product_name", rs.getString("product_name"));
                product.put("slug", rs.getString("slug"));
                product.put("description", rs.getString("description"));
                product.put("price", rs.getDouble("price"));
                
                double originalPrice = rs.getDouble("original_price");
                product.put("original_price", rs.wasNull() ? null : originalPrice);
                
                product.put("discount_percentage", rs.getInt("discount_percentage"));
                product.put("stock_quantity", rs.getInt("stock_quantity"));
                product.put("min_order", rs.getInt("min_order"));
                
                double weight = rs.getDouble("weight");
                product.put("weight", rs.wasNull() ? null : weight);
                
                product.put("condition", rs.getString("condition"));
                product.put("is_rental", rs.getBoolean("is_rental"));
                
                // Rental prices
                double dailyPrice = rs.getDouble("rental_price_daily");
                product.put("rental_price_daily", rs.wasNull() ? null : dailyPrice);
                
                double weeklyPrice = rs.getDouble("rental_price_weekly");
                product.put("rental_price_weekly", rs.wasNull() ? null : weeklyPrice);
                
                double monthlyPrice = rs.getDouble("rental_price_monthly");
                product.put("rental_price_monthly", rs.wasNull() ? null : monthlyPrice);
                
                double deposit = rs.getDouble("rental_deposit");
                product.put("rental_deposit", rs.wasNull() ? null : deposit);
                
                // Status and stats
                product.put("status", rs.getString("status"));
                product.put("views_count", rs.getInt("views_count"));
                product.put("likes_count", rs.getInt("likes_count"));
                product.put("sold_count", rs.getInt("sold_count"));
                product.put("rating_avg", rs.getDouble("rating_avg"));
                product.put("rating_count", rs.getInt("rating_count"));
                product.put("featured", rs.getBoolean("featured"));
                product.put("featured_until", rs.getTimestamp("featured_until"));
                product.put("image_url", rs.getString("image_url"));
                product.put("is_negotiable", rs.getBoolean("is_negotiable"));
                product.put("created_at", rs.getTimestamp("created_at"));
                product.put("updated_at", rs.getTimestamp("updated_at"));
                
                // Seller info
                product.put("seller_id", rs.getInt("seller_id"));
                product.put("seller_name", rs.getString("seller_name"));
                product.put("seller_username", rs.getString("seller_username"));
                product.put("seller_phone", rs.getString("seller_phone"));
                product.put("seller_avatar", rs.getString("seller_avatar"));
                product.put("seller_bio", rs.getString("seller_bio"));
                product.put("seller_verified", rs.getBoolean("seller_verified"));
                product.put("is_seller", rs.getBoolean("is_seller"));
                product.put("seller_joined_at", rs.getTimestamp("seller_joined_at"));
                
                return product;
            }
            
            return null;
            
        } finally {
            DatabaseConnection.closeResources(rs, stmt);
        }
    }
    
    // ==================== GET PRODUCT SPECIFICATIONS ====================
    private List<Map<String, Object>> getProductSpecifications(Connection conn, int productId) 
            throws SQLException {
        
        List<Map<String, Object>> specifications = new ArrayList<>();
        
        String sql = """
            SELECT spec_id, spec_name, spec_value, display_order 
            FROM product_specifications 
            WHERE product_id = ? 
            ORDER BY display_order ASC, spec_id ASC
            """;
        
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, productId);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> spec = new HashMap<>();
                spec.put("spec_id", rs.getInt("spec_id"));
                spec.put("spec_name", rs.getString("spec_name"));
                spec.put("spec_value", rs.getString("spec_value"));
                spec.put("display_order", rs.getInt("display_order"));
                specifications.add(spec);
            }
            
        } catch (SQLException e) {
            System.err.println("Error loading specifications: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt);
        }
        
        return specifications;
    }
    
    // ==================== GET PRODUCT IMAGES ====================
    private List<Map<String, Object>> getProductImages(Connection conn, int productId) 
            throws SQLException {
        
        List<Map<String, Object>> images = new ArrayList<>();
        
        String sql = """
            SELECT image_id, image_url, image_order, is_primary, created_at
            FROM product_images 
            WHERE product_id = ? 
            ORDER BY is_primary DESC, image_order ASC, image_id ASC
            """;
        
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, productId);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> image = new HashMap<>();
                image.put("image_id", rs.getInt("image_id"));
                image.put("image_url", rs.getString("image_url"));
                image.put("image_order", rs.getInt("image_order"));
                image.put("is_primary", rs.getBoolean("is_primary"));
                image.put("created_at", rs.getTimestamp("created_at"));
                images.add(image);
            }
            
        } catch (SQLException e) {
            System.err.println("Error loading images: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt);
        }
        
        return images;
    }
    
    // ==================== GET PRODUCT CATEGORIES ====================
    private List<Map<String, Object>> getProductCategories(Connection conn, int productId) 
            throws SQLException {
        
        List<Map<String, Object>> categories = new ArrayList<>();
        
        String sql = """
            SELECT c.category_id, c.category_name, c.slug, c.description, c.icon_url
            FROM categories c
            INNER JOIN product_categories pc ON c.category_id = pc.category_id
            WHERE pc.product_id = ?
            ORDER BY c.display_order ASC
            """;
        
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, productId);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> category = new HashMap<>();
                category.put("category_id", rs.getInt("category_id"));
                category.put("category_name", rs.getString("category_name"));
                category.put("slug", rs.getString("slug"));
                category.put("description", rs.getString("description"));
                category.put("icon_url", rs.getString("icon_url"));
                categories.add(category);
            }
            
        } catch (SQLException e) {
            System.err.println("Error loading categories: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt);
        }
        
        return categories;
    }
    
    // ==================== GET RELATED PRODUCTS ====================
    private List<Map<String, Object>> getRelatedProducts(Connection conn, int productId) 
            throws SQLException {
        
        List<Map<String, Object>> relatedProducts = new ArrayList<>();
        
        String sql = """
            SELECT DISTINCT p.product_id, p.product_name, p.price, 
                   p.original_price, p.discount_percentage,
                   p.image_url, p.status, p.is_rental, 
                   p.rating_avg, p.rating_count
            FROM products p 
            INNER JOIN product_categories pc1 ON p.product_id = pc1.product_id 
            WHERE pc1.category_id IN ( 
                SELECT category_id FROM product_categories WHERE product_id = ? 
            ) 
            AND p.product_id != ? 
            AND p.status = 'available' 
            ORDER BY p.views_count DESC, p.created_at DESC 
            LIMIT 8
            """;
        
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, productId);
            stmt.setInt(2, productId);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> product = new HashMap<>();
                product.put("product_id", rs.getInt("product_id"));
                product.put("product_name", rs.getString("product_name"));
                product.put("price", rs.getDouble("price"));
                
                double originalPrice = rs.getDouble("original_price");
                product.put("original_price", rs.wasNull() ? null : originalPrice);
                
                product.put("discount_percentage", rs.getInt("discount_percentage"));
                product.put("image_url", rs.getString("image_url"));
                product.put("status", rs.getString("status"));
                product.put("is_rental", rs.getBoolean("is_rental"));
                product.put("rating_avg", rs.getDouble("rating_avg"));
                product.put("rating_count", rs.getInt("rating_count"));
                
                relatedProducts.add(product);
            }
            
        } catch (SQLException e) {
            System.err.println("Error loading related products: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt);
        }
        
        return relatedProducts;
    }
    
    // ==================== TRACK PRODUCT VIEW ====================
    private void trackProductViewSync(Connection conn, HttpServletRequest request, int productId) {
        PreparedStatement stmt = null;
        PreparedStatement updateStmt = null;
        
        try {
            HttpSession session = request.getSession(false);
            Integer userId = null;
            if (session != null) {
                userId = (Integer) session.getAttribute("user_id");
            }
            
            String ipAddress = request.getRemoteAddr();
            
            // Insert activity log
            String sql = """
                INSERT INTO activity_logs 
                (user_id, action, entity_type, entity_id, ip_address, created_at) 
                VALUES (?, 'product_view', 'product', ?, ?, NOW())
                """;
            
            stmt = conn.prepareStatement(sql);
            if (userId != null) {
                stmt.setInt(1, userId);
            } else {
                stmt.setNull(1, java.sql.Types.INTEGER);
            }
            stmt.setInt(2, productId);
            stmt.setString(3, ipAddress);
            
            stmt.executeUpdate();
            
            // Update product views count
            String updateSql = """
                UPDATE products 
                SET views_count = views_count + 1 
                WHERE product_id = ?
                """;
            
            updateStmt = conn.prepareStatement(updateSql);
            updateStmt.setInt(1, productId);
            updateStmt.executeUpdate();
            
            System.out.println("Product view tracked: " + productId);
            
        } catch (SQLException e) {
            System.err.println("Error tracking view: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(updateStmt);
            DatabaseConnection.closeResources(stmt);
        }
    }
    
    // ==================== UTILITY METHODS ====================
    private void sendJsonResponse(HttpServletResponse response, Object data) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        PrintWriter out = response.getWriter();
        out.print(gson.toJson(data));
        out.flush();
    }
    
    private void sendError(HttpServletResponse response, String message) throws IOException {
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
    
    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setStatus(HttpServletResponse.SC_OK);
    }
}