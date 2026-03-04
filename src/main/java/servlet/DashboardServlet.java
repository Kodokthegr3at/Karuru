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

@WebServlet({"/DashboardServlet", "/Dashboard"})
public class DashboardServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        FilterEncodingUTF8.configureUTF8ForJSON(request, response);
        
        HttpSession session = request.getSession(false);
        if (session == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        // Try to get userId from session (check both user_id and userId)
        Integer userId = (Integer) session.getAttribute("user_id");
        if (userId == null) {
            Object userIdObj = session.getAttribute("userId");
            if (userIdObj instanceof Integer) {
                userId = (Integer) userIdObj;
            } else if (userIdObj != null) {
                try {
                    userId = Integer.valueOf(userIdObj.toString());
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
        }
        
        if (userId == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        String action = request.getParameter("action");
        
        try {
            if (action == null || action.isEmpty()) {
                action = "getStats"; // Default action
            }
            
            switch (action) {
                case "getStats":
                    getStats(request, response, userId);
                    break;
                case "getSales":
                    getSales(request, response, userId);
                    break;
                default:
                    sendError(response, "Invalid action: " + action);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage());
        }
    }
    
    private void getStats(HttpServletRequest request, HttpServletResponse response, Integer userId) 
            throws IOException, SQLException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            Map<String, Object> stats = new HashMap<>();
            
            // Get active listings count
            String activeListingsSql = "SELECT COUNT(*) as count FROM products WHERE user_id = ? AND status = 'available'";
            stmt = conn.prepareStatement(activeListingsSql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            if (rs.next()) {
                stats.put("activeListings", rs.getInt("count"));
            } else {
                stats.put("activeListings", 0);
            }
            rs.close();
            stmt.close();
            
            // Get total sales (all time)
            String salesSql = "SELECT COALESCE(SUM(oi.subtotal), 0) as total " +
                            "FROM order_items oi " +
                            "INNER JOIN orders o ON oi.order_id = o.order_id " +
                            "WHERE oi.seller_id = ? AND o.payment_status = 'paid'";
            stmt = conn.prepareStatement(salesSql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            if (rs.next()) {
                java.math.BigDecimal total = rs.getBigDecimal("total");
                stats.put("totalSales", total != null ? total.doubleValue() : 0.0);
            } else {
                stats.put("totalSales", 0.0);
            }
            rs.close();
            stmt.close();
            
            // Get sales this month
            String salesThisMonthSql = "SELECT COALESCE(SUM(oi.subtotal), 0) as total " +
                            "FROM order_items oi " +
                            "INNER JOIN orders o ON oi.order_id = o.order_id " +
                            "WHERE oi.seller_id = ? AND o.payment_status = 'paid' " +
                            "AND YEAR(oi.created_at) = YEAR(CURRENT_DATE) " +
                            "AND MONTH(oi.created_at) = MONTH(CURRENT_DATE)";
            stmt = conn.prepareStatement(salesThisMonthSql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            if (rs.next()) {
                java.math.BigDecimal total = rs.getBigDecimal("total");
                stats.put("salesThisMonth", total != null ? total.doubleValue() : 0.0);
            } else {
                stats.put("salesThisMonth", 0.0);
            }
            rs.close();
            stmt.close();
            
            // Get sales last month for comparison
            String salesLastMonthSql = "SELECT COALESCE(SUM(oi.subtotal), 0) as total " +
                            "FROM order_items oi " +
                            "INNER JOIN orders o ON oi.order_id = o.order_id " +
                            "WHERE oi.seller_id = ? AND o.payment_status = 'paid' " +
                            "AND YEAR(oi.created_at) = YEAR(DATE_SUB(CURRENT_DATE, INTERVAL 1 MONTH)) " +
                            "AND MONTH(oi.created_at) = MONTH(DATE_SUB(CURRENT_DATE, INTERVAL 1 MONTH))";
            stmt = conn.prepareStatement(salesLastMonthSql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            if (rs.next()) {
                java.math.BigDecimal total = rs.getBigDecimal("total");
                stats.put("salesLastMonth", total != null ? total.doubleValue() : 0.0);
            } else {
                stats.put("salesLastMonth", 0.0);
            }
            rs.close();
            stmt.close();
            
            // Get total orders (as buyer)
            String ordersSql = "SELECT COUNT(*) as count FROM orders WHERE user_id = ?";
            stmt = conn.prepareStatement(ordersSql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            if (rs.next()) {
                stats.put("totalOrders", rs.getInt("count"));
            } else {
                stats.put("totalOrders", 0);
            }
            rs.close();
            stmt.close();
            
            // Get pending orders count
            String pendingOrdersSql = "SELECT COUNT(*) as count FROM orders WHERE user_id = ? AND order_status IN ('pending', 'confirmed', 'processing')";
            stmt = conn.prepareStatement(pendingOrdersSql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            if (rs.next()) {
                stats.put("pendingOrders", rs.getInt("count"));
            } else {
                stats.put("pendingOrders", 0);
            }
            rs.close();
            stmt.close();
            
            // Get total products (all statuses)
            String allProductsSql = "SELECT COUNT(*) as count FROM products WHERE user_id = ?";
            stmt = conn.prepareStatement(allProductsSql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            if (rs.next()) {
                stats.put("totalProducts", rs.getInt("count"));
            } else {
                stats.put("totalProducts", 0);
            }
            rs.close();
            stmt.close();
            
            // Get sold products count
            String soldProductsSql = "SELECT COUNT(*) as count FROM products WHERE user_id = ? AND status = 'sold'";
            stmt = conn.prepareStatement(soldProductsSql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            if (rs.next()) {
                stats.put("soldProducts", rs.getInt("count"));
            } else {
                stats.put("soldProducts", 0);
            }
            rs.close();
            stmt.close();
            
            // Get unread messages count
            String messagesSql = "SELECT COUNT(*) as count FROM messages WHERE receiver_id = ? AND is_read = 0";
            stmt = conn.prepareStatement(messagesSql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            if (rs.next()) {
                stats.put("unreadMessages", rs.getInt("count"));
            } else {
                stats.put("unreadMessages", 0);
            }
            rs.close();
            stmt.close();
            
            stats.put("success", true);
            
            PrintWriter out = response.getWriter();
            out.print(gson.toJson(stats));
            out.flush();
            
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    private void getSales(HttpServletRequest request, HttpServletResponse response, Integer userId) 
            throws IOException, SQLException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = "SELECT DATE(oi.created_at) as sale_date, " +
                        "COUNT(DISTINCT oi.order_id) as order_count, " +
                        "SUM(oi.subtotal) as revenue " +
                        "FROM order_items oi " +
                        "INNER JOIN orders o ON oi.order_id = o.order_id " +
                        "WHERE oi.seller_id = ? AND o.payment_status = 'paid' " +
                        "GROUP BY DATE(oi.created_at) " +
                        "ORDER BY sale_date DESC " +
                        "LIMIT 30";
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("sales", new java.util.ArrayList<>());
            
            while (rs.next()) {
                Map<String, Object> sale = new HashMap<>();
                java.sql.Date saleDate = rs.getDate("sale_date");
                sale.put("date", saleDate != null ? saleDate.toString() : null);
                sale.put("sale_date", saleDate != null ? saleDate.toString() : null);
                sale.put("orderCount", rs.getInt("order_count"));
                sale.put("order_count", rs.getInt("order_count"));
                java.math.BigDecimal revenue = rs.getBigDecimal("revenue");
                sale.put("revenue", revenue != null ? revenue.doubleValue() : 0.0);
                sale.put("total", revenue != null ? revenue.doubleValue() : 0.0);
                ((java.util.List<Map<String, Object>>) result.get("sales")).add(sale);
            }
            
            PrintWriter out = response.getWriter();
            out.print(gson.toJson(result));
            out.flush();
            
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    private void sendError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        error.put("success", false);
        
        PrintWriter out = response.getWriter();
        out.print(gson.toJson(error));
        out.flush();
    }
}

