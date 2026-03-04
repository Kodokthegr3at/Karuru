package servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
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
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import util.DatabaseConnection;
import util.FilterEncodingUTF8;


@WebServlet("/AdminServlet")
public class AdminServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    
    // UTF-8対応のGsonインスタンス
    private Gson gson = new GsonBuilder()
        .disableHtmlEscaping()
        .create();
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        // UTF-8設定を適用
        FilterEncodingUTF8.configureUTF8ForJSON(request, response);
        
        if (!isAdmin(request)) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN, "アクセスが拒否されました");
            return;
        }
        
        String action = request.getParameter("action");
        System.out.println("AdminServlet - GET Action: " + action);
        
        try {
            if (action == null || action.trim().isEmpty()) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "アクションパラメータが必要です");
                return;
            }
            
            switch (action) {
                case "getStats":
                    getDashboardStats(request, response);
                    break;
                case "getRevenueData":
                    getRevenueData(request, response);
                    break;
                case "getRecentActivity":
                    getRecentActivity(request, response);
                    break;
                case "getActivityLogs":
                    getActivityLogs(request, response);
                    break;
                case "getUsers":
                    getUsers(request, response);
                    break;
                case "getProducts":
                    getProducts(request, response);
                    break;
                case "getCategories":
                    getCategories(request, response);
                    break;
                case "getOrders":
                    getOrders(request, response);
                    break;
                case "getUserAddresses":
                    getUserAddresses(request, response);
                    break;
                case "getUserWallets":
                    getUserWallets(request, response);
                    break;
                case "getProductReviews":
                    getProductReviews(request, response);
                    break;
                case "getRentals":
                    getRentals(request, response);
                    break;
                case "getVouchers":
                    getVouchers(request, response);
                    break;
                case "getBanners":
                    getBanners(request, response);
                    break;
                case "getMessages":
                    getMessages(request, response);
                    break;
                case "getNotifications":
                    getNotifications(request, response);
                    break;
                case "getRecord":
                    getRecord(request, response);
                    break;
                case "getOffers":
                    getOffers(request, response);
                    break;
                default:
                    sendError(response, HttpServletResponse.SC_BAD_REQUEST, "無効なアクション: " + action);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "内部サーバーエラー: " + e.getMessage());
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        // UTF-8設定を適用
        FilterEncodingUTF8.configureUTF8ForJSON(request, response);
        
        if (!isAdmin(request)) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN, "アクセスが拒否されました");
            return;
        }
        
        String action = request.getParameter("action");
        System.out.println("AdminServlet - POST Action: " + action);
        
        try {
            if (action == null || action.trim().isEmpty()) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "アクションパラメータが必要です");
                return;
            }
            
            switch (action) {
                case "create":
                    createRecord(request, response);
                    break;
                case "update":
                    updateRecord(request, response);
                    break;
                default:
                    sendError(response, HttpServletResponse.SC_BAD_REQUEST, "無効なアクション: " + action);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "内部サーバーエラー: " + e.getMessage());
        }
    }
    
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        // UTF-8設定を適用
        FilterEncodingUTF8.configureUTF8ForJSON(request, response);
        
        if (!isAdmin(request)) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN, "アクセスが拒否されました");
            return;
        }
        
        String action = request.getParameter("action");
        System.out.println("AdminServlet - DELETE Action: " + action);
        
        try {
            if (action == null || action.trim().isEmpty()) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "アクションパラメータが必要です");
                return;
            }
            
            if ("delete".equals(action)) {
                deleteRecord(request, response);
            } else {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "無効なアクション: " + action);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "内部サーバーエラー: " + e.getMessage());
        }
    }
    
    // ==================== DASHBOARD STATS ====================
    
    private void getDashboardStats(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            // UTF-8設定を接続に適用
            stmt = conn.prepareStatement("SET NAMES utf8mb4");
            stmt.execute();
            stmt.close();
            
            Map<String, Object> stats = new HashMap<>();
            
            // Total Users - Use simple query without deleted_at check for compatibility
            stmt = conn.prepareStatement("SELECT COUNT(*) as count FROM users");
            rs = stmt.executeQuery();
            if (rs.next()) {
                stats.put("totalUsers", rs.getInt("count"));
            } else {
                stats.put("totalUsers", 0);
            }
            DatabaseConnection.closeResources(rs, stmt, null);
            
            // Total Products - Use simple query without deleted_at check for compatibility
            stmt = conn.prepareStatement("SELECT COUNT(*) as count FROM products");
            rs = stmt.executeQuery();
            if (rs.next()) {
                stats.put("totalProducts", rs.getInt("count"));
            } else {
                stats.put("totalProducts", 0);
            }
            DatabaseConnection.closeResources(rs, stmt, null);
            
            // Total Orders
            stmt = conn.prepareStatement("SELECT COUNT(*) as count FROM orders");
            rs = stmt.executeQuery();
            if (rs.next()) {
                stats.put("totalOrders", rs.getInt("count"));
            } else {
                stats.put("totalOrders", 0);
            }
            DatabaseConnection.closeResources(rs, stmt, null);
            
            // Total Offers (if table exists)
            stmt = conn.prepareStatement("SHOW TABLES LIKE 'offers'");
            rs = stmt.executeQuery();
            if (rs.next()) {
                DatabaseConnection.closeResources(rs, stmt, null);
                stmt = conn.prepareStatement("SELECT COUNT(*) as count FROM offers");
                rs = stmt.executeQuery();
                stats.put("totalOffers", rs.next() ? rs.getInt("count") : 0);
            } else {
                stats.put("totalOffers", 0);
            }
            DatabaseConnection.closeResources(rs, stmt, null);
            
            // Total Revenue
            stmt = conn.prepareStatement("SELECT COALESCE(SUM(total_amount), 0) as revenue FROM orders WHERE payment_status = 'paid'");
            rs = stmt.executeQuery();
            if (rs.next()) {
                Object revenueObj = rs.getObject("revenue");
                if (revenueObj != null) {
                    if (revenueObj instanceof java.math.BigDecimal) {
                        stats.put("totalRevenue", ((java.math.BigDecimal) revenueObj).doubleValue());
                    } else if (revenueObj instanceof Number) {
                        stats.put("totalRevenue", ((Number) revenueObj).doubleValue());
                    } else {
                        stats.put("totalRevenue", 0.0);
                    }
                } else {
                    stats.put("totalRevenue", 0.0);
                }
            } else {
                stats.put("totalRevenue", 0.0);
            }
            DatabaseConnection.closeResources(rs, stmt, null);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("stats", stats);
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("Error in getDashboardStats: " + e.getMessage());
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "データベースエラー: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Unexpected error in getDashboardStats: " + e.getMessage());
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "予期しないエラー: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    private void getRevenueData(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            stmt = conn.prepareStatement("SET NAMES utf8mb4");
            stmt.execute();
            stmt.close();
            
            String sql = "SELECT DATE(created_at) as date, COALESCE(SUM(total_amount), 0) as revenue " +
                        "FROM orders WHERE payment_status = 'paid' " +
                        "AND created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY) " +
                        "GROUP BY DATE(created_at) ORDER BY date";
            
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            
            List<String> labels = new ArrayList<>();
            List<Double> values = new ArrayList<>();
            
            while (rs.next()) {
                labels.add(rs.getString("date"));
                values.add(rs.getDouble("revenue"));
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("labels", labels);
            result.put("values", values);
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "データベースエラー: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    private void getRecentActivity(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            stmt = conn.prepareStatement("SET NAMES utf8mb4");
            stmt.execute();
            stmt.close();
            
            // Check if activity_logs table exists
            String checkSql = "SHOW TABLES LIKE 'activity_logs'";
            stmt = conn.prepareStatement(checkSql);
            rs = stmt.executeQuery();
            boolean hasTable = rs.next();
            DatabaseConnection.closeResources(rs, stmt, null);
            
            List<Map<String, Object>> activities = new ArrayList<>();
            
            if (hasTable) {
                String sql = "SELECT action, entity_type, created_at FROM activity_logs " +
                            "ORDER BY created_at DESC LIMIT 10";
                
                stmt = conn.prepareStatement(sql);
                rs = stmt.executeQuery();
                
                while (rs.next()) {
                    Map<String, Object> activity = new HashMap<>();
                    activity.put("title", rs.getString("action") + " - " + rs.getString("entity_type"));
                    activity.put("time", getTimeAgo(rs.getTimestamp("created_at")));
                    activity.put("icon", "bi-check-circle");
                    activity.put("iconClass", "bg-success");
                    activities.add(activity);
                }
            } else {
                // Sample activities
                Map<String, Object> activity1 = new HashMap<>();
                activity1.put("title", "新しい注文");
                activity1.put("time", "5分前");
                activity1.put("icon", "bi-check-circle");
                activity1.put("iconClass", "bg-success");
                activities.add(activity1);
                
                Map<String, Object> activity2 = new HashMap<>();
                activity2.put("title", "新規ユーザー登録");
                activity2.put("time", "10分前");
                activity2.put("icon", "bi-person-plus");
                activity2.put("iconClass", "bg-info");
                activities.add(activity2);
                
                Map<String, Object> activity3 = new HashMap<>();
                activity3.put("title", "商品レビュー投稿");
                activity3.put("time", "15分前");
                activity3.put("icon", "bi-chat-text");
                activity3.put("iconClass", "bg-warning");
                activities.add(activity3);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("activities", activities);
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "データベースエラー: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    private void getActivityLogs(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            stmt = conn.prepareStatement("SET NAMES utf8mb4");
            stmt.execute();
            stmt.close();
            
            // Get limit parameter, default to 50
            String limitParam = request.getParameter("limit");
            int limit = 50;
            if (limitParam != null && !limitParam.trim().isEmpty()) {
                try {
                    limit = Integer.parseInt(limitParam);
                    if (limit < 1 || limit > 1000) {
                        limit = 50; // Sanitize limit
                    }
                } catch (NumberFormatException e) {
                    limit = 50;
                }
            }
            
            List<Map<String, Object>> logs = new ArrayList<>();
            
            // Check if activity_logs table exists
            String checkSql = "SHOW TABLES LIKE 'activity_logs'";
            stmt = conn.prepareStatement(checkSql);
            rs = stmt.executeQuery();
            boolean hasTable = rs.next();
            DatabaseConnection.closeResources(rs, stmt, null);
            
            if (hasTable) {
                String sql = "SELECT " +
                            "al.log_id, " +
                            "al.user_id, " +
                            "al.action, " +
                            "al.entity_type, " +
                            "al.entity_id, " +
                            "al.ip_address, " +
                            "al.details, " +
                            "al.created_at, " +
                            "u.username, " +
                            "u.full_name " +
                            "FROM activity_logs al " +
                            "LEFT JOIN users u ON al.user_id = u.user_id " +
                            "ORDER BY al.created_at DESC " +
                            "LIMIT ?";
                
                stmt = conn.prepareStatement(sql);
                stmt.setInt(1, limit);
                rs = stmt.executeQuery();
                
                while (rs.next()) {
                    Map<String, Object> log = new HashMap<>();
                    log.put("log_id", rs.getLong("log_id"));
                    log.put("user_id", rs.getObject("user_id"));
                    log.put("action", getStringSafe(rs, "action"));
                    log.put("entity_type", getStringSafe(rs, "entity_type"));
                    log.put("entity_id", rs.getObject("entity_id"));
                    log.put("ip_address", getStringSafe(rs, "ip_address"));
                    log.put("details", getStringSafe(rs, "details"));
                    log.put("created_at", rs.getTimestamp("created_at"));
                    log.put("username", getStringSafe(rs, "username"));
                    log.put("full_name", getStringSafe(rs, "full_name"));
                    logs.add(log);
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("logs", logs);
            result.put("total", logs.size());
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("Error in getActivityLogs: " + e.getMessage());
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "データベースエラー: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Unexpected error in getActivityLogs: " + e.getMessage());
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "予期しないエラー: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== GET DATA METHODS ====================
    
    private void getUsers(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            stmt = conn.prepareStatement("SET NAMES utf8mb4");
            stmt.execute();
            stmt.close();
            
            String sql = "SELECT user_id, username, email, role, is_verified, is_seller, created_at " +
                        "FROM users WHERE deleted_at IS NULL ORDER BY user_id DESC";
            
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            
            List<Map<String, Object>> users = new ArrayList<>();
            
            while (rs.next()) {
                Map<String, Object> user = new HashMap<>();
                user.put("user_id", rs.getInt("user_id"));
                user.put("username", getStringSafe(rs, "username"));
                user.put("email", getStringSafe(rs, "email"));
                user.put("role", getStringSafe(rs, "role"));
                user.put("is_verified", rs.getBoolean("is_verified"));
                user.put("is_seller", rs.getBoolean("is_seller"));
                
                Timestamp createdAt = rs.getTimestamp("created_at");
                user.put("created_at", createdAt != null ? createdAt.toString() : "");
                
                users.add(user);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", users);
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "データベースエラー: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    private void getProducts(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            stmt = conn.prepareStatement("SET NAMES utf8mb4");
            stmt.execute();
            stmt.close();
            
            String sql = "SELECT p.product_id, p.product_name, p.price, p.stock_quantity, " +
                        "p.status, p.is_rental, p.condition, p.created_at, " +
                        "COALESCE(u.username, 'Unknown') as seller_name, " +
                        "p.user_id as seller_id " +
                        "FROM products p " +
                        "LEFT JOIN users u ON p.user_id = u.user_id " +
                        "ORDER BY p.product_id DESC";
            
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            
            List<Map<String, Object>> products = new ArrayList<>();
            
            while (rs.next()) {
                Map<String, Object> product = new HashMap<>();
                product.put("product_id", rs.getInt("product_id"));
                product.put("product_name", getStringSafe(rs, "product_name"));
                product.put("price", rs.getDouble("price"));
                product.put("stock_quantity", rs.getInt("stock_quantity"));
                product.put("status", getStringSafe(rs, "status"));
                product.put("is_rental", rs.getBoolean("is_rental"));
                product.put("condition", getStringSafe(rs, "condition"));
                product.put("seller_name", getStringSafe(rs, "seller_name"));
                product.put("seller_id", rs.getObject("seller_id"));
                
                Timestamp createdAt = rs.getTimestamp("created_at");
                product.put("created_at", createdAt != null ? createdAt.toString() : "");
                
                products.add(product);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", products);
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "データベースエラー: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    private void getCategories(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            stmt = conn.prepareStatement("SET NAMES utf8mb4");
            stmt.execute();
            stmt.close();
            
            String sql = "SELECT c.category_id, c.category_name, c.slug, c.display_order, " +
                        "c.is_active, c.image_url, c.icon_url, c.description, " +
                        "COALESCE(p.category_name, 'No Parent') as parent_name, c.parent_id " +
                        "FROM categories c " +
                        "LEFT JOIN categories p ON c.parent_id = p.category_id " +
                        "ORDER BY c.display_order";
            
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            
            List<Map<String, Object>> categories = new ArrayList<>();
            
            while (rs.next()) {
                Map<String, Object> category = new HashMap<>();
                category.put("category_id", rs.getInt("category_id"));
                category.put("category_name", getStringSafe(rs, "category_name"));
                category.put("slug", getStringSafe(rs, "slug"));
                category.put("display_order", rs.getInt("display_order"));
                category.put("is_active", rs.getBoolean("is_active"));
                category.put("image_url", getStringSafe(rs, "image_url"));
                category.put("icon_url", getStringSafe(rs, "icon_url"));
                category.put("description", getStringSafe(rs, "description"));
                category.put("parent_name", getStringSafe(rs, "parent_name"));
                category.put("parent_id", rs.getObject("parent_id"));
                categories.add(category);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", categories);
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "データベースエラー: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    private void getOrders(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            stmt = conn.prepareStatement("SET NAMES utf8mb4");
            stmt.execute();
            stmt.close();
            
            String sql = "SELECT o.order_id, o.order_number, o.total_amount, " +
                        "o.payment_status, o.order_status, o.created_at, " +
                        "COALESCE(u.username, 'Unknown') as username " +
                        "FROM orders o " +
                        "LEFT JOIN users u ON o.user_id = u.user_id " +
                        "ORDER BY o.order_id DESC";
            
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            
            List<Map<String, Object>> orders = new ArrayList<>();
            
            while (rs.next()) {
                Map<String, Object> order = new HashMap<>();
                order.put("order_id", rs.getInt("order_id"));
                order.put("order_number", getStringSafe(rs, "order_number"));
                order.put("total_amount", rs.getDouble("total_amount"));
                order.put("payment_status", getStringSafe(rs, "payment_status"));
                order.put("order_status", getStringSafe(rs, "order_status"));
                order.put("username", getStringSafe(rs, "username"));
                
                Timestamp createdAt = rs.getTimestamp("created_at");
                order.put("created_at", createdAt != null ? createdAt.toString() : "");
                
                orders.add(order);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", orders);
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "データベースエラー: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    private void getUserAddresses(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            stmt = conn.prepareStatement("SET NAMES utf8mb4");
            stmt.execute();
            stmt.close();
            
            String sql = "SELECT a.address_id, a.user_id, a.address_label, a.recipient_name, " +
                        "a.phone, a.postal_code, a.prefecture, a.city, a.address_line1, " +
                        "a.is_default, a.created_at, " +
                        "COALESCE(u.username, 'Unknown') as username " +
                        "FROM user_addresses a " +
                        "LEFT JOIN users u ON a.user_id = u.user_id " +
                        "ORDER BY a.address_id DESC";
            
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            
            List<Map<String, Object>> addresses = new ArrayList<>();
            
            while (rs.next()) {
                Map<String, Object> address = new HashMap<>();
                address.put("address_id", rs.getInt("address_id"));
                address.put("user_id", rs.getInt("user_id"));
                address.put("username", getStringSafe(rs, "username"));
                address.put("address_label", getStringSafe(rs, "address_label"));
                address.put("recipient_name", getStringSafe(rs, "recipient_name"));
                address.put("phone", getStringSafe(rs, "phone"));
                address.put("postal_code", getStringSafe(rs, "postal_code"));
                address.put("prefecture", getStringSafe(rs, "prefecture"));
                address.put("city", getStringSafe(rs, "city"));
                address.put("address_line1", getStringSafe(rs, "address_line1"));
                address.put("is_default", rs.getBoolean("is_default"));
                
                Timestamp createdAt = rs.getTimestamp("created_at");
                address.put("created_at", createdAt != null ? createdAt.toString() : "");
                
                addresses.add(address);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", addresses);
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "データベースエラー: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    private void getUserWallets(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            stmt = conn.prepareStatement("SET NAMES utf8mb4");
            stmt.execute();
            stmt.close();
            
            String sql = "SELECT w.wallet_id, w.user_id, w.balance, w.frozen_balance, " +
                        "w.total_earned, w.total_spent, w.last_transaction_at, " +
                        "w.created_at, COALESCE(u.username, 'Unknown') as username " +
                        "FROM user_wallets w " +
                        "LEFT JOIN users u ON w.user_id = u.user_id " +
                        "ORDER BY w.wallet_id DESC";
            
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            
            List<Map<String, Object>> wallets = new ArrayList<>();
            
            while (rs.next()) {
                Map<String, Object> wallet = new HashMap<>();
                wallet.put("wallet_id", rs.getInt("wallet_id"));
                wallet.put("user_id", rs.getInt("user_id"));
                wallet.put("username", getStringSafe(rs, "username"));
                wallet.put("balance", rs.getDouble("balance"));
                wallet.put("frozen_balance", rs.getDouble("frozen_balance"));
                wallet.put("total_earned", rs.getDouble("total_earned"));
                wallet.put("total_spent", rs.getDouble("total_spent"));
                wallet.put("last_transaction_at", rs.getTimestamp("last_transaction_at"));
                
                Timestamp createdAt = rs.getTimestamp("created_at");
                wallet.put("created_at", createdAt != null ? createdAt.toString() : "");
                
                wallets.add(wallet);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", wallets);
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "データベースエラー: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    private void getProductReviews(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            stmt = conn.prepareStatement("SET NAMES utf8mb4");
            stmt.execute();
            stmt.close();
            
            String sql = "SELECT r.review_id, r.product_id, r.user_id, r.rating, " +
                        "r.review_text, r.is_verified_purchase, r.status, r.created_at, " +
                        "COALESCE(u.username, 'Unknown') as username, " +
                        "COALESCE(p.product_name, 'Unknown Product') as product_name " +
                        "FROM product_reviews r " +
                        "LEFT JOIN users u ON r.user_id = u.user_id " +
                        "LEFT JOIN products p ON r.product_id = p.product_id " +
                        "ORDER BY r.review_id DESC";
            
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            
            List<Map<String, Object>> reviews = new ArrayList<>();
            
            while (rs.next()) {
                Map<String, Object> review = new HashMap<>();
                review.put("review_id", rs.getInt("review_id"));
                review.put("product_id", rs.getInt("product_id"));
                review.put("product_name", getStringSafe(rs, "product_name"));
                review.put("user_id", rs.getInt("user_id"));
                review.put("username", getStringSafe(rs, "username"));
                review.put("rating", rs.getInt("rating"));
                review.put("review_text", getStringSafe(rs, "review_text"));
                review.put("is_verified_purchase", rs.getBoolean("is_verified_purchase"));
                review.put("status", getStringSafe(rs, "status"));
                
                Timestamp createdAt = rs.getTimestamp("created_at");
                review.put("created_at", createdAt != null ? createdAt.toString() : "");
                
                reviews.add(review);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", reviews);
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "データベースエラー: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    private void getRentals(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            stmt = conn.prepareStatement("SET NAMES utf8mb4");
            stmt.execute();
            stmt.close();
            // Check if rentals table exists for current database
            stmt = conn.prepareStatement("SHOW TABLES LIKE 'rentals'");
            rs = stmt.executeQuery();
            boolean hasTable = rs.next();
            DatabaseConnection.closeResources(rs, stmt, null);

            if (!hasTable) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("data", new ArrayList<Map<String, Object>>());
                sendJsonResponse(response, result);
                return;
            }

            String sql = "SELECT r.rental_id, r.rental_number, r.start_date, r.end_date, " +
                        "r.rental_price, r.total_amount, r.payment_status, r.status, " +
                        "r.created_at, " +
                        "COALESCE(ru.username, 'Unknown') as renter_name, " +
                        "COALESCE(ou.username, 'Unknown') as owner_name, " +
                        "COALESCE(p.product_name, 'Unknown Product') as product_name " +
                        "FROM rentals r " +
                        "LEFT JOIN users ru ON r.renter_id = ru.user_id " +
                        "LEFT JOIN users ou ON r.owner_id = ou.user_id " +
                        "LEFT JOIN products p ON r.product_id = p.product_id " +
                        "ORDER BY r.rental_id DESC";
            
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            
            List<Map<String, Object>> rentals = new ArrayList<>();
            
            while (rs.next()) {
                Map<String, Object> rental = new HashMap<>();
                rental.put("rental_id", rs.getInt("rental_id"));
                rental.put("rental_number", getStringSafe(rs, "rental_number"));
                rental.put("product_name", getStringSafe(rs, "product_name"));
                rental.put("renter_name", getStringSafe(rs, "renter_name"));
                rental.put("owner_name", getStringSafe(rs, "owner_name"));
                rental.put("start_date", rs.getDate("start_date"));
                rental.put("end_date", rs.getDate("end_date"));
                rental.put("rental_price", rs.getDouble("rental_price"));
                rental.put("total_amount", rs.getDouble("total_amount"));
                rental.put("payment_status", getStringSafe(rs, "payment_status"));
                rental.put("status", getStringSafe(rs, "status"));
                
                Timestamp createdAt = rs.getTimestamp("created_at");
                rental.put("created_at", createdAt != null ? createdAt.toString() : "");
                
                rentals.add(rental);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", rentals);
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "データベースエラー: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    private void getVouchers(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            stmt = conn.prepareStatement("SET NAMES utf8mb4");
            stmt.execute();
            stmt.close();
            // Check if vouchers table exists for current database
            stmt = conn.prepareStatement("SHOW TABLES LIKE 'vouchers'");
            rs = stmt.executeQuery();
            boolean hasTable = rs.next();
            DatabaseConnection.closeResources(rs, stmt, null);

            if (!hasTable) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("data", new ArrayList<Map<String, Object>>());
                sendJsonResponse(response, result);
                return;
            }

            String sql = "SELECT voucher_id, voucher_code, voucher_name, discount_type, " +
                        "discount_value, min_purchase, max_discount, usage_limit, used_count, " +
                        "valid_from, valid_until, is_active, created_at " +
                        "FROM vouchers ORDER BY voucher_id DESC";
            
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            
            List<Map<String, Object>> vouchers = new ArrayList<>();
            
            while (rs.next()) {
                Map<String, Object> voucher = new HashMap<>();
                voucher.put("voucher_id", rs.getInt("voucher_id"));
                voucher.put("voucher_code", getStringSafe(rs, "voucher_code"));
                voucher.put("voucher_name", getStringSafe(rs, "voucher_name"));
                voucher.put("discount_type", getStringSafe(rs, "discount_type"));
                voucher.put("discount_value", rs.getDouble("discount_value"));
                voucher.put("min_purchase", rs.getDouble("min_purchase"));
                voucher.put("max_discount", rs.getDouble("max_discount"));
                voucher.put("usage_limit", rs.getInt("usage_limit"));
                voucher.put("used_count", rs.getInt("used_count"));
                voucher.put("valid_from", rs.getTimestamp("valid_from"));
                voucher.put("valid_until", rs.getTimestamp("valid_until"));
                voucher.put("is_active", rs.getBoolean("is_active"));
                
                Timestamp createdAt = rs.getTimestamp("created_at");
                voucher.put("created_at", createdAt != null ? createdAt.toString() : "");
                
                vouchers.add(voucher);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", vouchers);
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "データベースエラー: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    private void getBanners(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            stmt = conn.prepareStatement("SET NAMES utf8mb4");
            stmt.execute();
            stmt.close();
            
            String sql = "SELECT banner_id, title, image_url, link_url, position, " +
                        "display_order, is_active, start_date, end_date, created_at " +
                        "FROM banners ORDER BY display_order";
            
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            
            List<Map<String, Object>> banners = new ArrayList<>();
            
            while (rs.next()) {
                Map<String, Object> banner = new HashMap<>();
                banner.put("banner_id", rs.getInt("banner_id"));
                banner.put("title", getStringSafe(rs, "title"));
                banner.put("image_url", getStringSafe(rs, "image_url"));
                banner.put("link_url", getStringSafe(rs, "link_url"));
                banner.put("position", getStringSafe(rs, "position"));
                banner.put("display_order", rs.getInt("display_order"));
                banner.put("is_active", rs.getBoolean("is_active"));
                banner.put("start_date", rs.getTimestamp("start_date"));
                banner.put("end_date", rs.getTimestamp("end_date"));
                
                Timestamp createdAt = rs.getTimestamp("created_at");
                banner.put("created_at", createdAt != null ? createdAt.toString() : "");
                
                banners.add(banner);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", banners);
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "データベースエラー: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    private void getMessages(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            stmt = conn.prepareStatement("SET NAMES utf8mb4");
            stmt.execute();
            stmt.close();
            
            String sql = "SELECT m.message_id, m.sender_id, m.receiver_id, m.message_text, " +
                        "m.is_read, m.sent_at, " +
                        "COALESCE(s.username, 'Unknown') as sender_name, " +
                        "COALESCE(r.username, 'Unknown') as receiver_name, " +
                        "COALESCE(p.product_name, 'No Product') as product_name " +
                        "FROM messages m " +
                        "LEFT JOIN users s ON m.sender_id = s.user_id " +
                        "LEFT JOIN users r ON m.receiver_id = r.user_id " +
                        "LEFT JOIN products p ON m.product_id = p.product_id " +
                        "ORDER BY m.sent_at DESC";
            
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            
            List<Map<String, Object>> messages = new ArrayList<>();
            
            while (rs.next()) {
                Map<String, Object> message = new HashMap<>();
                message.put("message_id", rs.getInt("message_id"));
                message.put("sender_id", rs.getInt("sender_id"));
                message.put("sender_name", getStringSafe(rs, "sender_name"));
                message.put("receiver_id", rs.getInt("receiver_id"));
                message.put("receiver_name", getStringSafe(rs, "receiver_name"));
                message.put("product_name", getStringSafe(rs, "product_name"));
                message.put("message_text", getStringSafe(rs, "message_text"));
                message.put("is_read", rs.getBoolean("is_read"));
                message.put("sent_at", rs.getTimestamp("sent_at"));
                messages.add(message);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", messages);
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "データベースエラー: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    private void getNotifications(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            stmt = conn.prepareStatement("SET NAMES utf8mb4");
            stmt.execute();
            stmt.close();
            
            String sql = "SELECT n.notification_id, n.user_id, n.type, n.title, " +
                        "n.message, n.is_read, n.created_at, " +
                        "COALESCE(u.username, 'Unknown') as username " +
                        "FROM notifications n " +
                        "LEFT JOIN users u ON n.user_id = u.user_id " +
                        "ORDER BY n.created_at DESC";
            
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            
            List<Map<String, Object>> notifications = new ArrayList<>();
            
            while (rs.next()) {
                Map<String, Object> notification = new HashMap<>();
                notification.put("notification_id", rs.getInt("notification_id"));
                notification.put("user_id", rs.getInt("user_id"));
                notification.put("username", getStringSafe(rs, "username"));
                notification.put("type", getStringSafe(rs, "type"));
                notification.put("title", getStringSafe(rs, "title"));
                notification.put("message", getStringSafe(rs, "message"));
                notification.put("is_read", rs.getBoolean("is_read"));
                
                Timestamp createdAt = rs.getTimestamp("created_at");
                notification.put("created_at", createdAt != null ? createdAt.toString() : "");
                
                notifications.add(notification);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", notifications);
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "データベースエラー: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== OFFERS (Admin - all offers) ====================
    
    private void getOffers(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            stmt = conn.prepareStatement("SET NAMES utf8mb4");
            stmt.execute();
            stmt.close();
            
            // Check if offers table exists
            stmt = conn.prepareStatement("SHOW TABLES LIKE 'offers'");
            rs = stmt.executeQuery();
            boolean hasTable = rs.next();
            DatabaseConnection.closeResources(rs, stmt, null);
            
            List<Map<String, Object>> offers = new ArrayList<>();
            
            if (hasTable) {
                String sql = """
                    SELECT o.offer_id, o.product_id, o.buyer_id, o.seller_id, 
                           o.offer_price, o.message, o.status, o.created_at, o.updated_at,
                           p.product_name, p.price as product_price, p.image_url, p.status as product_status,
                           ub.username as buyer_username, ub.full_name as buyer_name, ub.avatar_url as buyer_avatar,
                           us.username as seller_username, us.full_name as seller_name, us.avatar_url as seller_avatar
                    FROM offers o
                    LEFT JOIN products p ON o.product_id = p.product_id
                    LEFT JOIN users ub ON o.buyer_id = ub.user_id
                    LEFT JOIN users us ON o.seller_id = us.user_id
                    ORDER BY o.created_at DESC
                    """;
                
                stmt = conn.prepareStatement(sql);
                rs = stmt.executeQuery();
                
                while (rs.next()) {
                    Map<String, Object> offer = new HashMap<>();
                    offer.put("offer_id", rs.getInt("offer_id"));
                    offer.put("product_id", rs.getInt("product_id"));
                    offer.put("buyer_id", rs.getInt("buyer_id"));
                    offer.put("seller_id", rs.getInt("seller_id"));
                    offer.put("offer_price", rs.getDouble("offer_price"));
                    offer.put("message", rs.getString("message"));
                    offer.put("status", rs.getString("status"));
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    Timestamp updatedAt = rs.getTimestamp("updated_at");
                    offer.put("created_at", createdAt != null ? createdAt.toString() : null);
                    offer.put("updated_at", updatedAt != null ? updatedAt.toString() : null);
                    offer.put("product_name", rs.getString("product_name"));
                    offer.put("product_price", rs.getDouble("product_price"));
                    offer.put("image_url", rs.getString("image_url"));
                    offer.put("product_status", rs.getString("product_status"));
                    offer.put("buyer_username", rs.getString("buyer_username"));
                    offer.put("buyer_name", rs.getString("buyer_name"));
                    offer.put("buyer_avatar", rs.getString("buyer_avatar"));
                    offer.put("seller_username", rs.getString("seller_username"));
                    offer.put("seller_name", rs.getString("seller_name"));
                    offer.put("seller_avatar", rs.getString("seller_avatar"));
                    offers.add(offer);
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("offers", offers);
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "データベースエラー: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== CRUD OPERATIONS ====================
    
    private void getRecord(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        String table = request.getParameter("table");
        String id = request.getParameter("id");
        
        if (table == null || table.trim().isEmpty()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "テーブルパラメータが必要です");
            return;
        }
        
        if (id == null || id.trim().isEmpty()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "IDパラメータが必要です");
            return;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        PreparedStatement setUtf8 = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            setUtf8 = conn.prepareStatement("SET NAMES utf8mb4");
            setUtf8.execute();
            setUtf8.close();
            
            String idColumn = getIdColumn(table);
            String sql = "SELECT * FROM " + table + " WHERE " + idColumn + " = ?";
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, Integer.parseInt(id));
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                Map<String, Object> record = resultSetToMap(rs);
                
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("record", record);
                
                sendJsonResponse(response, result);
            } else {
                sendError(response, HttpServletResponse.SC_NOT_FOUND, "レコードが見つかりません");
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "データベースエラー: " + e.getMessage());
        } catch (NumberFormatException e) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "無効なID形式");
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    private void createRecord(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        String table = request.getParameter("table");
        
        if (table == null || table.trim().isEmpty()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "テーブルパラメータが必要です");
            return;
        }
        
        // UTF-8でボディを読み込む
        StringBuilder jsonBuilder = new StringBuilder();
        String line;
        try (BufferedReader reader = request.getReader()) {
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
        }
        
        String body = jsonBuilder.toString();
        System.out.println("Create record - Table: " + table);
        System.out.println("Body received: " + body);
        
        if (body == null || body.trim().isEmpty()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "リクエストボディが必要です");
            return;
        }
        
        JsonObject jsonData;
        try {
            JsonElement jsonElement = JsonParser.parseString(body);
            if (!jsonElement.isJsonObject()) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "無効なJSON形式");
                return;
            }
            jsonData = jsonElement.getAsJsonObject();
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "JSON解析エラー: " + e.getMessage());
            return;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        PreparedStatement setUtf8 = null;
        ResultSet generatedKeys = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            // UTF-8設定
            setUtf8 = conn.prepareStatement("SET NAMES utf8mb4");
            setUtf8.execute();
            setUtf8.close();
            
            // Build SQL dynamically
            StringBuilder sql = new StringBuilder("INSERT INTO " + table + " (");
            StringBuilder values = new StringBuilder(" VALUES (");
            List<Object> params = new ArrayList<>();
            
            for (Map.Entry<String, JsonElement> entry : jsonData.entrySet()) {
                String key = entry.getKey();
                sql.append(key).append(", ");
                values.append("?, ");
                
                JsonElement value = entry.getValue();
                if (value.isJsonNull()) {
                    params.add(null);
                } else if (value.isJsonPrimitive()) {
                    if (value.getAsJsonPrimitive().isBoolean()) {
                        params.add(value.getAsBoolean());
                    } else if (value.getAsJsonPrimitive().isNumber()) {
                        Number number = value.getAsNumber();
                        // Handle integer fields specifically (display_order, parent_id, etc.)
                        if (key.equals("display_order") || key.equals("parent_id") || 
                            key.equals("category_id") || key.endsWith("_id")) {
                            params.add(number.intValue());
                        } else if (number instanceof Integer) {
                            params.add(number.intValue());
                        } else if (number instanceof Long) {
                            params.add(number.longValue());
                        } else if (number instanceof Double || number instanceof Float) {
                            params.add(number.doubleValue());
                        } else {
                            // For BigDecimal and other Number types, convert to appropriate type
                            double doubleVal = number.doubleValue();
                            if (doubleVal == (int) doubleVal) {
                                params.add((int) doubleVal);
                            } else {
                                params.add(doubleVal);
                            }
                        }
                    } else {
                        String stringValue = value.getAsString();
                        params.add(stringValue);
                        System.out.println("Parameter [" + key + "]: " + stringValue);
                    }
                } else {
                    params.add(value.toString());
                }
            }
            
            if (params.isEmpty()) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "データが提供されていません");
                return;
            }
            
            sql.setLength(sql.length() - 2);
            values.setLength(values.length() - 2);
            sql.append(")").append(values).append(")");
            
            System.out.println("SQL: " + sql.toString());
            
            stmt = conn.prepareStatement(sql.toString(), PreparedStatement.RETURN_GENERATED_KEYS);
            
            // Get column names for type checking
            List<String> columnNames = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : jsonData.entrySet()) {
                columnNames.add(entry.getKey());
            }
            
            for (int i = 0; i < params.size(); i++) {
                Object param = params.get(i);
                String columnName = i < columnNames.size() ? columnNames.get(i) : "";
                
                if (param == null) {
                    stmt.setNull(i + 1, java.sql.Types.NULL);
                } else if (param instanceof String) {
                    stmt.setString(i + 1, (String) param);
                } else if (param instanceof Integer) {
                    stmt.setInt(i + 1, (Integer) param);
                } else if (param instanceof Long) {
                    stmt.setLong(i + 1, (Long) param);
                } else if (param instanceof Double) {
                    stmt.setDouble(i + 1, (Double) param);
                } else if (param instanceof Boolean) {
                    stmt.setBoolean(i + 1, (Boolean) param);
                } else {
                    stmt.setObject(i + 1, param);
                }
            }
            
            int affected = stmt.executeUpdate();
            
            Map<String, Object> result = new HashMap<>();
            if (affected > 0) {
                generatedKeys = stmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    result.put("generatedId", generatedKeys.getObject(1));
                }
                result.put("success", true);
                result.put("message", "レコードが正常に作成されました");
            } else {
                result.put("success", false);
                result.put("message", "レコードの作成に失敗しました");
            }
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "データベースエラー: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(generatedKeys, stmt, conn);
        }
    }
    
    private void updateRecord(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        String table = request.getParameter("table");
        String id = request.getParameter("id");
        
        if (table == null || table.trim().isEmpty()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "テーブルパラメータが必要です");
            return;
        }
        
        if (id == null || id.trim().isEmpty()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "IDパラメータが必要です");
            return;
        }
        
        StringBuilder jsonBuilder = new StringBuilder();
        String line;
        try (BufferedReader reader = request.getReader()) {
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
        }
        
        String body = jsonBuilder.toString();
        System.out.println("Update record - Table: " + table + ", ID: " + id);
        System.out.println("Body received: " + body);
        
        if (body == null || body.trim().isEmpty()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "リクエストボディが必要です");
            return;
        }
        
        JsonObject jsonData;
        try {
            JsonElement jsonElement = JsonParser.parseString(body);
            if (!jsonElement.isJsonObject()) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "無効なJSON形式");
                return;
            }
            jsonData = jsonElement.getAsJsonObject();
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "JSON解析エラー: " + e.getMessage());
            return;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        PreparedStatement setUtf8 = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            // UTF-8設定
            setUtf8 = conn.prepareStatement("SET NAMES utf8mb4");
            setUtf8.execute();
            setUtf8.close();
            
            StringBuilder sql = new StringBuilder("UPDATE " + table + " SET ");
            List<Object> params = new ArrayList<>();
            
            for (Map.Entry<String, JsonElement> entry : jsonData.entrySet()) {
                String key = entry.getKey();
                sql.append(key).append(" = ?, ");
                
                JsonElement value = entry.getValue();
                if (value.isJsonNull()) {
                    params.add(null);
                } else if (value.isJsonPrimitive()) {
                    if (value.getAsJsonPrimitive().isBoolean()) {
                        params.add(value.getAsBoolean());
                    } else if (value.getAsJsonPrimitive().isNumber()) {
                        Number number = value.getAsNumber();
                        // Handle integer fields specifically (display_order, parent_id, etc.)
                        if (key.equals("display_order") || key.equals("parent_id") || 
                            key.equals("category_id") || key.endsWith("_id")) {
                            params.add(number.intValue());
                        } else if (number instanceof Integer) {
                            params.add(number.intValue());
                        } else if (number instanceof Long) {
                            params.add(number.longValue());
                        } else if (number instanceof Double || number instanceof Float) {
                            params.add(number.doubleValue());
                        } else {
                            // For BigDecimal and other Number types, convert to appropriate type
                            double doubleVal = number.doubleValue();
                            if (doubleVal == (int) doubleVal) {
                                params.add((int) doubleVal);
                            } else {
                                params.add(doubleVal);
                            }
                        }
                    } else {
                        String stringValue = value.getAsString();
                        params.add(stringValue);
                        System.out.println("Parameter [" + key + "]: " + stringValue);
                    }
                } else {
                    params.add(value.toString());
                }
            }
            
            if (params.isEmpty()) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "更新用のデータが提供されていません");
                return;
            }
            
            sql.setLength(sql.length() - 2);
            
            String idColumn = getIdColumn(table);
            sql.append(" WHERE ").append(idColumn).append(" = ?");
            params.add(Integer.parseInt(id));
            
            System.out.println("SQL: " + sql.toString());
            
            stmt = conn.prepareStatement(sql.toString());
            
            // Get column names for type checking (excluding the ID parameter at the end)
            List<String> columnNames = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : jsonData.entrySet()) {
                columnNames.add(entry.getKey());
            }
            
            for (int i = 0; i < params.size(); i++) {
                Object param = params.get(i);
                
                if (param == null) {
                    stmt.setNull(i + 1, java.sql.Types.NULL);
                } else if (param instanceof String) {
                    stmt.setString(i + 1, (String) param);
                } else if (param instanceof Integer) {
                    stmt.setInt(i + 1, (Integer) param);
                } else if (param instanceof Long) {
                    stmt.setLong(i + 1, (Long) param);
                } else if (param instanceof Double) {
                    stmt.setDouble(i + 1, (Double) param);
                } else if (param instanceof Boolean) {
                    stmt.setBoolean(i + 1, (Boolean) param);
                } else {
                    stmt.setObject(i + 1, param);
                }
            }
            
            int affected = stmt.executeUpdate();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", affected > 0);
            result.put("message", affected > 0 ? "レコードが正常に更新されました" : "更新するレコードが見つかりません");
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "データベースエラー: " + e.getMessage());
        } catch (NumberFormatException e) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "無効なID形式");
        } finally {
            DatabaseConnection.closeResources(null, stmt, conn);
        }
    }
    
    private void deleteRecord(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        String table = request.getParameter("table");
        String id = request.getParameter("id");
        
        if (table == null || table.trim().isEmpty()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "テーブルパラメータが必要です");
            return;
        }
        
        if (id == null || id.trim().isEmpty()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "IDパラメータが必要です");
            return;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        PreparedStatement setUtf8 = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            setUtf8 = conn.prepareStatement("SET NAMES utf8mb4");
            setUtf8.execute();
            setUtf8.close();
            
            String idColumn = getIdColumn(table);
            String sql;
            
            // Use soft delete for users table to maintain ID sequence
            if ("users".equals(table)) {
                // Soft delete: set deleted_at instead of hard delete
                sql = "UPDATE " + table + " SET deleted_at = NOW(), updated_at = NOW() WHERE " + idColumn + " = ? AND deleted_at IS NULL";
            } else {
                // Hard delete for other tables
                sql = "DELETE FROM " + table + " WHERE " + idColumn + " = ?";
            }
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, Integer.parseInt(id));
            
            int affected = stmt.executeUpdate();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", affected > 0);
            result.put("message", affected > 0 ? "レコードが正常に削除されました" : "削除するレコードが見つかりません");
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "データベースエラー: " + e.getMessage());
        } catch (NumberFormatException e) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "無効なID形式");
        } finally {
            DatabaseConnection.closeResources(null, stmt, conn);
        }
    }
    
    // ==================== HELPER METHODS ====================
    
    private boolean isAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return false;
        
        String role = (String) session.getAttribute("role");
        return "admin".equals(role);
    }
    
    private String getIdColumn(String table) {
        Map<String, String> idColumns = new HashMap<>();
        idColumns.put("users", "user_id");
        idColumns.put("products", "product_id");
        idColumns.put("categories", "category_id");
        idColumns.put("orders", "order_id");
        idColumns.put("user_addresses", "address_id");
        idColumns.put("user_wallets", "wallet_id");
        idColumns.put("product_reviews", "review_id");
        idColumns.put("vouchers", "voucher_id");
        idColumns.put("banners", "banner_id");
        idColumns.put("rentals", "rental_id");
        idColumns.put("messages", "message_id");
        idColumns.put("notifications", "notification_id");
        idColumns.put("wallet_transactions", "transaction_id");
        idColumns.put("product_images", "image_id");
        idColumns.put("product_specifications", "spec_id");
        idColumns.put("voucher_usage", "usage_id");
        idColumns.put("site_settings", "setting_id");
        idColumns.put("activity_logs", "log_id");
        idColumns.put("carts", "cart_id");
        idColumns.put("user_favorites", "favorite_id");
        idColumns.put("order_items", "item_id");
        idColumns.put("offers", "offer_id");
        
        return idColumns.getOrDefault(table, "id");
    }
    
    private Map<String, Object> resultSetToMap(ResultSet rs) throws SQLException {
        Map<String, Object> map = new HashMap<>();
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        
        for (int i = 1; i <= columnCount; i++) {
            String columnName = metaData.getColumnName(i);
            Object value = rs.getObject(i);
            
            // 文字列の場合、UTF-8として正しく取得
            if (value instanceof String) {
                value = rs.getString(i);
            }
            
            map.put(columnName, value);
        }
        
        return map;
    }
    
    private String getStringSafe(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return value != null ? value : "";
    }
    
    private String getTimeAgo(java.sql.Timestamp timestamp) {
        if (timestamp == null) return "不明";
        
        long diff = System.currentTimeMillis() - timestamp.getTime();
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) return days + "日前";
        if (hours > 0) return hours + "時間前";
        if (minutes > 0) return minutes + "分前";
        return "たった今";
    }
    
    private void sendJsonResponse(HttpServletResponse response, Object data) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write(gson.toJson(data));
        response.getWriter().flush();
    }
    
    private void sendError(HttpServletResponse response, int status, String message) 
            throws IOException {
        response.setStatus(status);
        
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("message", message);
        
        response.getWriter().write(gson.toJson(error));
        response.getWriter().flush();
    }
}