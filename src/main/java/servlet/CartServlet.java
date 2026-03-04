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

@WebServlet({"/CartServlet", "/Cart"})
public class CartServlet extends HttpServlet {
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
                case "getCart":
                    getCart(request, response);
                    break;
                case "getCartCount":
                    getCartCount(request, response);
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
        
        String action = request.getParameter("action");
        
        try {
            if (action == null || action.isEmpty()) {
                sendError(response, "Action parameter is required");
                return;
            }
            
            switch (action) {
                case "add":
                    addToCart(request, response);
                    break;
                case "update":
                    updateCartItem(request, response);
                    break;
                case "remove":
                    removeFromCart(request, response);
                    break;
                case "clear":
                    clearCart(request, response);
                    break;
                default:
                    sendError(response, "Invalid action: " + action);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage());
        }
    }
    
    /** Get user ID from session, trying user_id and userId, handling Integer/Long/String. */
    private Integer getSessionUserId(HttpSession session) {
        if (session == null) return null;
        Object obj = session.getAttribute("user_id");
        if (obj == null) obj = session.getAttribute("userId");
        if (obj == null) return null;
        if (obj instanceof Integer) return (Integer) obj;
        if (obj instanceof Long) return Integer.valueOf(((Long) obj).intValue());
        try {
            return Integer.valueOf(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    // ==================== LOG CART ACTIVITY ====================
    private void logCartActivity(int userId, int productId, String action, int quantity) {
    	ActivityServlet.logUserActivity(userId, "cart_" + action, "product", productId, 
            "{\"action\": \"" + action + "\", \"product_id\": " + productId + ", \"quantity\": " + quantity + "}");
    }
    
    // ==================== GET CART ====================
    private void getCart(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        Integer userId = getSessionUserId(session);
        if (session == null || userId == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        List<Map<String, Object>> cartItems = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        Map<String, Object> responseData = new HashMap<>();
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = """
                SELECT 
                    c.cart_id, c.quantity, c.price_snapshot, c.added_at,
                    p.product_id, p.product_name, p.description, p.price, 
                    p.original_price, p.discount_percentage, p.stock_quantity, 
                    p.status, p.image_url, p.is_rental, p.condition,
                    p.rental_price_daily, p.rental_price_weekly, p.rental_price_monthly,
                    u.username as seller_name, u.user_id as seller_id
                FROM carts c
                INNER JOIN products p ON c.product_id = p.product_id
                LEFT JOIN users u ON p.user_id = u.user_id
                WHERE c.user_id = ? AND (p.status = 'available' OR (p.status = 'reserved' AND EXISTS (
                    SELECT 1 FROM offers o WHERE o.product_id = p.product_id AND o.buyer_id = c.user_id AND o.status = 'accepted')))
                ORDER BY c.added_at DESC
                """;
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                cartItems.add(mapCartItemFromResultSet(rs));
            }
            
            // Calculate totals
            double subtotal = 0.0;
            for (Map<String, Object> item : cartItems) {
                double price = ((Number) item.get("price_snapshot")).doubleValue();
                int quantity = ((Number) item.get("quantity")).intValue();
                subtotal += price * quantity;
            }
            
            // Shipping cost (default 500 yen)
            double shipping = cartItems.isEmpty() ? 0.0 : 500.0;
            double total = subtotal + shipping;
            
            // Build response object
            responseData.put("items", cartItems);
            responseData.put("subtotal", subtotal);
            responseData.put("shipping", shipping);
            responseData.put("total", total);
            responseData.put("itemCount", cartItems.size());
            
            System.out.println("✅ Cart items found: " + cartItems.size() + " for user: " + userId);
            System.out.println("✅ Subtotal: " + subtotal + ", Shipping: " + shipping + ", Total: " + total);
            
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("❌ SQL Error in getCart: " + e.getMessage());
            sendError(response, "データベースエラー: " + e.getMessage());
            return;
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
        
        sendJsonResponse(response, responseData);
    }
    
    // ==================== GET CART COUNT ====================
    private void getCartCount(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        Integer userId = getSessionUserId(session);
        if (session == null || userId == null) {
            sendJsonResponse(response, Map.of("count", 0, "cartCount", 0));
            return;
        }
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = """
                SELECT COUNT(*) as item_count, COALESCE(SUM(quantity), 0) as total_quantity 
                FROM carts c
                INNER JOIN products p ON c.product_id = p.product_id
                WHERE c.user_id = ? AND (p.status = 'available' OR (p.status = 'reserved' AND EXISTS (
                    SELECT 1 FROM offers o WHERE o.product_id = p.product_id AND o.buyer_id = c.user_id AND o.status = 'accepted')))
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            
            int itemCount = 0;
            int totalQuantity = 0;
            if (rs.next()) {
                itemCount = rs.getInt("item_count");
                totalQuantity = rs.getInt("total_quantity");
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("count", itemCount);
            result.put("cartCount", totalQuantity);
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendJsonResponse(response, Map.of("count", 0, "cartCount", 0));
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== ADD TO CART ====================
    private void addToCart(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        Integer userId = getSessionUserId(session);
        if (session == null || userId == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        String productIdStr = request.getParameter("productId");
        if (productIdStr == null || productIdStr.isEmpty()) {
            productIdStr = request.getParameter("product_id");
        }
        String quantityStr = request.getParameter("quantity");
        String offerIdStr = request.getParameter("offer_id");
        
        if (productIdStr == null || productIdStr.isEmpty()) {
            sendError(response, "商品IDが必要です");
            return;
        }
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            int productId = Integer.parseInt(productIdStr);
            int quantity = quantityStr != null ? Integer.parseInt(quantityStr) : 1;
            
            // Validate quantity
            if (quantity < 1) {
                sendError(response, "数量は1以上である必要があります");
                return;
            }
            
            conn = DatabaseConnection.getConnection();
            
            double price;
            int stockQuantity;
            String status;
            Integer offerId = null;
            
            if (offerIdStr != null && !offerIdStr.trim().isEmpty()) {
                // Add via accepted offer - use offer price, allow reserved product
                int oid = Integer.parseInt(offerIdStr);
                String offerSql = "SELECT o.offer_price, o.product_id, p.stock_quantity, p.status " +
                    "FROM offers o JOIN products p ON o.product_id = p.product_id " +
                    "WHERE o.offer_id = ? AND o.buyer_id = ? AND o.status = 'accepted'";
                stmt = conn.prepareStatement(offerSql);
                stmt.setInt(1, oid);
                stmt.setInt(2, userId);
                rs = stmt.executeQuery();
                if (!rs.next()) {
                    sendError(response, "オファーが見つからないか、承認されていません");
                    return;
                }
                price = rs.getDouble("offer_price");
                stockQuantity = rs.getInt("stock_quantity");
                status = rs.getString("status");
                if (!"reserved".equals(status) && !"available".equals(status)) {
                    sendError(response, "この商品は現在購入できません");
                    return;
                }
                offerId = oid;
                rs.close();
                stmt.close();
            } else {
                // Normal add - product must be available
                String productSql = "SELECT price, stock_quantity, status FROM products WHERE product_id = ?";
                stmt = conn.prepareStatement(productSql);
                stmt.setInt(1, productId);
                rs = stmt.executeQuery();
                if (!rs.next()) {
                    sendError(response, "商品が見つかりません");
                    return;
                }
                price = rs.getDouble("price");
                stockQuantity = rs.getInt("stock_quantity");
                status = rs.getString("status");
                if (!"available".equals(status)) {
                    sendError(response, "この商品は現在購入できません");
                    return;
                }
            }
            
            if (stockQuantity < quantity) {
                sendError(response, "在庫が不足しています");
                return;
            }
            
            if (rs != null) { rs.close(); rs = null; }
            if (stmt != null) { stmt.close(); stmt = null; }
            
            // When adding via offer, remove any existing cart item (replace with offer price)
            if (offerId != null) {
                String deleteSql = "DELETE FROM carts WHERE user_id = ? AND product_id = ?";
                stmt = conn.prepareStatement(deleteSql);
                stmt.setInt(1, userId);
                stmt.setInt(2, productId);
                stmt.executeUpdate();
                stmt.close();
                stmt = null;
            }
            
            // Check if item already in cart
            String checkSql = "SELECT cart_id, quantity FROM carts WHERE user_id = ? AND product_id = ?";
            stmt = conn.prepareStatement(checkSql);
            stmt.setInt(1, userId);
            stmt.setInt(2, productId);
            rs = stmt.executeQuery();
            
            Map<String, Object> result = new HashMap<>();
            
            if (rs.next()) {
                // Update existing cart item
                int cartId = rs.getInt("cart_id");
                int existingQuantity = rs.getInt("quantity");
                int newQuantity = existingQuantity + quantity;
                
                if (newQuantity > stockQuantity) {
                    sendError(response, "在庫が不足しています");
                    return;
                }
                
                String updateSql = "UPDATE carts SET quantity = ?, updated_at = NOW() WHERE cart_id = ?";
                stmt = conn.prepareStatement(updateSql);
                stmt.setInt(1, newQuantity);
                stmt.setInt(2, cartId);
                int rowsAffected = stmt.executeUpdate();
                
                if (rowsAffected > 0) {
                    // Log cart activity
                    logCartActivity(userId, productId, "update", newQuantity);
                    
                    result.put("success", true);
                    result.put("message", "カートを更新しました");
                    result.put("quantity", newQuantity);
                } else {
                    result.put("success", false);
                    result.put("message", "カートの更新に失敗しました");
                }
            } else {
                // Add new item to cart (price_snapshot stores offer price when adding via offer)
                String insertSql = "INSERT INTO carts (user_id, product_id, quantity, price_snapshot, added_at) VALUES (?, ?, ?, ?, NOW())";
                stmt = conn.prepareStatement(insertSql);
                stmt.setInt(1, userId);
                stmt.setInt(2, productId);
                stmt.setInt(3, quantity);
                stmt.setDouble(4, price);
                int rowsAffected = stmt.executeUpdate();
                
                if (rowsAffected > 0) {
                    // Log cart activity
                    logCartActivity(userId, productId, "add", quantity);
                    
                    result.put("success", true);
                    result.put("message", "カートに追加しました");
                    result.put("quantity", quantity);
                } else {
                    result.put("success", false);
                    result.put("message", "カートへの追加に失敗しました");
                }
            }
            
            sendJsonResponse(response, result);
            
        } catch (NumberFormatException e) {
            sendError(response, "無効な数値形式です");
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "データベースエラー: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== UPDATE CART ITEM ====================
    private void updateCartItem(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        Integer userId = getSessionUserId(session);
        if (session == null || userId == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        String cartIdStr = request.getParameter("cartId");
        String productIdStr = request.getParameter("productId");
        String quantityStr = request.getParameter("quantity");
        
        if (quantityStr == null || quantityStr.isEmpty()) {
            sendError(response, "数量が必要です");
            return;
        }
        
        // Support both cartId and productId
        boolean useProductId = (cartIdStr == null || cartIdStr.isEmpty()) && (productIdStr != null && !productIdStr.isEmpty());
        
        if (!useProductId && (cartIdStr == null || cartIdStr.isEmpty())) {
            sendError(response, "カートIDまたは商品IDが必要です");
            return;
        }
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            int quantity = Integer.parseInt(quantityStr);
            
            if (quantity < 1) {
                sendError(response, "数量は1以上である必要があります");
                return;
            }
            
            conn = DatabaseConnection.getConnection();
            
            int cartId;
            int productId;
            int stockQuantity;
            String status;
            
            if (useProductId) {
                // Find cart by productId
                int productIdInt = Integer.parseInt(productIdStr);
                String findSql = "SELECT c.cart_id, p.stock_quantity, p.status FROM carts c INNER JOIN products p ON c.product_id = p.product_id WHERE c.product_id = ? AND c.user_id = ?";
                stmt = conn.prepareStatement(findSql);
                stmt.setInt(1, productIdInt);
                stmt.setInt(2, userId);
                ResultSet rs = stmt.executeQuery();
                
                if (!rs.next()) {
                    sendError(response, "カートアイテムが見つかりません");
                    return;
                }
                
                cartId = rs.getInt("cart_id");
                productId = productIdInt;
                stockQuantity = rs.getInt("stock_quantity");
                status = rs.getString("status");
                rs.close();
                stmt.close();
            } else {
                cartId = Integer.parseInt(cartIdStr);
                // Check if cart item belongs to user and get product info
                String checkSql = """
                    SELECT p.product_id, p.stock_quantity, p.status 
                    FROM carts c 
                    INNER JOIN products p ON c.product_id = p.product_id 
                    WHERE c.cart_id = ? AND c.user_id = ?
                    """;
                stmt = conn.prepareStatement(checkSql);
                stmt.setInt(1, cartId);
                stmt.setInt(2, userId);
                ResultSet rs = stmt.executeQuery();
                
                if (!rs.next()) {
                    sendError(response, "カートアイテムが見つかりません");
                    return;
                }
                
                productId = rs.getInt("product_id");
                stockQuantity = rs.getInt("stock_quantity");
                status = rs.getString("status");
                rs.close();
                stmt.close();
            }
            
            if (!"available".equals(status) && !"reserved".equals(status)) {
                sendError(response, "この商品は現在購入できません");
                return;
            }
            
            if (quantity > stockQuantity) {
                sendError(response, "在庫が不足しています");
                return;
            }
            
            // Update cart item
            String updateSql = "UPDATE carts SET quantity = ?, updated_at = NOW() WHERE cart_id = ? AND user_id = ?";
            stmt = conn.prepareStatement(updateSql);
            stmt.setInt(1, quantity);
            stmt.setInt(2, cartId);
            stmt.setInt(3, userId);
            int rowsAffected = stmt.executeUpdate();
            
            Map<String, Object> result = new HashMap<>();
            
            if (rowsAffected > 0) {
                // Log cart activity
                logCartActivity(userId, productId, "update", quantity);
                
                result.put("success", true);
                result.put("message", "カートを更新しました");
            } else {
                result.put("success", false);
                result.put("message", "カートの更新に失敗しました");
            }
            
            sendJsonResponse(response, result);
            
        } catch (NumberFormatException e) {
            sendError(response, "無効な数値形式です");
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "データベースエラー: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(null, stmt, conn);
        }
    }
    
    // ==================== REMOVE FROM CART ====================
    private void removeFromCart(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        Integer userId = getSessionUserId(session);
        if (session == null || userId == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        String cartIdStr = request.getParameter("cartId");
        String productIdStr = request.getParameter("productId");
        
        // Support both cartId and productId
        boolean useProductId = (cartIdStr == null || cartIdStr.isEmpty()) && (productIdStr != null && !productIdStr.isEmpty());
        
        if (!useProductId && (cartIdStr == null || cartIdStr.isEmpty())) {
            sendError(response, "カートIDまたは商品IDが必要です");
            return;
        }
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            int cartId;
            int productId = 0;
            int quantity = 0;
            
            conn = DatabaseConnection.getConnection();
            
            if (useProductId) {
                // Find cart by productId
                int productIdInt = Integer.parseInt(productIdStr);
                String findSql = "SELECT cart_id, quantity FROM carts WHERE product_id = ? AND user_id = ?";
                stmt = conn.prepareStatement(findSql);
                stmt.setInt(1, productIdInt);
                stmt.setInt(2, userId);
                ResultSet rs = stmt.executeQuery();
                
                if (!rs.next()) {
                    sendError(response, "カートアイテムが見つかりません");
                    return;
                }
                
                cartId = rs.getInt("cart_id");
                productId = productIdInt;
                quantity = rs.getInt("quantity");
                rs.close();
                stmt.close();
            } else {
                cartId = Integer.parseInt(cartIdStr);
                // Get product info before deletion for logging
                String selectSql = "SELECT product_id, quantity FROM carts WHERE cart_id = ? AND user_id = ?";
                stmt = conn.prepareStatement(selectSql);
                stmt.setInt(1, cartId);
                stmt.setInt(2, userId);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    productId = rs.getInt("product_id");
                    quantity = rs.getInt("quantity");
                }
                rs.close();
                stmt.close();
            }
            
            String deleteSql = "DELETE FROM carts WHERE cart_id = ? AND user_id = ?";
            stmt = conn.prepareStatement(deleteSql);
            stmt.setInt(1, cartId);
            stmt.setInt(2, userId);
            int rowsAffected = stmt.executeUpdate();
            
            Map<String, Object> result = new HashMap<>();
            
            if (rowsAffected > 0) {
                // Log cart activity
                logCartActivity(userId, productId, "remove", quantity);
                
                result.put("success", true);
                result.put("message", "カートから削除しました");
            } else {
                result.put("success", false);
                result.put("message", "削除に失敗しました");
            }
            
            sendJsonResponse(response, result);
            
        } catch (NumberFormatException e) {
            sendError(response, "Invalid cart ID format");
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(null, stmt, conn);
        }
    }
    
    // ==================== CLEAR CART ====================
    private void clearCart(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        Integer userId = getSessionUserId(session);
        if (session == null || userId == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            // Get all cart items for logging
            String selectSql = "SELECT product_id, quantity FROM carts WHERE user_id = ?";
            stmt = conn.prepareStatement(selectSql);
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            
            List<Map<String, Object>> cartItems = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> item = new HashMap<>();
                item.put("product_id", rs.getInt("product_id"));
                item.put("quantity", rs.getInt("quantity"));
                cartItems.add(item);
            }
            rs.close();
            stmt.close();
            
            // Delete all cart items
            String deleteSql = "DELETE FROM carts WHERE user_id = ?";
            stmt = conn.prepareStatement(deleteSql);
            stmt.setInt(1, userId);
            int rowsAffected = stmt.executeUpdate();
            
            Map<String, Object> result = new HashMap<>();
            
            if (rowsAffected > 0) {
                // Log cart activity for each item
                for (Map<String, Object> item : cartItems) {
                    int productId = (Integer) item.get("product_id");
                    int quantity = (Integer) item.get("quantity");
                    logCartActivity(userId, productId, "remove", quantity);
                }
                
                result.put("success", true);
                result.put("message", "カートを空にしました");
                result.put("count", rowsAffected);
            } else {
                result.put("success", true);
                result.put("message", "カートは既に空です");
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
    
    // ==================== MAP CART ITEM FROM RESULTSET ====================
    private Map<String, Object> mapCartItemFromResultSet(ResultSet rs) throws SQLException {
        Map<String, Object> item = new HashMap<>();
        
        item.put("cart_id", rs.getInt("cart_id"));
        item.put("quantity", rs.getInt("quantity"));
        item.put("price_snapshot", rs.getDouble("price_snapshot"));
        item.put("added_at", rs.getTimestamp("added_at"));
        
        item.put("product_id", rs.getInt("product_id"));
        item.put("product_name", rs.getString("product_name"));
        item.put("description", rs.getString("description"));
        item.put("price", rs.getDouble("price"));
        
        double originalPrice = rs.getDouble("original_price");
        item.put("original_price", rs.wasNull() ? null : originalPrice);
        
        item.put("discount_percentage", rs.getInt("discount_percentage"));
        item.put("stock_quantity", rs.getInt("stock_quantity"));
        item.put("status", rs.getString("status"));
        item.put("image_url", rs.getString("image_url"));
        item.put("is_rental", rs.getBoolean("is_rental"));
        item.put("condition", rs.getString("condition"));
        
        double dailyPrice = rs.getDouble("rental_price_daily");
        item.put("rental_price_daily", rs.wasNull() ? null : dailyPrice);
        
        double weeklyPrice = rs.getDouble("rental_price_weekly");
        item.put("rental_price_weekly", rs.wasNull() ? null : weeklyPrice);
        
        double monthlyPrice = rs.getDouble("rental_price_monthly");
        item.put("rental_price_monthly", rs.wasNull() ? null : monthlyPrice);
        
        item.put("seller_name", rs.getString("seller_name"));
        item.put("seller_id", rs.getInt("seller_id"));
        
        // Calculate subtotal
        double subtotal = rs.getDouble("price_snapshot") * rs.getInt("quantity");
        item.put("subtotal", subtotal);
        
        return item;
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
}