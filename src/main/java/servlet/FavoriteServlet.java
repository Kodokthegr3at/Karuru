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

@WebServlet({"/FavoriteServlet", "/Favorite"})
public class FavoriteServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        FilterEncodingUTF8.configureUTF8ForJSON(request, response);
        
        String action = request.getParameter("action");
        
        try {
            if (action == null || action.isEmpty()) {
                sendError(response, "Action parameter is required");
                return;
            }
            
            switch (action) {
                case "getFavorites":
                    getFavorites(request, response);
                    break;
                case "getCount":
                    getFavoriteCount(request, response);
                    break;
                case "getFavoriteStatus":
                    getFavoriteStatus(request, response);
                    break;
                case "getFavoriteProducts":
                    getFavoriteProducts(request, response);
                    break;
                case "getRecentFavorites":
                    getRecentFavorites(request, response);
                    break;
                case "isFavorite":
                case "check":
                    checkIsFavorite(request, response);
                    break;
                default:
                    sendError(response, "Invalid action: " + action);
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
        
        // Try to get action from parameter first, then from JSON body
        String action = request.getParameter("action");
        
        // If action is not in parameter, try to read from JSON body
        if (action == null || action.isEmpty()) {
            try {
                String body = request.getReader().lines().reduce("", (accumulator, actual) -> accumulator + actual);
                if (body != null && !body.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> jsonBody = gson.fromJson(body, Map.class);
                    if (jsonBody != null && jsonBody.containsKey("action")) {
                        action = (String) jsonBody.get("action");
                    }
                }
            } catch (Exception e) {
                // Ignore JSON parsing errors, will use parameter
            }
        }
        
        try {
            if (action == null || action.isEmpty()) {
                sendError(response, "Action parameter is required");
                return;
            }
            
            switch (action) {
                case "toggle":
                    toggleFavorite(request, response);
                    break;
                case "add":
                    addToFavorites(request, response);
                    break;
                case "remove":
                    removeFromFavorites(request, response);
                    break;
                case "clearAll":
                    clearAllFavorites(request, response);
                    break;
                case "batchAdd":
                    batchAddFavorites(request, response);
                    break;
                default:
                    sendError(response, "Invalid action: " + action);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage());
        }
    }
    
    // ==================== LOG FAVORITE ACTIVITY ====================
    private void logFavoriteActivity(int userId, int productId, String action) {
        try {
            // Log ke activity logs
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = null;
            
            String activityAction = "add".equals(action) ? "favorite_add" : "favorite_remove";
            
            String sql = """
                INSERT INTO activity_logs 
                (user_id, action, entity_type, entity_id, details, created_at) 
                VALUES (?, ?, 'product', ?, ?, NOW())
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            stmt.setString(2, activityAction);
            stmt.setInt(3, productId);
            stmt.setString(4, "{\"action\": \"" + action + "\"}");
            stmt.executeUpdate();
            
            stmt.close();
            conn.close();
            
        } catch (Exception e) {
            System.err.println("Failed to log favorite activity: " + e.getMessage());
        }
    }
    
    // ==================== GET FAVORITES ====================
    private void getFavorites(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            sendJsonResponse(response, new ArrayList<>());
            return;
        }
        
        Integer userId = (Integer) session.getAttribute("user_id");
        List<Map<String, Object>> favorites = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = """
                SELECT 
                    p.product_id, p.product_name, p.description, p.price, 
                    p.original_price, p.discount_percentage, p.stock_quantity, 
                    p.status, p.image_url, p.is_rental, p.condition, 
                    p.rental_price_daily, p.rental_price_weekly, p.rental_price_monthly, 
                    p.is_negotiable, 
                    p.views_count, p.likes_count, p.sold_count, p.rating_avg, p.rating_count, 
                    p.user_id as seller_id, p.created_at, u.username as seller_name,
                    f.added_at as favorite_date
                FROM user_favorites f
                INNER JOIN products p ON f.product_id = p.product_id
                LEFT JOIN users u ON p.user_id = u.user_id
                WHERE f.user_id = ? AND p.status IN ('available', 'reserved')
                ORDER BY f.added_at DESC
                LIMIT 100
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> product = mapProductFromResultSet(rs);
                
                // Get images for this product
                int productId = (Integer) product.get("product_id");
                List<String> images = getProductImages(conn, productId);
                product.put("images", images);
                
                favorites.add(product);
            }
            
            System.out.println("✅ Favorites found: " + favorites.size() + " for user: " + userId);
            
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("❌ SQL Error in getFavorites: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
        
        sendJsonResponse(response, favorites);
    }
    
    // ==================== GET FAVORITE PRODUCTS (FOR INDEX PAGE) ====================
    private void getFavoriteProducts(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            sendJsonResponse(response, new ArrayList<>());
            return;
        }
        
        Integer userId = (Integer) session.getAttribute("user_id");
        List<Map<String, Object>> favorites = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = """
                SELECT 
                    p.product_id, p.product_name, p.description, p.price, 
                    p.original_price, p.discount_percentage, p.stock_quantity, 
                    p.status, p.image_url, p.is_rental, p.condition, 
                    p.rental_price_daily, p.rental_price_weekly, p.rental_price_monthly, 
                    p.is_negotiable, 
                    p.views_count, p.likes_count, p.sold_count, p.rating_avg, p.rating_count, 
                    p.user_id as seller_id, p.created_at, u.username as seller_name,
                    f.added_at as favorite_date
                FROM user_favorites f
                INNER JOIN products p ON f.product_id = p.product_id
                LEFT JOIN users u ON p.user_id = u.user_id
                WHERE f.user_id = ? 
                ORDER BY f.added_at DESC
                LIMIT 20
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> product = mapProductFromResultSet(rs);
                
                // Get images for this product
                int productId = (Integer) product.get("product_id");
                List<String> images = getProductImages(conn, productId);
                product.put("images", images);
                
                favorites.add(product);
            }
            
            System.out.println("✅ Favorite products for index: " + favorites.size());
            
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("❌ Error getting favorite products: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
        
        sendJsonResponse(response, favorites);
    }
    
    // ==================== GET RECENT FAVORITES ====================
    private void getRecentFavorites(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            sendJsonResponse(response, new ArrayList<>());
            return;
        }
        
        Integer userId = (Integer) session.getAttribute("user_id");
        List<Map<String, Object>> favorites = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = """
                SELECT 
                    p.product_id, p.product_name, p.price, p.image_url, 
                    p.status, p.condition, p.created_at,
                    u.username as seller_name,
                    f.added_at as favorite_date
                FROM user_favorites f
                INNER JOIN products p ON f.product_id = p.product_id
                LEFT JOIN users u ON p.user_id = u.user_id
                WHERE f.user_id = ?
                ORDER BY f.added_at DESC
                LIMIT 10
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> favorite = new HashMap<>();
                favorite.put("product_id", rs.getInt("product_id"));
                favorite.put("product_name", rs.getString("product_name"));
                favorite.put("price", rs.getDouble("price"));
                favorite.put("image_url", rs.getString("image_url"));
                favorite.put("status", rs.getString("status"));
                favorite.put("condition", rs.getString("condition"));
                favorite.put("seller_name", rs.getString("seller_name"));
                favorite.put("favorite_date", rs.getTimestamp("favorite_date"));
                favorites.add(favorite);
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
        
        sendJsonResponse(response, favorites);
    }
    
    // ==================== GET FAVORITE COUNT ====================
    private void getFavoriteCount(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            sendJsonResponse(response, Map.of("count", 0));
            return;
        }
        
        Integer userId = (Integer) session.getAttribute("user_id");
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = """
                SELECT COUNT(*) as favorite_count 
                FROM user_favorites f
                INNER JOIN products p ON f.product_id = p.product_id
                WHERE f.user_id = ? AND p.status IN ('available', 'reserved')
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            
            int count = 0;
            if (rs.next()) {
                count = rs.getInt("favorite_count");
            }
            
            sendJsonResponse(response, Map.of("count", count));
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendJsonResponse(response, Map.of("count", 0));
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== GET FAVORITE STATUS ====================
    private void getFavoriteStatus(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            sendJsonResponse(response, Map.of("success", true, "favorites", new ArrayList<>()));
            return;
        }
        
        Integer userId = (Integer) session.getAttribute("user_id");
        List<Integer> favoriteProductIds = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = "SELECT product_id FROM user_favorites WHERE user_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                favoriteProductIds.add(rs.getInt("product_id"));
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("favorites", favoriteProductIds);
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("favorites", new ArrayList<>());
            sendJsonResponse(response, result);
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== CHECK IF PRODUCT IS FAVORITE ====================
    private void checkIsFavorite(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            sendJsonResponse(response, Map.of("isFavorite", false));
            return;
        }
        
        // Try to get productId from parameter (support both productId and product_id)
        String productIdStr = request.getParameter("productId");
        if (productIdStr == null || productIdStr.isEmpty()) {
            productIdStr = request.getParameter("product_id");
        }
        if (productIdStr == null || productIdStr.isEmpty()) {
            sendError(response, "Product ID is required");
            return;
        }
        
        Integer userId = (Integer) session.getAttribute("user_id");
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            int productId = Integer.parseInt(productIdStr);
            conn = DatabaseConnection.getConnection();
            
            String sql = "SELECT 1 FROM user_favorites WHERE user_id = ? AND product_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            stmt.setInt(2, productId);
            rs = stmt.executeQuery();
            
            boolean isFavorite = rs.next();
            
            Map<String, Object> result = new HashMap<>();
            result.put("isFavorite", isFavorite);
            result.put("productId", productId);
            
            sendJsonResponse(response, result);
            
        } catch (NumberFormatException e) {
            sendError(response, "Invalid product ID format");
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== TOGGLE FAVORITE (MAIN FUNCTION) ====================
    private void toggleFavorite(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            sendJsonResponse(response, Map.of(
                "success", false,
                "message", "ログインが必要です",
                "isFavorite", false
            ));
            return;
        }
        
        String productIdStr = request.getParameter("productId");
        if (productIdStr == null || productIdStr.isEmpty()) {
            sendError(response, "Product ID is required");
            return;
        }
        
        Integer userId = (Integer) session.getAttribute("user_id");
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            int productId = Integer.parseInt(productIdStr);
            conn = DatabaseConnection.getConnection();
            
            // Check if product exists
            String checkProductSql = "SELECT 1 FROM products WHERE product_id = ?";
            stmt = conn.prepareStatement(checkProductSql);
            stmt.setInt(1, productId);
            rs = stmt.executeQuery();
            
            if (!rs.next()) {
                sendJsonResponse(response, Map.of(
                    "success", false,
                    "message", "商品が見つかりません",
                    "isFavorite", false
                ));
                return;
            }
            rs.close();
            stmt.close();
            
            // Check if already favorited
            String checkSql = "SELECT 1 FROM user_favorites WHERE user_id = ? AND product_id = ?";
            stmt = conn.prepareStatement(checkSql);
            stmt.setInt(1, userId);
            stmt.setInt(2, productId);
            rs = stmt.executeQuery();
            
            boolean isCurrentlyFavorite = rs.next();
            DatabaseConnection.closeResources(rs, stmt, null);
            
            Map<String, Object> result = new HashMap<>();
            
            if (isCurrentlyFavorite) {
                // Remove from favorites
                String deleteSql = "DELETE FROM user_favorites WHERE user_id = ? AND product_id = ?";
                stmt = conn.prepareStatement(deleteSql);
                stmt.setInt(1, userId);
                stmt.setInt(2, productId);
                int rowsAffected = stmt.executeUpdate();
                
                if (rowsAffected > 0) {
                    // Update product likes count
                    updateProductLikesCount(conn, productId, -1);
                    
                    // Log activity
                    logFavoriteActivity(userId, productId, "remove");
                    
                    result.put("success", true);
                    result.put("isFavorite", false);
                    result.put("message", "お気に入りから削除しました");
                    result.put("action", "removed");
                } else {
                    result.put("success", false);
                    result.put("isFavorite", true);
                    result.put("message", "削除に失敗しました");
                }
            } else {
                // Add to favorites
                String insertSql = "INSERT INTO user_favorites (user_id, product_id, added_at) VALUES (?, ?, NOW())";
                stmt = conn.prepareStatement(insertSql);
                stmt.setInt(1, userId);
                stmt.setInt(2, productId);
                int rowsAffected = stmt.executeUpdate();
                
                if (rowsAffected > 0) {
                    // Update product likes count
                    updateProductLikesCount(conn, productId, 1);
                    
                    // Log favorite activity
                    logFavoriteActivity(userId, productId, "add");
                    
                    result.put("success", true);
                    result.put("isFavorite", true);
                    result.put("message", "お気に入りに追加しました");
                    result.put("action", "added");
                } else {
                    result.put("success", false);
                    result.put("isFavorite", false);
                    result.put("message", "追加に失敗しました");
                }
            }
            
            // Get updated likes count
            int updatedLikesCount = getProductLikesCount(conn, productId);
            result.put("likesCount", updatedLikesCount);
            result.put("productId", productId);
            
            sendJsonResponse(response, result);
            
        } catch (NumberFormatException e) {
            sendJsonResponse(response, Map.of(
                "success", false,
                "message", "無効な商品IDです",
                "isFavorite", false
            ));
        } catch (SQLException e) {
            e.printStackTrace();
            sendJsonResponse(response, Map.of(
                "success", false,
                "message", "データベースエラー: " + e.getMessage(),
                "isFavorite", false
            ));
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== ADD TO FAVORITES ====================
    private void addToFavorites(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        // Try to get productId from parameter first, then from JSON body
        String productIdStr = request.getParameter("product_id");
        if (productIdStr == null || productIdStr.isEmpty()) {
            productIdStr = request.getParameter("productId");
        }
        
        // If still not found, try to read from JSON body
        if (productIdStr == null || productIdStr.isEmpty()) {
            try {
                String body = request.getReader().lines().reduce("", (accumulator, actual) -> accumulator + actual);
                if (body != null && !body.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> jsonBody = gson.fromJson(body, Map.class);
                    if (jsonBody != null) {
                        Object productIdObj = jsonBody.get("product_id");
                        if (productIdObj == null) {
                            productIdObj = jsonBody.get("productId");
                        }
                        if (productIdObj != null) {
                            productIdStr = productIdObj.toString();
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore JSON parsing errors
            }
        }
        
        if (productIdStr == null || productIdStr.isEmpty()) {
            sendError(response, "Product ID is required");
            return;
        }
        
        Integer userId = (Integer) session.getAttribute("user_id");
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            int productId = Integer.parseInt(productIdStr);
            conn = DatabaseConnection.getConnection();
            
            // Check if already favorited
            String checkSql = "SELECT 1 FROM user_favorites WHERE user_id = ? AND product_id = ?";
            stmt = conn.prepareStatement(checkSql);
            stmt.setInt(1, userId);
            stmt.setInt(2, productId);
            ResultSet rs = stmt.executeQuery();
            
            Map<String, Object> result = new HashMap<>();
            
            if (rs.next()) {
                result.put("success", true);
                result.put("message", "既にお気に入りに登録されています");
                result.put("isFavorite", true);
            } else {
                // Add to favorites
                String insertSql = "INSERT INTO user_favorites (user_id, product_id, added_at) VALUES (?, ?, NOW())";
                stmt = conn.prepareStatement(insertSql);
                stmt.setInt(1, userId);
                stmt.setInt(2, productId);
                int rowsAffected = stmt.executeUpdate();
                
                if (rowsAffected > 0) {
                    // Update product likes count
                    updateProductLikesCount(conn, productId, 1);
                    
                    // Log favorite activity
                    logFavoriteActivity(userId, productId, "add");
                    
                    result.put("success", true);
                    result.put("message", "お気に入りに追加しました");
                    result.put("isFavorite", true);
                } else {
                    result.put("success", false);
                    result.put("message", "追加に失敗しました");
                    result.put("isFavorite", false);
                }
            }
            
            rs.close();
            sendJsonResponse(response, result);
            
        } catch (NumberFormatException e) {
            sendError(response, "Invalid product ID format");
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(null, stmt, conn);
        }
    }
    
    // ==================== REMOVE FROM FAVORITES ====================
    private void removeFromFavorites(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        // Try to get productId from parameter first, then from JSON body
        String productIdStr = request.getParameter("product_id");
        if (productIdStr == null || productIdStr.isEmpty()) {
            productIdStr = request.getParameter("productId");
        }
        
        // If still not found, try to read from JSON body
        if (productIdStr == null || productIdStr.isEmpty()) {
            try {
                String body = request.getReader().lines().reduce("", (accumulator, actual) -> accumulator + actual);
                if (body != null && !body.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> jsonBody = gson.fromJson(body, Map.class);
                    if (jsonBody != null) {
                        Object productIdObj = jsonBody.get("product_id");
                        if (productIdObj == null) {
                            productIdObj = jsonBody.get("productId");
                        }
                        if (productIdObj != null) {
                            productIdStr = productIdObj.toString();
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore JSON parsing errors
            }
        }
        
        if (productIdStr == null || productIdStr.isEmpty()) {
            sendError(response, "Product ID is required");
            return;
        }
        
        Integer userId = (Integer) session.getAttribute("user_id");
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            int productId = Integer.parseInt(productIdStr);
            conn = DatabaseConnection.getConnection();
            
            String deleteSql = "DELETE FROM user_favorites WHERE user_id = ? AND product_id = ?";
            stmt = conn.prepareStatement(deleteSql);
            stmt.setInt(1, userId);
            stmt.setInt(2, productId);
            int rowsAffected = stmt.executeUpdate();
            
            Map<String, Object> result = new HashMap<>();
            
            if (rowsAffected > 0) {
                // Update product likes count
                updateProductLikesCount(conn, productId, -1);
                
                result.put("success", true);
                result.put("message", "お気に入りから削除しました");
            } else {
                result.put("success", false);
                result.put("message", "削除に失敗しました");
            }
            
            sendJsonResponse(response, result);
            
        } catch (NumberFormatException e) {
            sendError(response, "Invalid product ID format");
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(null, stmt, conn);
        }
    }
    
    // ==================== BATCH ADD FAVORITES ====================
    private void batchAddFavorites(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        String productIdsStr = request.getParameter("productIds");
        if (productIdsStr == null || productIdsStr.isEmpty()) {
            sendError(response, "Product IDs are required");
            return;
        }
        
        Integer userId = (Integer) session.getAttribute("user_id");
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            String[] productIdArray = productIdsStr.split(",");
            conn = DatabaseConnection.getConnection();
            
            int addedCount = 0;
            List<Integer> addedProductIds = new ArrayList<>();
            
            for (String productIdStr : productIdArray) {
                try {
                    int productId = Integer.parseInt(productIdStr.trim());
                    
                    // Check if already favorited
                    String checkSql = "SELECT 1 FROM user_favorites WHERE user_id = ? AND product_id = ?";
                    stmt = conn.prepareStatement(checkSql);
                    stmt.setInt(1, userId);
                    stmt.setInt(2, productId);
                    ResultSet rs = stmt.executeQuery();
                    
                    if (!rs.next()) {
                        // Add to favorites
                        String insertSql = "INSERT INTO user_favorites (user_id, product_id, added_at) VALUES (?, ?, NOW())";
                        stmt = conn.prepareStatement(insertSql);
                        stmt.setInt(1, userId);
                        stmt.setInt(2, productId);
                        int rowsAffected = stmt.executeUpdate();
                        
                        if (rowsAffected > 0) {
                            addedCount++;
                            addedProductIds.add(productId);
                            updateProductLikesCount(conn, productId, 1);
                        }
                    }
                    
                    rs.close();
                    
                } catch (NumberFormatException e) {
                    // Skip invalid product IDs
                    continue;
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("addedCount", addedCount);
            result.put("addedProductIds", addedProductIds);
            result.put("message", addedCount + "件のお気に入りを追加しました");
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(null, stmt, conn);
        }
    }
    
    // ==================== CLEAR ALL FAVORITES ====================
    private void clearAllFavorites(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        Integer userId = (Integer) session.getAttribute("user_id");
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            // First get all product IDs to update likes count
            String selectSql = "SELECT product_id FROM user_favorites WHERE user_id = ?";
            stmt = conn.prepareStatement(selectSql);
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            
            List<Integer> productIds = new ArrayList<>();
            while (rs.next()) {
                productIds.add(rs.getInt("product_id"));
            }
            rs.close();
            
            // Delete all favorites
            String deleteSql = "DELETE FROM user_favorites WHERE user_id = ?";
            stmt = conn.prepareStatement(deleteSql);
            stmt.setInt(1, userId);
            int rowsAffected = stmt.executeUpdate();
            
            Map<String, Object> result = new HashMap<>();
            
            if (rowsAffected > 0) {
                // Update likes count for all products
                for (int productId : productIds) {
                    updateProductLikesCount(conn, productId, -1);
                }
                
                result.put("success", true);
                result.put("message", "すべてのお気に入りを削除しました");
                result.put("count", rowsAffected);
            } else {
                result.put("success", true);
                result.put("message", "お気に入りはありません");
                result.put("count", 0);
            }
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(null, stmt, conn);
        }
    }
    
    // ==================== UPDATE PRODUCT LIKES COUNT ====================
    private void updateProductLikesCount(Connection conn, int productId, int change) throws SQLException {
        PreparedStatement stmt = null;
        try {
            String sql = "UPDATE products SET likes_count = GREATEST(0, likes_count + ?) WHERE product_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, change);
            stmt.setInt(2, productId);
            stmt.executeUpdate();
        } finally {
            if (stmt != null) {
                stmt.close();
            }
        }
    }
    
    // ==================== GET PRODUCT LIKES COUNT ====================
    private int getProductLikesCount(Connection conn, int productId) throws SQLException {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            String sql = "SELECT likes_count FROM products WHERE product_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, productId);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt("likes_count");
            }
            return 0;
        } finally {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
        }
    }
    
    // ==================== MAP PRODUCT FROM RESULTSET ====================
    private Map<String, Object> mapProductFromResultSet(ResultSet rs) throws SQLException {
        Map<String, Object> product = new HashMap<>();
        
        product.put("product_id", rs.getInt("product_id"));
        product.put("product_name", rs.getString("product_name"));
        product.put("description", rs.getString("description"));
        product.put("price", rs.getDouble("price"));
        
        double originalPrice = rs.getDouble("original_price");
        product.put("original_price", rs.wasNull() ? null : originalPrice);
        
        product.put("discount_percentage", rs.getInt("discount_percentage"));
        product.put("stock_quantity", rs.getInt("stock_quantity"));
        product.put("status", rs.getString("status"));
        product.put("image_url", rs.getString("image_url"));
        product.put("is_rental", rs.getBoolean("is_rental"));
        product.put("condition", rs.getString("condition"));
        
        double dailyPrice = rs.getDouble("rental_price_daily");
        product.put("rental_price_daily", rs.wasNull() ? null : dailyPrice);
        
        double weeklyPrice = rs.getDouble("rental_price_weekly");
        product.put("rental_price_weekly", rs.wasNull() ? null : weeklyPrice);
        
        double monthlyPrice = rs.getDouble("rental_price_monthly");
        product.put("rental_price_monthly", rs.wasNull() ? null : monthlyPrice);
        
        // Check if is_negotiable column exists before accessing it
        try {
            product.put("is_negotiable", rs.getBoolean("is_negotiable"));
        } catch (SQLException e) {
            // Column might not exist in some queries, default to false
            product.put("is_negotiable", false);
        }
        
        product.put("views_count", rs.getInt("views_count"));
        product.put("likes_count", rs.getInt("likes_count"));
        product.put("sold_count", rs.getInt("sold_count"));
        product.put("rating_avg", rs.getDouble("rating_avg"));
        product.put("rating_count", rs.getInt("rating_count"));
        product.put("seller_id", rs.getInt("seller_id"));
        product.put("seller_name", rs.getString("seller_name"));
        product.put("created_at", rs.getTimestamp("created_at"));
        
        // Favorite specific fields
        if (hasColumn(rs, "favorite_date")) {
            product.put("favorite_date", rs.getTimestamp("favorite_date"));
        }
        
        // Determine status text for display
        String status = rs.getString("status");
        String statusText = "";
        String statusColor = "";
        
        switch (status) {
            case "available":
                statusText = "販売中";
                statusColor = "success";
                break;
            case "sold":
                statusText = "売り切れ";
                statusColor = "danger";
                break;
            case "rented":
                statusText = "レンタル中";
                statusColor = "warning";
                break;
            case "reserved":
                statusText = "予約中";
                statusColor = "info";
                break;
            default:
                statusText = status;
                statusColor = "secondary";
        }
        
        product.put("status_text", statusText);
        product.put("status_color", statusColor);
        
        return product;
    }
    
    // ==================== GET PRODUCT IMAGES ====================
    private List<String> getProductImages(Connection conn, int productId) {
        List<String> images = new ArrayList<>();
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            String sql = """
                SELECT image_url 
                FROM product_images 
                WHERE product_id = ? 
                ORDER BY is_primary DESC, image_order ASC, image_id ASC
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, productId);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                String imageUrl = rs.getString("image_url");
                if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                    images.add(imageUrl);
                }
            }
            
        } catch (SQLException e) {
            System.err.println("Error getting product images: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, null);
        }
        
        return images;
    }
    
    // ==================== CHECK IF COLUMN EXISTS IN RESULTSET ====================
    private boolean hasColumn(ResultSet rs, String columnName) throws SQLException {
        try {
            rs.findColumn(columnName);
            return true;
        } catch (SQLException e) {
            return false;
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
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        PrintWriter out = response.getWriter();
        out.print(gson.toJson(error));
        out.flush();
    }
    
    // ==================== ACTIVITY LOGGER HELPER CLASS ====================
    private static class Activity {
        public static void logProductLike(int userId, int productId) {
            try {
                Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO activity_logs (user_id, action, entity_type, entity_id, created_at) " +
                    "VALUES (?, 'product_like', 'product', ?, NOW())"
                );
                stmt.setInt(1, userId);
                stmt.setInt(2, productId);
                stmt.executeUpdate();
                stmt.close();
                conn.close();
            } catch (Exception e) {
                System.err.println("Failed to log product like activity: " + e.getMessage());
            }
        }
        
        public static void logProductView(int userId, int productId) {
            try {
                Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO activity_logs (user_id, action, entity_type, entity_id, created_at) " +
                    "VALUES (?, 'product_view', 'product', ?, NOW())"
                );
                stmt.setInt(1, userId);
                stmt.setInt(2, productId);
                stmt.executeUpdate();
                stmt.close();
                conn.close();
            } catch (Exception e) {
                System.err.println("Failed to log product view activity: " + e.getMessage());
            }
        }
    }
}