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

@WebServlet({"/ProductServlet", "/Product"})
public class ProductServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Gson gson = new Gson();
    private static final int ITEMS_PER_PAGE = 20;

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
                case "getProducts":
                    getProducts(request, response);
                    break;
                case "getProductById":
                    getProductById(request, response);
                    break;
                case "searchProducts":
                    searchProducts(request, response);
                    break;
                case "getFavorites":
                    getFavorites(request, response);
                    break;
                case "getSales":
                    getSales(request, response);
                    break;
                case "getRecommendations":
                    getRecommendations(request, response);
                    break;
                case "getCategories":
                    getCategories(request, response);
                    break;
                case "getProductsForMessaging":
                    getProductsForMessaging(request, response);
                    break;
                case "getUserProducts":
                    getUserProducts(request, response);
                    break;
                case "getAllProducts":
                    getAllProducts(request, response);
                    break;
                case "getUserActiveListingsCount":
                    getUserActiveListingsCount(request, response);
                    break;
                case "getProductsByStatus":
                    getProductsByStatus(request, response);
                    break;
                case "delete":
                    deleteProduct(request, response);
                    break;
                case "getProductStock":
                    getProductStock(request, response);
                    break;
                case "checkProductAvailability":
                    checkProductAvailability(request, response);
                    break;
                case "getAvailableProducts":
                    getAvailableProducts(request, response);
                    break;
                case "getSoldProducts":
                    getSoldProducts(request, response);
                    break;
                case "getFeatured":
                    getFeatured(request, response);
                    break;
                case "getPopular":
                    getPopularProducts(request, response);
                    break;
                case "getRelated":
                    getRelatedProducts(request, response);
                    break;
                default:
                    sendError(response, "Invalid action: " + action);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage());
        }
    }
    
    // ==================== GET AVAILABLE PRODUCTS ONLY ====================
    private void getAvailableProducts(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        int page = 1;
        int limit = ITEMS_PER_PAGE;
        try {
            String pageParam = request.getParameter("page");
            if (pageParam != null && !pageParam.isEmpty()) {
                page = Integer.parseInt(pageParam);
            }
            String limitParam = request.getParameter("limit");
            if (limitParam != null && !limitParam.isEmpty()) {
                limit = Integer.parseInt(limitParam);
            }
        } catch (NumberFormatException e) {
            page = 1;
            limit = ITEMS_PER_PAGE;
        }
        
        int offset = (page - 1) * limit;
        String sort = request.getParameter("sort");
        
        List<Map<String, Object>> products = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            StringBuilder sql = new StringBuilder("""
                SELECT p.product_id, p.product_name, p.description, p.price, 
                       p.original_price, p.discount_percentage, p.stock_quantity, 
                       p.status, p.image_url, p.is_rental, p.condition, 
                       p.rental_price_daily, p.rental_price_weekly, p.rental_price_monthly, 
                       p.is_negotiable, 
                       p.views_count, p.likes_count, p.sold_count, p.rating_avg, p.rating_count, 
                       p.user_id as seller_id, p.created_at, u.username as seller_name 
                FROM products p 
                LEFT JOIN users u ON p.user_id = u.user_id 
                WHERE p.status = 'available' 
                AND p.stock_quantity > 0
                """);
            
            // Add sorting
            if (sort != null && !sort.trim().isEmpty()) {
                switch (sort.trim()) {
                    case "newest":
                        sql.append("ORDER BY p.created_at DESC ");
                        break;
                    case "popular":
                        sql.append("ORDER BY p.views_count DESC, p.likes_count DESC ");
                        break;
                    case "price_low":
                        sql.append("ORDER BY p.price ASC ");
                        break;
                    case "price_high":
                        sql.append("ORDER BY p.price DESC ");
                        break;
                    default:
                        sql.append("ORDER BY p.created_at DESC ");
                        break;
                }
            } else {
                sql.append("ORDER BY p.created_at DESC ");
            }
            
            sql.append("LIMIT ? OFFSET ?");
            
            stmt = conn.prepareStatement(sql.toString());
            stmt.setInt(1, limit);
            stmt.setInt(2, offset);
            
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> product = mapProductFromResultSet(rs);
                product.put("is_available", true);
                product.put("is_disabled", false);
                
                // Get images for this product
                int productId = (Integer) product.get("product_id");
                List<String> images = getProductImages(productId);
                product.put("images", images);
                
                products.add(product);
            }
            
            System.out.println("✅ Available products found: " + products.size());
            
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("❌ SQL Error in getAvailableProducts: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
        
        sendJsonResponse(response, products);
    }
    
    // ==================== GET SOLD PRODUCTS ====================
    private void getSoldProducts(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        int page = 1;
        try {
            String pageParam = request.getParameter("page");
            if (pageParam != null && !pageParam.isEmpty()) {
                page = Integer.parseInt(pageParam);
            }
        } catch (NumberFormatException e) {
            page = 1;
        }
        
        int offset = (page - 1) * ITEMS_PER_PAGE;
        
        List<Map<String, Object>> products = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = """
                SELECT p.product_id, p.product_name, p.description, p.price, 
                       p.original_price, p.discount_percentage, p.stock_quantity, 
                       p.status, p.image_url, p.is_rental, p.condition, 
                       p.rental_price_daily, p.rental_price_weekly, p.rental_price_monthly, 
                       p.is_negotiable, 
                       p.views_count, p.likes_count, p.sold_count, p.rating_avg, p.rating_count, 
                       p.user_id as seller_id, p.created_at, u.username as seller_name 
                FROM products p 
                LEFT JOIN users u ON p.user_id = u.user_id 
                WHERE p.status = 'sold'
                ORDER BY p.updated_at DESC 
                LIMIT ? OFFSET ?
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, ITEMS_PER_PAGE);
            stmt.setInt(2, offset);
            
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> product = mapProductFromResultSet(rs);
                product.put("is_available", false);
                product.put("is_disabled", true);
                products.add(product);
            }
            
            System.out.println("✅ Sold products found: " + products.size());
            
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("❌ SQL Error in getSoldProducts: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
        
        sendJsonResponse(response, products);
    }
    
    // ==================== CHECK PRODUCT AVAILABILITY ====================
    private void checkProductAvailability(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        String productIdStr = request.getParameter("productId");
        if (productIdStr == null || productIdStr.isEmpty()) {
            sendError(response, "Product ID is required");
            return;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            int productId = Integer.parseInt(productIdStr);
            conn = DatabaseConnection.getConnection();
            
            String sql = """
                SELECT 
                    p.product_id,
                    p.product_name,
                    p.status,
                    p.stock_quantity,
                    p.sold_count,
                    p.is_rental,
                    p.rental_price_daily,
                    CASE 
                        WHEN p.status = 'sold' THEN false
                        WHEN p.status = 'rented' THEN false
                        WHEN p.stock_quantity <= 0 THEN false
                        ELSE true
                    END as is_available
                FROM products p
                WHERE p.product_id = ?
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, productId);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                Map<String, Object> result = new HashMap<>();
                result.put("product_id", rs.getInt("product_id"));
                result.put("product_name", rs.getString("product_name"));
                result.put("status", rs.getString("status"));
                result.put("stock_quantity", rs.getInt("stock_quantity"));
                result.put("sold_count", rs.getInt("sold_count"));
                result.put("is_rental", rs.getBoolean("is_rental"));
                result.put("is_available", rs.getBoolean("is_available"));
                
                // Determine availability message
                String status = rs.getString("status");
                String availabilityMessage = "";
                String availabilityColor = "";
                
                switch (status) {
                    case "available":
                        availabilityMessage = "販売中";
                        availabilityColor = "success";
                        break;
                    case "sold":
                        availabilityMessage = "売り切れ";
                        availabilityColor = "danger";
                        break;
                    case "rented":
                        availabilityMessage = "レンタル中";
                        availabilityColor = "warning";
                        break;
                    case "reserved":
                        availabilityMessage = "予約中";
                        availabilityColor = "info";
                        break;
                    default:
                        availabilityMessage = status;
                        availabilityColor = "secondary";
                }
                
                result.put("availability_message", availabilityMessage);
                result.put("availability_color", availabilityColor);
                
                sendJsonResponse(response, result);
            } else {
                sendError(response, "Product not found");
            }
            
        } catch (NumberFormatException e) {
            sendError(response, "Invalid product ID format");
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== GET PRODUCT STOCK ====================
    private void getProductStock(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        String productIdStr = request.getParameter("productId");
        if (productIdStr == null || productIdStr.isEmpty()) {
            sendError(response, "Product ID is required");
            return;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            int productId = Integer.parseInt(productIdStr);
            conn = DatabaseConnection.getConnection();
            
            String sql = """
                SELECT 
                    product_id,
                    product_name,
                    stock_quantity,
                    sold_count,
                    status,
                    CASE 
                        WHEN status = 'sold' THEN 0
                        WHEN status = 'rented' THEN 0
                        WHEN stock_quantity <= 0 THEN 0
                        ELSE stock_quantity
                    END as available_quantity
                FROM products 
                WHERE product_id = ?
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, productId);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                Map<String, Object> result = new HashMap<>();
                result.put("product_id", rs.getInt("product_id"));
                result.put("product_name", rs.getString("product_name"));
                result.put("stock_quantity", rs.getInt("stock_quantity"));
                result.put("sold_count", rs.getInt("sold_count"));
                result.put("status", rs.getString("status"));
                result.put("available_quantity", rs.getInt("available_quantity"));
                result.put("is_in_stock", rs.getInt("available_quantity") > 0);
                
                sendJsonResponse(response, result);
            } else {
                sendError(response, "Product not found");
            }
            
        } catch (NumberFormatException e) {
            sendError(response, "Invalid product ID format");
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== GET PRODUCTS BY STATUS ====================
    private void getProductsByStatus(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        String status = request.getParameter("status");
        if (status == null || status.isEmpty()) {
            sendError(response, "Status parameter is required");
            return;
        }
        
        int page = 1;
        try {
            String pageParam = request.getParameter("page");
            if (pageParam != null && !pageParam.isEmpty()) {
                page = Integer.parseInt(pageParam);
            }
        } catch (NumberFormatException e) {
            page = 1;
        }
        
        int offset = (page - 1) * ITEMS_PER_PAGE;
        
        List<Map<String, Object>> products = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = """
                SELECT p.product_id, p.product_name, p.description, p.price, 
                       p.original_price, p.discount_percentage, p.stock_quantity, 
                       p.status, p.image_url, p.is_rental, p.condition, 
                       p.rental_price_daily, p.rental_price_weekly, p.rental_price_monthly, 
                       p.is_negotiable, 
                       p.views_count, p.likes_count, p.sold_count, p.rating_avg, p.rating_count, 
                       p.user_id as seller_id, p.created_at, u.username as seller_name 
                FROM products p 
                LEFT JOIN users u ON p.user_id = u.user_id 
                WHERE p.status = ?
                ORDER BY p.created_at DESC 
                LIMIT ? OFFSET ?
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, status);
            stmt.setInt(2, ITEMS_PER_PAGE);
            stmt.setInt(3, offset);
            
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> product = mapProductFromResultSet(rs);
                
                // Set availability based on status
                boolean isAvailable = !"sold".equals(status) && !"rented".equals(status);
                product.put("is_available", isAvailable);
                product.put("is_disabled", !isAvailable);
                
                products.add(product);
            }
            
            System.out.println("✅ Products with status '" + status + "' found: " + products.size());
            
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("❌ SQL Error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
        
        sendJsonResponse(response, products);
    }
    
    // ==================== GET USER ACTIVE LISTINGS COUNT ====================
    private void getUserActiveListingsCount(HttpServletRequest request, HttpServletResponse response) 
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
                SELECT COUNT(*) as active_count 
                FROM products 
                WHERE user_id = ? AND status = 'available' AND stock_quantity > 0
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            
            int count = 0;
            if (rs.next()) {
                count = rs.getInt("active_count");
            }
            
            sendJsonResponse(response, Map.of("count", count));
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendJsonResponse(response, Map.of("count", 0));
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== LOG PRODUCT VIEW ====================
    private void logProductView(int userId, int productId) {
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
            System.err.println("Failed to log product view: " + e.getMessage());
        }
    }
    
    // ==================== GET ALL PRODUCTS ====================
    private void getAllProducts(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        System.out.println("=== Getting All Products from Database ===");
        
        List<Map<String, Object>> products = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = """
                SELECT p.product_id, p.product_name, p.description, p.price, 
                       p.original_price, p.discount_percentage, p.stock_quantity, 
                       p.status, p.image_url, p.is_rental, p.condition, 
                       p.rental_price_daily, p.rental_price_weekly, p.rental_price_monthly, 
                       p.is_negotiable, 
                       p.views_count, p.likes_count, p.sold_count, p.rating_avg, p.rating_count, 
                       p.user_id as seller_id, p.created_at, u.username as seller_name 
                FROM products p 
                LEFT JOIN users u ON p.user_id = u.user_id 
                ORDER BY 
                    CASE 
                        WHEN p.status = 'available' AND p.stock_quantity > 0 THEN 1
                        WHEN p.status = 'reserved' THEN 2
                        WHEN p.status = 'rented' THEN 3
                        WHEN p.status = 'sold' THEN 4
                        ELSE 5
                    END,
                    p.created_at DESC 
                LIMIT 50
                """;
            
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> product = mapProductFromResultSet(rs);
                
                // Set real availability
                boolean isAvailable = checkRealTimeAvailability(product);
                product.put("is_available", isAvailable);
                product.put("is_disabled", !isAvailable);
                
                // Get images for this product
                int productId = (Integer) product.get("product_id");
                List<String> images = getProductImages(productId);
                product.put("images", images);
                
                products.add(product);
            }
            
            System.out.println("✅ All Products found: " + products.size());
            
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("❌ SQL Error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
        
        sendJsonResponse(response, products);
    }
    
    // ==================== CHECK REAL-TIME AVAILABILITY ====================
    private boolean checkRealTimeAvailability(Map<String, Object> product) {
        String status = (String) product.get("status");
        Integer stockQuantity = (Integer) product.get("stock_quantity");
        
        // Check if product is sold or rented
        if ("sold".equals(status) || "rented".equals(status)) {
            return false;
        }
        
        // Check stock quantity
        if (stockQuantity != null && stockQuantity <= 0) {
            return false;
        }
        
        return true;
    }
    
    // ==================== GET ACTIVE RENTAL INFO ====================
    private Map<String, Object> getActiveRentalInfo(int productId, Connection conn) {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            // Check if rentals table exists
            stmt = conn.prepareStatement("SHOW TABLES LIKE 'rentals'");
            rs = stmt.executeQuery();
            boolean hasTable = rs.next();
            rs.close();
            stmt.close();
            
            if (!hasTable) {
                return null;
            }
            
            // Get active rental (pending, confirmed, or active status)
            String sql = "SELECT r.end_date, r.status, r.rental_number " +
                        "FROM rentals r " +
                        "WHERE r.product_id = ? " +
                        "AND r.status IN ('pending', 'confirmed', 'active') " +
                        "ORDER BY r.end_date DESC " +
                        "LIMIT 1";
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, productId);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                Map<String, Object> rentalInfo = new HashMap<>();
                rentalInfo.put("end_date", rs.getDate("end_date"));
                rentalInfo.put("status", rs.getString("status"));
                rentalInfo.put("rental_number", rs.getString("rental_number"));
                return rentalInfo;
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            DatabaseConnection.closeResources(rs, stmt, null);
        }
        
        return null;
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
            
            if (categories.isEmpty()) {
                System.out.println("⚠️ No categories in database, returning defaults");
                categories = createDefaultCategories();
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("❌ SQL Error: " + e.getMessage());
            categories = createDefaultCategories();
            
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
        
        sendJsonResponse(response, categories);
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
    
    // ==================== GET PRODUCTS WITH FILTERING ====================
    private void getProducts(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        int page = 1;
        try {
            String pageParam = request.getParameter("page");
            if (pageParam != null && !pageParam.isEmpty()) {
                page = Integer.parseInt(pageParam);
            }
        } catch (NumberFormatException e) {
            page = 1;
        }
        
        int offset = (page - 1) * ITEMS_PER_PAGE;
        
        String categories = request.getParameter("categories");
        String price = request.getParameter("price");
        String conditions = request.getParameter("conditions");
        String sort = request.getParameter("sort");
        String status = request.getParameter("status");
        String includeSold = request.getParameter("includeSold");
        
        System.out.println("=== Product Filter Request ===");
        System.out.println("Categories: " + categories);
        System.out.println("Price: " + price);
        System.out.println("Conditions: " + conditions);
        System.out.println("Sort: " + sort);
        System.out.println("Status: " + status);
        System.out.println("Include Sold: " + includeSold);
        System.out.println("Is Rental: " + request.getParameter("is_rental"));
        System.out.println("Page: " + page);
        
        List<Map<String, Object>> products = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT DISTINCT ");
            sql.append("p.product_id, p.product_name, p.description, p.price, ");
            sql.append("p.original_price, p.discount_percentage, p.stock_quantity, ");
            sql.append("p.status, p.image_url, p.is_rental, p.condition, ");
            sql.append("p.rental_price_daily, p.rental_price_weekly, p.rental_price_monthly, ");
            sql.append("p.is_negotiable, ");
            sql.append("p.views_count, p.likes_count, p.sold_count, ");
            sql.append("p.rating_avg, p.rating_count, ");
            sql.append("p.user_id as seller_id, p.created_at, ");
            sql.append("u.username as seller_name ");
            sql.append("FROM products p ");
            sql.append("LEFT JOIN users u ON p.user_id = u.user_id ");
            
            boolean hasCategories = (categories != null && !categories.trim().isEmpty());
            if (hasCategories) {
                sql.append("INNER JOIN product_categories pc ON p.product_id = pc.product_id ");
                sql.append("LEFT JOIN categories c ON pc.category_id = c.category_id ");
            }
            
            // Build WHERE clause based on filters
            List<String> whereConditions = new ArrayList<>();
            List<Object> params = new ArrayList<>();
            
            // Rental filter
            String isRental = request.getParameter("is_rental");
            if ("true".equals(isRental)) {
                // Only show rental products with stock > 0
                whereConditions.add("p.is_rental = 1");
                whereConditions.add("p.stock_quantity > 0");
                whereConditions.add("p.status = 'available'");
            } else {
                // Status filtering (only if not filtering by rental)
            if (status != null && !status.trim().isEmpty()) {
                whereConditions.add("p.status = ?");
                params.add(status.trim());
            } else if ("true".equals(includeSold)) {
                // Include all products including sold
                    whereConditions.add("p.status IN ('available', 'reserved', 'sold', 'rented', 'inactive')");
            } else {
                // Default: show only available products with stock
                whereConditions.add("p.status = 'available'");
                whereConditions.add("p.stock_quantity > 0");
                }
            }
            
            // Category filter - support both category_id and category slug
            if (hasCategories) {
                String[] catArray = categories.split(",");
                // Check if it's numeric (category_id) or string (slug)
                boolean isNumeric = true;
                for (String cat : catArray) {
                    try {
                        Integer.parseInt(cat.trim());
                    } catch (NumberFormatException e) {
                        isNumeric = false;
                        break;
                    }
                }
                
                if (isNumeric) {
                    // Use category_id directly
                    String categoryPlaceholders = String.join(",", 
                        java.util.Collections.nCopies(catArray.length, "?"));
                    whereConditions.add("pc.category_id IN (" + categoryPlaceholders + ")");
                    for (String cat : catArray) {
                        params.add(Integer.parseInt(cat.trim()));
                    }
                } else {
                    // Use category slug
                String categoryPlaceholders = String.join(",", 
                    java.util.Collections.nCopies(catArray.length, "?"));
                whereConditions.add("c.slug IN (" + categoryPlaceholders + ")");
                for (String cat : catArray) {
                    params.add(cat.trim());
                    }
                }
            }
            
            // Price filter - support both predefined ranges and custom min-max
            if (price != null && !price.trim().isEmpty()) {
                // Check if it's a range format (e.g., "1000-5000" or "1000-+")
                if (price.contains("-")) {
                    String[] priceParts = price.split("-");
                    if (priceParts.length == 2) {
                        try {
                            double minPrice = Double.parseDouble(priceParts[0].trim());
                            String maxPart = priceParts[1].trim();
                            if ("+".equals(maxPart)) {
                                whereConditions.add("p.price >= ?");
                                params.add(minPrice);
                            } else {
                                double maxPrice = Double.parseDouble(maxPart);
                                whereConditions.add("p.price BETWEEN ? AND ?");
                                params.add(minPrice);
                                params.add(maxPrice);
                            }
                        } catch (NumberFormatException e) {
                            // Invalid format, skip price filter
                        }
                    }
                } else {
                    // Predefined ranges
                switch (price.trim()) {
                    case "under50k":
                        whereConditions.add("p.price < 50000");
                        break;
                    case "50k-100k":
                        whereConditions.add("p.price BETWEEN 50000 AND 100000");
                        break;
                    case "100k-500k":
                        whereConditions.add("p.price BETWEEN 100000 AND 500000");
                        break;
                    case "over500k":
                        whereConditions.add("p.price >= 500000");
                        break;
                    }
                }
            }
            
            // Condition filter
            if (conditions != null && !conditions.trim().isEmpty()) {
                String[] condArray = conditions.split(",");
                String conditionPlaceholders = String.join(",", 
                    java.util.Collections.nCopies(condArray.length, "?"));
                whereConditions.add("p.condition IN (" + conditionPlaceholders + ")");
                for (String cond : condArray) {
                    params.add(cond.trim());
                }
            }
            
            // Add WHERE clause if conditions exist
            if (!whereConditions.isEmpty()) {
                sql.append("WHERE ").append(String.join(" AND ", whereConditions)).append(" ");
            }
            
            // Sorting
            if (sort != null && !sort.trim().isEmpty()) {
                switch (sort.trim()) {
                    case "price_low":
                        sql.append("ORDER BY p.price ASC ");
                        break;
                    case "price_high":
                        sql.append("ORDER BY p.price DESC ");
                        break;
                    case "popular":
                        sql.append("ORDER BY p.views_count DESC, p.likes_count DESC ");
                        break;
                    case "recently_sold":
                        sql.append("ORDER BY CASE WHEN p.status = 'sold' THEN 0 ELSE 1 END, p.updated_at DESC ");
                        break;
                    case "newest":
                    default:
                        sql.append("ORDER BY p.created_at DESC ");
                        break;
                }
            } else {
                sql.append("ORDER BY p.created_at DESC ");
            }
            
            sql.append("LIMIT ? OFFSET ?");
            
            stmt = conn.prepareStatement(sql.toString());
            
            int paramIndex = 1;
            for (Object param : params) {
                if (param instanceof String) {
                    stmt.setString(paramIndex++, (String) param);
                } else if (param instanceof Integer) {
                    stmt.setInt(paramIndex++, (Integer) param);
                } else if (param instanceof Double) {
                    stmt.setDouble(paramIndex++, (Double) param);
                }
            }
            
            stmt.setInt(paramIndex++, ITEMS_PER_PAGE);
            stmt.setInt(paramIndex, offset);
            
            System.out.println("=== Executing Query ===");
            System.out.println("Limit: " + ITEMS_PER_PAGE + ", Offset: " + offset);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> product = mapProductFromResultSet(rs);
                
                // Add real-time availability check
                boolean isAvailable = checkRealTimeAvailability(product);
                product.put("is_available", isAvailable);
                product.put("is_disabled", !isAvailable);
                
                // If this is a rental product, check active rentals
                if ("true".equals(isRental) && (Boolean) product.get("is_rental")) {
                    Map<String, Object> rentalInfo = getActiveRentalInfo((Integer) product.get("product_id"), conn);
                    if (rentalInfo != null) {
                        product.put("is_currently_rented", true);
                        product.put("rental_end_date", rentalInfo.get("end_date"));
                        product.put("rental_status", rentalInfo.get("status"));
                    } else {
                        product.put("is_currently_rented", false);
                    }
                }
                
                // Get images for this product
                int productId = (Integer) product.get("product_id");
                List<String> images = getProductImages(productId);
                product.put("images", images);
                
                products.add(product);
            }
            
            System.out.println("✅ Products found: " + products.size());
            
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("❌ SQL Error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
        
        // Wrap products in a response object for consistency
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("products", products);
        result.put("count", products.size());
        result.put("total", products.size());
        sendJsonResponse(response, result);
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
    
    // ==================== SEARCH PRODUCTS ====================
    private void searchProducts(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        String query = request.getParameter("query");
        
        if (query == null || query.trim().isEmpty()) {
            // Show only available products by default
            getAvailableProducts(request, response);
            return;
        }
        
        List<Map<String, Object>> products = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = """
                SELECT p.product_id, p.product_name, p.description, p.price, 
                       p.original_price, p.discount_percentage, p.stock_quantity, 
                       p.status, p.image_url, p.is_rental, p.condition, 
                       p.rental_price_daily, p.rental_price_weekly, p.rental_price_monthly, 
                       p.is_negotiable, 
                       p.views_count, p.likes_count, p.sold_count, p.rating_avg, p.rating_count, 
                       p.user_id as seller_id, p.created_at, u.username as seller_name 
                FROM products p 
                LEFT JOIN users u ON p.user_id = u.user_id 
                WHERE p.status = 'available' 
                AND p.stock_quantity > 0
                AND (LOWER(p.product_name) LIKE LOWER(?) 
                     OR LOWER(p.description) LIKE LOWER(?)) 
                ORDER BY p.created_at DESC 
                LIMIT 50
                """;
            
            stmt = conn.prepareStatement(sql);
            String searchPattern = "%" + query + "%";
            stmt.setString(1, searchPattern);
            stmt.setString(2, searchPattern);
            
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> product = mapProductFromResultSet(rs);
                product.put("is_available", true);
                product.put("is_disabled", false);
                
                // Get images for this product
                int productId = (Integer) product.get("product_id");
                List<String> images = getProductImages(productId);
                product.put("images", images);
                
                products.add(product);
            }
            
            System.out.println("✅ Search results for '" + query + "': " + products.size());
            
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("❌ Search error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
        
        sendJsonResponse(response, products);
    }
    
    // ==================== GET FAVORITES ====================
    private void getFavorites(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        
        if (session == null || session.getAttribute("user_id") == null) {
            getPopularProducts(request, response);
            return;
        }
        
        Integer userId = (Integer) session.getAttribute("user_id");
        List<Map<String, Object>> products = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = """
                SELECT p.product_id, p.product_name, p.description, p.price, 
                       p.original_price, p.discount_percentage, p.stock_quantity, 
                       p.status, p.image_url, p.is_rental, p.condition, 
                       p.rental_price_daily, p.rental_price_weekly, p.rental_price_monthly, 
                       p.is_negotiable, 
                       p.views_count, p.likes_count, p.sold_count, p.rating_avg, p.rating_count, 
                       p.user_id as seller_id, p.created_at, u.username as seller_name 
                FROM products p 
                LEFT JOIN users u ON p.user_id = u.user_id 
                INNER JOIN user_favorites f ON p.product_id = f.product_id 
                WHERE p.status = 'available' 
                AND p.stock_quantity > 0
                AND f.user_id = ? 
                ORDER BY f.added_at DESC 
                LIMIT 50
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> product = mapProductFromResultSet(rs);
                product.put("is_available", true);
                product.put("is_disabled", false);
                
                // Get images for this product
                int productId = (Integer) product.get("product_id");
                List<String> images = getProductImages(productId);
                product.put("images", images);
                
                products.add(product);
            }
            
            if (products.isEmpty()) {
                System.out.println("⚠️ No favorites found, loading popular products instead");
                getPopularProducts(request, response);
                return;
            }
            
            System.out.println("✅ Favorites found: " + products.size());
            
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("❌ Error getting favorites: " + e.getMessage());
            getPopularProducts(request, response);
            return;
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
        
        sendJsonResponse(response, products);
    }
    
    // ==================== GET SALES ====================
    private void getSales(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        List<Map<String, Object>> products = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = """
                SELECT p.product_id, p.product_name, p.description, p.price, 
                       p.original_price, p.discount_percentage, p.stock_quantity, 
                       p.status, p.image_url, p.is_rental, p.condition, 
                       p.rental_price_daily, p.rental_price_weekly, p.rental_price_monthly, 
                       p.is_negotiable, 
                       p.views_count, p.likes_count, p.sold_count, p.rating_avg, p.rating_count, 
                       p.user_id as seller_id, p.created_at, u.username as seller_name 
                FROM products p 
                LEFT JOIN users u ON p.user_id = u.user_id 
                WHERE p.status = 'available' 
                AND p.stock_quantity > 0
                AND p.discount_percentage > 0 
                ORDER BY p.discount_percentage DESC, p.price ASC 
                LIMIT 30
                """;
            
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> product = mapProductFromResultSet(rs);
                product.put("is_available", true);
                product.put("is_disabled", false);
                
                // Get images for this product
                int productId = (Integer) product.get("product_id");
                List<String> images = getProductImages(productId);
                product.put("images", images);
                
                products.add(product);
            }
            
            System.out.println("✅ Sales products found: " + products.size());
            
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("❌ Error getting sales: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
        
        sendJsonResponse(response, products);
    }
    
    // ==================== GET RECOMMENDATIONS ====================
    private void getRecommendations(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        getPopularProducts(request, response);
    }
    
    // ==================== GET POPULAR PRODUCTS ====================
    private void getPopularProducts(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        List<Map<String, Object>> products = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            int limit = 8;
            try {
                String limitParam = request.getParameter("limit");
                if (limitParam != null && !limitParam.isEmpty()) {
                    limit = Integer.parseInt(limitParam);
                }
            } catch (NumberFormatException e) {
                limit = 8;
            }
            
            String sql = """
                SELECT p.product_id, p.product_name, p.description, p.price, 
                       p.original_price, p.discount_percentage, p.stock_quantity, 
                       p.status, p.image_url, p.is_rental, p.condition, 
                       p.rental_price_daily, p.rental_price_weekly, p.rental_price_monthly, 
                       p.is_negotiable, 
                       p.views_count, p.likes_count, p.sold_count, p.rating_avg, p.rating_count, 
                       p.user_id as seller_id, p.created_at, u.username as seller_name 
                FROM products p 
                LEFT JOIN users u ON p.user_id = u.user_id 
                WHERE p.status = 'available' 
                AND p.stock_quantity > 0
                ORDER BY p.views_count DESC, p.likes_count DESC, p.created_at DESC 
                LIMIT ?
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, limit);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> product = mapProductFromResultSet(rs);
                product.put("is_available", true);
                product.put("is_disabled", false);
                
                // Get images for this product
                int productId = (Integer) product.get("product_id");
                List<String> images = getProductImages(productId);
                product.put("images", images);
                
                products.add(product);
            }
            
            System.out.println("✅ Popular products found: " + products.size());
            
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("❌ Error getting popular products: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
        
        sendJsonResponse(response, products);
    }
    
    // ==================== GET FEATURED PRODUCTS ====================
    private void getFeatured(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        int limit = 8;
        try {
            String limitParam = request.getParameter("limit");
            if (limitParam != null && !limitParam.isEmpty()) {
                limit = Integer.parseInt(limitParam);
            }
        } catch (NumberFormatException e) {
            limit = 8;
        }
        
        List<Map<String, Object>> products = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            // Featured products: high rating, high views, or discount
            String sql = """
                SELECT p.product_id, p.product_name, p.description, p.price, 
                       p.original_price, p.discount_percentage, p.stock_quantity, 
                       p.status, p.image_url, p.is_rental, p.condition, 
                       p.rental_price_daily, p.rental_price_weekly, p.rental_price_monthly, 
                       p.is_negotiable, 
                       p.views_count, p.likes_count, p.sold_count, p.rating_avg, p.rating_count, 
                       p.user_id as seller_id, p.created_at, u.username as seller_name 
                FROM products p 
                LEFT JOIN users u ON p.user_id = u.user_id 
                WHERE p.status = 'available' 
                AND p.stock_quantity > 0
                AND (p.rating_avg >= 4.0 OR p.discount_percentage > 0 OR p.views_count > 100)
                ORDER BY p.rating_avg DESC, p.views_count DESC, p.created_at DESC 
                LIMIT ?
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, limit);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> product = mapProductFromResultSet(rs);
                product.put("is_available", true);
                product.put("is_disabled", false);
                
                // Get images for this product
                int productId = (Integer) product.get("product_id");
                List<String> images = getProductImages(productId);
                product.put("images", images);
                
                products.add(product);
            }
            
            System.out.println("✅ Featured products found: " + products.size());
            
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("❌ Error getting featured products: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
        
        sendJsonResponse(response, products);
    }
    
    // ==================== GET PRODUCT BY ID ====================
    private void getProductById(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        String productIdStr = request.getParameter("id");
        
        if (productIdStr == null || productIdStr.isEmpty()) {
            sendError(response, "Product ID is required");
            return;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            int productId = Integer.parseInt(productIdStr);
            conn = DatabaseConnection.getConnection();
            
            String sql = """
                SELECT p.product_id, p.product_name, p.description, p.price, 
                       p.original_price, p.discount_percentage, p.stock_quantity, 
                       p.status, p.image_url, p.is_rental, p.condition, 
                       p.rental_price_daily, p.rental_price_weekly, p.rental_price_monthly, 
                       p.is_negotiable, 
                       p.views_count, p.likes_count, p.sold_count, p.rating_avg, p.rating_count, 
                       p.user_id as seller_id, p.created_at, p.updated_at,
                       u.username as seller_name,
                       u.full_name as seller_full_name, u.avatar_url as seller_avatar,
                       u.is_verified as seller_verified,
                       u.created_at as seller_created_at
                FROM products p 
                LEFT JOIN users u ON p.user_id = u.user_id 
                WHERE p.product_id = ?
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, productId);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                Map<String, Object> product = mapProductFromResultSet(rs);
                
                // Check real availability
                boolean isAvailable = checkRealTimeAvailability(product);
                product.put("is_available", isAvailable);
                product.put("is_disabled", !isAvailable);
                
                // Add extra seller info
                product.put("seller_full_name", rs.getString("seller_full_name"));
                product.put("seller_avatar", rs.getString("seller_avatar"));
                product.put("seller_verified", rs.getBoolean("seller_verified"));
                product.put("seller_created_at", rs.getTimestamp("seller_created_at"));
                product.put("updated_at", rs.getTimestamp("updated_at"));
                
                // Get categories for this product
                List<Map<String, Object>> categories = getProductCategories(productId);
                product.put("categories", categories);
                
                // Get images for this product
                List<String> images = getProductImages(productId);
                product.put("images", images);
                
                // Log product view activity
                HttpSession session = request.getSession(false);
                if (session != null && session.getAttribute("user_id") != null) {
                    Integer userId = (Integer) session.getAttribute("user_id");
                    logProductView(userId, productId);
                }
                
                System.out.println("✅ Product found: " + product.get("product_name") + 
                                 ", Status: " + product.get("status") + 
                                 ", Available: " + product.get("is_available"));
                
                // Wrap in response object for consistency
                Map<String, Object> responseData = new HashMap<>();
                responseData.put("success", true);
                responseData.put("product", product);
                sendJsonResponse(response, responseData);
            } else {
                System.out.println("❌ Product not found: " + productId);
                sendError(response, "Product not found");
            }
            
        } catch (NumberFormatException e) {
            System.err.println("❌ Invalid product ID format: " + productIdStr);
            sendError(response, "Invalid product ID format");
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("❌ Database error: " + e.getMessage());
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== GET PRODUCT CATEGORIES ====================
    private List<Map<String, Object>> getProductCategories(int productId) {
        List<Map<String, Object>> categories = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = """
                SELECT c.category_id, c.category_name, c.slug, c.description, 
                       c.icon_url, c.image_url, c.display_order 
                FROM categories c 
                INNER JOIN product_categories pc ON c.category_id = pc.category_id 
                WHERE pc.product_id = ? 
                ORDER BY c.display_order ASC
                """;
            
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
                category.put("image_url", rs.getString("image_url"));
                category.put("display_order", rs.getInt("display_order"));
                
                categories.add(category);
            }
            
        } catch (SQLException e) {
            System.err.println("Error getting product categories: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
        
        return categories;
    }
    
    // ==================== GET PRODUCT IMAGES ====================
    private List<String> getProductImages(int productId) {
        List<String> images = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = """
                SELECT image_url 
                FROM product_images 
                WHERE product_id = ? 
                ORDER BY image_order ASC
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
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
        
        // If no additional images, return empty list
        return images;
    }
    
    // ==================== GET PRODUCTS FOR MESSAGING ====================
    private void getProductsForMessaging(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        List<Map<String, Object>> products = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = """
                SELECT 
                    p.product_id,
                    p.product_name,
                    p.price,
                    p.image_url,
                    p.status,
                    p.stock_quantity,
                    p.is_rental,
                    p.rental_price_daily,
                    u.username as seller_username,
                    u.user_id as seller_id
                FROM products p
                INNER JOIN users u ON p.user_id = u.user_id
                WHERE p.status IN ('available', 'reserved', 'sold', 'rented')
                AND u.deleted_at IS NULL
                ORDER BY p.created_at DESC
                LIMIT 100
                """;
            
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> product = new HashMap<>();
                product.put("product_id", rs.getInt("product_id"));
                product.put("product_name", rs.getString("product_name"));
                product.put("price", rs.getDouble("price"));
                product.put("image_url", rs.getString("image_url"));
                product.put("status", rs.getString("status"));
                product.put("stock_quantity", rs.getInt("stock_quantity"));
                product.put("is_rental", rs.getBoolean("is_rental"));
                product.put("rental_price_daily", rs.getObject("rental_price_daily"));
                product.put("seller_username", rs.getString("seller_username"));
                product.put("seller_id", rs.getInt("seller_id"));
                
                // Check availability
                boolean isAvailable = !"sold".equals(rs.getString("status")) && 
                                     !"rented".equals(rs.getString("status")) &&
                                     rs.getInt("stock_quantity") > 0;
                product.put("is_available", isAvailable);
                
                products.add(product);
            }
            
            System.out.println("✅ Products for messaging: " + products.size());
            
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("❌ Error getting products for messaging: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
        
        sendJsonResponse(response, products);
    }
    
    // ==================== GET USER PRODUCTS ====================
    private void getUserProducts(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        System.out.println("=== Getting User Products from Database ===");
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        Integer userId = (Integer) session.getAttribute("user_id");
        
        List<Map<String, Object>> products = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = """
                SELECT p.product_id, p.product_name, p.description, p.price, 
                       p.original_price, p.discount_percentage, p.stock_quantity, 
                       p.status, p.image_url, p.is_rental, p.condition, 
                       p.rental_price_daily, p.rental_price_weekly, p.rental_price_monthly, 
                       p.is_negotiable, 
                       p.views_count, p.likes_count, p.sold_count, p.rating_avg, p.rating_count, 
                       p.user_id as seller_id, p.created_at, u.username as seller_name 
                FROM products p 
                LEFT JOIN users u ON p.user_id = u.user_id 
                WHERE p.user_id = ?
                ORDER BY 
                    CASE 
                        WHEN p.status = 'available' THEN 1
                        WHEN p.status = 'reserved' THEN 2
                        WHEN p.status = 'rented' THEN 3
                        WHEN p.status = 'sold' THEN 4
                        ELSE 5
                    END,
                    p.created_at DESC
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> product = mapProductFromResultSet(rs);
                
                // Check real availability
                boolean isAvailable = checkRealTimeAvailability(product);
                product.put("is_available", isAvailable);
                product.put("is_disabled", !isAvailable);
                
                products.add(product);
            }
            
            System.out.println("✅ User products found: " + products.size());
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("products", products);
            result.put("count", products.size());
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("❌ Error getting user products: " + e.getMessage());
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
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        error.put("timestamp", new java.util.Date());
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
    protected void doPut(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        FilterEncodingUTF8.configureUTF8ForJSON(request, response);
        
        try {
            String action = request.getParameter("action");
            if (action == null || action.isEmpty()) {
                sendError(response, "Action parameter is required");
                return;
            }
            
            switch (action) {
                case "updateProductStatus":
                    updateProductStatus(request, response);
                    break;
                case "markAsSold":
                    markAsSold(request, response);
                    break;
                case "restockProduct":
                    restockProduct(request, response);
                    break;
                case "delete":
                    deleteProduct(request, response);
                    break;
                default:
                    sendError(response, "Invalid action for PUT: " + action);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage());
        }
    }
    
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        FilterEncodingUTF8.configureUTF8ForJSON(request, response);
        
        try {
            String action = request.getParameter("action");
            if (action == null || action.isEmpty()) {
                action = "delete"; // Default action for DELETE method
            }
            
            if ("delete".equals(action)) {
                deleteProduct(request, response);
            } else {
                sendError(response, "Invalid action for DELETE: " + action);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage());
        }
    }
    
    // ==================== UPDATE PRODUCT STATUS ====================
    private void updateProductStatus(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        String productIdStr = request.getParameter("product_id");
        String status = request.getParameter("status");
        String reason = request.getParameter("reason");
        
        if (productIdStr == null || productIdStr.isEmpty() || 
            status == null || status.isEmpty()) {
            sendError(response, "Product ID and status are required");
            return;
        }
        
        Integer userId = (Integer) session.getAttribute("user_id");
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            int productId = Integer.parseInt(productIdStr);
            conn = DatabaseConnection.getConnection();
            
            // Check if user owns the product or is admin
            String checkSql = "SELECT user_id, status, stock_quantity FROM products WHERE product_id = ?";
            PreparedStatement checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setInt(1, productId);
            ResultSet rs = checkStmt.executeQuery();
            
            if (!rs.next()) {
                sendError(response, "Product not found");
                return;
            }
            
            int productOwnerId = rs.getInt("user_id");
            String currentStatus = rs.getString("status");
            int currentStock = rs.getInt("stock_quantity");
            
            String userRole = (String) session.getAttribute("role");
            
            // Allow update if user is owner or admin
            if (productOwnerId != userId && !"admin".equals(userRole) && !"moderator".equals(userRole)) {
                sendError(response, "You are not authorized to update this product");
                return;
            }
            
            // Validate status transition
            if ("sold".equals(status) && currentStock <= 0) {
                sendError(response, "Cannot mark as sold: product is out of stock");
                return;
            }
            
            // Update product status
            String updateSql = "UPDATE products SET status = ?, updated_at = NOW() WHERE product_id = ?";
            stmt = conn.prepareStatement(updateSql);
            stmt.setString(1, status);
            stmt.setInt(2, productId);
            
            int rowsAffected = stmt.executeUpdate();
            
            if (rowsAffected > 0) {
                // If marking as sold, update stock and sold count
                if ("sold".equals(status) && !"sold".equals(currentStatus)) {
                    updateSoldCount(conn, productId);
                }
                
                // Log status change activity
                logStatusChangeActivity(userId, productId, status, reason);
                
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "Product status updated successfully");
                result.put("product_id", productId);
                result.put("new_status", status);
                result.put("updated_at", new java.util.Date());
                
                sendJsonResponse(response, result);
            } else {
                sendError(response, "Failed to update product status");
            }
            
        } catch (NumberFormatException e) {
            sendError(response, "Invalid product ID format");
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(null, stmt, conn);
        }
    }
    
    // ==================== MARK AS SOLD ====================
    private void markAsSold(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        String productIdStr = request.getParameter("product_id");
        String buyerIdStr = request.getParameter("buyer_id");
        String orderIdStr = request.getParameter("order_id");
        
        if (productIdStr == null || productIdStr.isEmpty()) {
            sendError(response, "Product ID is required");
            return;
        }
        
        Integer userId = (Integer) session.getAttribute("user_id");
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            int productId = Integer.parseInt(productIdStr);
            Integer buyerId = buyerIdStr != null ? Integer.parseInt(buyerIdStr) : null;
            Integer orderId = orderIdStr != null ? Integer.parseInt(orderIdStr) : null;
            
            conn = DatabaseConnection.getConnection();
            
            // Check if user owns the product or is admin
            String checkSql = "SELECT user_id, status, stock_quantity FROM products WHERE product_id = ?";
            PreparedStatement checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setInt(1, productId);
            ResultSet rs = checkStmt.executeQuery();
            
            if (!rs.next()) {
                sendError(response, "Product not found");
                return;
            }
            
            int productOwnerId = rs.getInt("user_id");
            String currentStatus = rs.getString("status");
            int stockQuantity = rs.getInt("stock_quantity");
            
            String userRole = (String) session.getAttribute("role");
            
            // Allow update if user is owner or admin
            if (productOwnerId != userId && !"admin".equals(userRole) && !"moderator".equals(userRole)) {
                sendError(response, "You are not authorized to mark this product as sold");
                return;
            }
            
            // Check if product is already sold
            if ("sold".equals(currentStatus)) {
                sendError(response, "Product is already sold");
                return;
            }
            
            // Check if there's stock available
            if (stockQuantity <= 0) {
                sendError(response, "Product is out of stock");
                return;
            }
            
            // Update product as sold
            String updateSql = """
                UPDATE products 
                SET status = 'sold',
                    stock_quantity = GREATEST(0, stock_quantity - 1),
                    sold_count = sold_count + 1,
                    updated_at = NOW()
                WHERE product_id = ?
                """;
            
            stmt = conn.prepareStatement(updateSql);
            stmt.setInt(1, productId);
            int rowsAffected = stmt.executeUpdate();
            
            if (rowsAffected > 0) {
                // Log the sale
                logSaleActivity(userId, productId, buyerId, orderId);
                
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "Product marked as sold successfully");
                result.put("product_id", productId);
                result.put("new_status", "sold");
                result.put("updated_stock", Math.max(0, stockQuantity - 1));
                result.put("updated_sold_count", 1);
                
                sendJsonResponse(response, result);
            } else {
                sendError(response, "Failed to mark product as sold");
            }
            
        } catch (NumberFormatException e) {
            sendError(response, "Invalid parameter format");
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(null, stmt, conn);
        }
    }
    
    // ==================== RESTOCK PRODUCT ====================
    private void restockProduct(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        String productIdStr = request.getParameter("product_id");
        String quantityStr = request.getParameter("quantity");
        
        if (productIdStr == null || productIdStr.isEmpty()) {
            sendError(response, "Product ID is required");
            return;
        }
        
        Integer userId = (Integer) session.getAttribute("user_id");
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            int productId = Integer.parseInt(productIdStr);
            int quantity = quantityStr != null ? Integer.parseInt(quantityStr) : 1;
            
            if (quantity <= 0) {
                sendError(response, "Quantity must be greater than 0");
                return;
            }
            
            conn = DatabaseConnection.getConnection();
            
            // Check if user owns the product
            String checkSql = "SELECT user_id, status FROM products WHERE product_id = ?";
            PreparedStatement checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setInt(1, productId);
            ResultSet rs = checkStmt.executeQuery();
            
            if (!rs.next()) {
                sendError(response, "Product not found");
                return;
            }
            
            int productOwnerId = rs.getInt("user_id");
            String currentStatus = rs.getString("status");
            
            if (productOwnerId != userId) {
                sendError(response, "You are not authorized to restock this product");
                return;
            }
            
            // Determine new status
            String newStatus = "available";
            if ("sold".equals(currentStatus) && quantity > 0) {
                newStatus = "available";
            }
            
            // Restock product
            String updateSql = """
                UPDATE products 
                SET status = ?,
                    stock_quantity = stock_quantity + ?,
                    updated_at = NOW()
                WHERE product_id = ?
                """;
            
            stmt = conn.prepareStatement(updateSql);
            stmt.setString(1, newStatus);
            stmt.setInt(2, quantity);
            stmt.setInt(3, productId);
            int rowsAffected = stmt.executeUpdate();
            
            if (rowsAffected > 0) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "Product restocked successfully");
                result.put("product_id", productId);
                result.put("new_status", newStatus);
                result.put("quantity_added", quantity);
                
                sendJsonResponse(response, result);
            } else {
                sendError(response, "Failed to restock product");
            }
            
        } catch (NumberFormatException e) {
            sendError(response, "Invalid parameter format");
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(null, stmt, conn);
        }
    }
    
    // ==================== DELETE PRODUCT ====================
    private void deleteProduct(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        String productIdStr = request.getParameter("id");
        if (productIdStr == null) {
            productIdStr = request.getParameter("product_id");
        }
        
        if (productIdStr == null || productIdStr.isEmpty()) {
            sendError(response, "Product ID is required");
            return;
        }
        
        Integer userId = (Integer) session.getAttribute("user_id");
        String userRole = (String) session.getAttribute("role");
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            int productId = Integer.parseInt(productIdStr);
            conn = DatabaseConnection.getConnection();
            
            // Check if user owns the product or is admin
            String checkSql = "SELECT user_id FROM products WHERE product_id = ?";
            stmt = conn.prepareStatement(checkSql);
            stmt.setInt(1, productId);
            rs = stmt.executeQuery();
            
            if (!rs.next()) {
                sendError(response, "Product not found");
                return;
            }
            
            int productOwnerId = rs.getInt("user_id");
            
            // Allow delete if user is owner or admin
            if (productOwnerId != userId && !"admin".equals(userRole) && !"moderator".equals(userRole)) {
                sendError(response, "You are not authorized to delete this product");
                return;
            }
            
            rs.close();
            stmt.close();
            
            // Soft delete by setting status to 'deleted'
            String deleteSql = "UPDATE products SET status = 'deleted', updated_at = NOW() WHERE product_id = ?";
            stmt = conn.prepareStatement(deleteSql);
            stmt.setInt(1, productId);
            
            int rowsAffected = stmt.executeUpdate();
            
            Map<String, Object> result = new HashMap<>();
            if (rowsAffected > 0) {
                result.put("success", true);
                result.put("message", "Product deleted successfully");
            } else {
                result.put("success", false);
                result.put("error", "Failed to delete product");
            }
            
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            PrintWriter out = response.getWriter();
            out.print(gson.toJson(result));
            out.flush();
            
        } catch (NumberFormatException e) {
            sendError(response, "Invalid product ID format");
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== UPDATE SOLD COUNT ====================
    private void updateSoldCount(Connection conn, int productId) throws SQLException {
        PreparedStatement stmt = null;
        try {
            String sql = """
                UPDATE products 
                SET stock_quantity = GREATEST(0, stock_quantity - 1),
                    sold_count = sold_count + 1
                WHERE product_id = ?
                """;
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, productId);
            stmt.executeUpdate();
        } finally {
            if (stmt != null) stmt.close();
        }
    }
    
    // ==================== LOG STATUS CHANGE ACTIVITY ====================
    private void logStatusChangeActivity(int userId, int productId, String newStatus, String reason) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO activity_logs (user_id, action, entity_type, entity_id, details, created_at) " +
                "VALUES (?, ?, 'product', ?, ?, NOW())"
            );
            
            String action = "product_status_change";
            String details = String.format("{\"new_status\": \"%s\", \"reason\": \"%s\"}", 
                newStatus, reason != null ? reason : "");
            
            stmt.setInt(1, userId);
            stmt.setString(2, action);
            stmt.setInt(3, productId);
            stmt.setString(4, details);
            stmt.executeUpdate();
            
            stmt.close();
            conn.close();
            
        } catch (Exception e) {
            System.err.println("Failed to log status change activity: " + e.getMessage());
        }
    }
    
    // ==================== GET RELATED PRODUCTS ====================
    private void getRelatedProducts(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        String productIdStr = request.getParameter("product_id");
        int limit = 4;
        
        try {
            String limitParam = request.getParameter("limit");
            if (limitParam != null && !limitParam.isEmpty()) {
                limit = Integer.parseInt(limitParam);
            }
        } catch (NumberFormatException e) {
            limit = 4;
        }
        
        if (productIdStr == null || productIdStr.isEmpty()) {
            sendError(response, "Product ID is required");
            return;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            int productId = Integer.parseInt(productIdStr);
            conn = DatabaseConnection.getConnection();
            
            // Get related products from same categories
            String sql = """
                SELECT DISTINCT p.product_id, p.product_name, p.description, p.price, 
                       p.original_price, p.discount_percentage, p.stock_quantity, 
                       p.status, p.image_url, p.is_rental, p.condition, 
                       p.rental_price_daily, p.rental_price_weekly, p.rental_price_monthly, 
                       p.is_negotiable, 
                       p.views_count, p.likes_count, p.sold_count, p.rating_avg, p.rating_count, 
                       p.user_id as seller_id, p.created_at, u.username as seller_name 
                FROM products p 
                INNER JOIN product_categories pc1 ON p.product_id = pc1.product_id 
                LEFT JOIN users u ON p.user_id = u.user_id 
                WHERE pc1.category_id IN ( 
                    SELECT category_id FROM product_categories WHERE product_id = ? 
                ) 
                AND p.product_id != ? 
                AND p.status = 'available' 
                AND p.stock_quantity > 0
                ORDER BY p.views_count DESC, p.rating_avg DESC, p.created_at DESC 
                LIMIT ?
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, productId);
            stmt.setInt(2, productId);
            stmt.setInt(3, limit);
            rs = stmt.executeQuery();
            
            List<Map<String, Object>> products = new ArrayList<>();
            
            while (rs.next()) {
                Map<String, Object> product = mapProductFromResultSet(rs);
                
                // Get images for this product
                int prodId = (Integer) product.get("product_id");
                List<String> images = getProductImages(prodId);
                product.put("images", images);
                
                products.add(product);
            }
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("success", true);
            responseData.put("products", products);
            
            sendJsonResponse(response, responseData);
            
        } catch (NumberFormatException e) {
            sendError(response, "Invalid product ID format");
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== LOG SALE ACTIVITY ====================
    private void logSaleActivity(int sellerId, int productId, Integer buyerId, Integer orderId) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO activity_logs (user_id, action, entity_type, entity_id, details, created_at) " +
                "VALUES (?, ?, 'product', ?, ?, NOW())"
            );
            
            String action = "product_sold";
            String details = String.format("{\"buyer_id\": %s, \"order_id\": %s}", 
                buyerId != null ? buyerId.toString() : "null",
                orderId != null ? orderId.toString() : "null");
            
            stmt.setInt(1, sellerId);
            stmt.setString(2, action);
            stmt.setInt(3, productId);
            stmt.setString(4, details);
            stmt.executeUpdate();
            
            stmt.close();
            conn.close();
            
        } catch (Exception e) {
            System.err.println("Failed to log sale activity: " + e.getMessage());
        }
    }
}
