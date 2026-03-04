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

@WebServlet("/OrderServlet")
public class OrderServlet extends HttpServlet {
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
                case "getUserOrders":
                    getUserOrders(request, response);
                    break;
                case "getOrderDetails":
                    getOrderDetails(request, response);
                    break;
                case "getOrderCount":
                    getOrderCount(request, response);
                    break;
                case "getOrderByProduct":
                    getOrderByProduct(request, response);
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
                case "updateOrderStatus":
                    updateOrderStatus(request, response);
                    break;
                case "updateShippingInfo":
                    updateShippingInfo(request, response);
                    break;
                case "confirmOrder":
                    confirmOrder(request, response);
                    break;
                default:
                    sendError(response, "Invalid action: " + action);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage());
        }
    }
    
    // ==================== GET USER ORDERS ====================
    private void getUserOrders(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        Integer userId = getSessionUserId(session);
        if (session == null || userId == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        List<Map<String, Object>> orders = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String statusFilter = request.getParameter("status");
            
            StringBuilder sql = new StringBuilder("""
                SELECT 
                    o.order_id, o.user_id, o.order_number, o.total_amount, o.order_status, 
                    o.payment_status, o.payment_method, o.created_at, o.shipped_at,
                    o.delivered_at, o.tracking_number, o.courier,
                    COUNT(oi.item_id) as item_count
                FROM orders o
                LEFT JOIN order_items oi ON o.order_id = oi.order_id
                WHERE o.user_id = ?
                """);
            
            // Add status filter if provided
            if (statusFilter != null && !statusFilter.trim().isEmpty()) {
                sql.append("AND o.order_status = ? ");
            }
            
            sql.append("""
                GROUP BY o.order_id, o.user_id, o.order_number, o.total_amount, o.order_status, 
                         o.payment_status, o.payment_method, o.created_at, o.shipped_at,
                         o.delivered_at, o.tracking_number, o.courier
                ORDER BY o.created_at DESC
                """);
            
            stmt = conn.prepareStatement(sql.toString());
            stmt.setInt(1, userId);
            if (statusFilter != null && !statusFilter.trim().isEmpty()) {
                stmt.setString(2, statusFilter.trim());
            }
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> order = mapOrderFromResultSet(rs);
                
                // Get order items
                List<Map<String, Object>> orderItems = getOrderItems(conn, rs.getInt("order_id"));
                order.put("items", orderItems);
                
                orders.add(order);
            }
            
            System.out.println("✅ Orders found: " + orders.size() + " for user: " + userId);
            
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("❌ SQL Error in getUserOrders: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
        
        // Wrap orders in a response object
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("orders", orders);
        result.put("count", orders.size());
        sendJsonResponse(response, result);
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
    
    // ==================== GET ORDER DETAILS ====================
    private void getOrderDetails(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        Integer userId = getSessionUserId(session);
        if (session == null || userId == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        String orderIdStr = request.getParameter("order_id");
        if (orderIdStr == null || orderIdStr.isEmpty()) {
            // Try alternative parameter name
            orderIdStr = request.getParameter("orderId");
            if (orderIdStr == null || orderIdStr.isEmpty()) {
                sendError(response, "Order ID is required");
                return;
            }
        }
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            int orderId = Integer.parseInt(orderIdStr);
            conn = DatabaseConnection.getConnection();
            
            // Allow both buyer (o.user_id) and seller (oi.seller_id) to view order details
            String sql = """
                SELECT 
                    o.order_id, o.user_id, o.order_number, o.total_amount, o.order_status, 
                    o.payment_status, o.payment_method, o.created_at, o.shipped_at,
                    o.delivered_at, o.tracking_number, o.courier, o.notes,
                    o.subtotal, o.shipping_cost, o.discount_amount, o.tax_amount,
                    a.recipient_name, a.phone, a.postal_code, a.prefecture, 
                    a.city, a.address_line1, a.address_line2, a.building_name
                FROM orders o
                LEFT JOIN user_addresses a ON o.shipping_address_id = a.address_id
                WHERE o.order_id = ?
                AND (o.user_id = ? OR EXISTS (
                    SELECT 1 FROM order_items oi WHERE oi.order_id = o.order_id AND oi.seller_id = ?
                ))
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, orderId);
            stmt.setInt(2, userId);
            stmt.setInt(3, userId);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                Map<String, Object> order = mapOrderDetailFromResultSet(rs);
                
                // Get order items
                List<Map<String, Object>> orderItems = getOrderItems(conn, orderId);
                order.put("items", orderItems);
                
                sendJsonResponse(response, order);
            } else {
                // Debug: log when order not found (order may exist but belong to different user)
                System.out.println("[OrderServlet] getOrderDetails: order_id=" + orderId + " not found for session user_id=" + userId);
                sendError(response, "注文が見つかりません");
            }
            
        } catch (NumberFormatException e) {
            sendError(response, "Invalid order ID format");
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== GET ORDER COUNT ====================
    private void getOrderCount(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        Integer userId = getSessionUserId(session);
        if (session == null || userId == null) {
            sendJsonResponse(response, Map.of("count", 0));
            return;
        }
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = "SELECT COUNT(*) as order_count FROM orders WHERE user_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            
            int count = 0;
            if (rs.next()) {
                count = rs.getInt("order_count");
            }
            
            sendJsonResponse(response, Map.of("count", count));
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendJsonResponse(response, Map.of("count", 0));
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== GET ORDER ITEMS ====================
    private List<Map<String, Object>> getOrderItems(Connection conn, int orderId) throws SQLException {
        List<Map<String, Object>> items = new ArrayList<>();
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            String sql = """
                SELECT 
                    oi.item_id, oi.product_id, oi.product_name, oi.quantity, 
                    oi.price, oi.subtotal, oi.status, oi.seller_id,
                    p.image_url, p.is_rental
                FROM order_items oi
                LEFT JOIN products p ON oi.product_id = p.product_id
                WHERE oi.order_id = ?
                ORDER BY oi.item_id
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, orderId);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> item = new HashMap<>();
                item.put("item_id", rs.getInt("item_id"));
                item.put("product_id", rs.getInt("product_id"));
                item.put("product_name", rs.getString("product_name"));
                item.put("quantity", rs.getInt("quantity"));
                item.put("price", rs.getDouble("price"));
                item.put("subtotal", rs.getDouble("subtotal"));
                item.put("status", rs.getString("status"));
                item.put("seller_id", rs.getInt("seller_id"));
                item.put("image_url", rs.getString("image_url"));
                item.put("is_rental", rs.getBoolean("is_rental"));
                
                items.add(item);
            }
        } finally {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
        }
        
        return items;
    }
    
    // ==================== MAP ORDER FROM RESULTSET ====================
    private Map<String, Object> mapOrderFromResultSet(ResultSet rs) throws SQLException {
        Map<String, Object> order = new HashMap<>();
        
        int orderId = rs.getInt("order_id");
        order.put("order_id", orderId);
        order.put("user_id", rs.getInt("user_id"));
        
        // Get order_number, if null generate one from order_id
        String orderNumber = rs.getString("order_number");
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            orderNumber = "ORD-" + String.format("%06d", orderId);
        }
        order.put("order_number", orderNumber);
        
        order.put("total_amount", rs.getDouble("total_amount"));
        order.put("order_status", rs.getString("order_status"));
        order.put("payment_status", rs.getString("payment_status"));
        order.put("payment_method", rs.getString("payment_method"));
        
        // Format timestamps as ISO string for JavaScript
        java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            order.put("created_at", createdAt.toInstant().toString());
        } else {
            // If created_at is null, use current timestamp as fallback
            order.put("created_at", java.time.Instant.now().toString());
        }
        
        java.sql.Timestamp shippedAt = rs.getTimestamp("shipped_at");
        if (shippedAt != null) {
            order.put("shipped_at", shippedAt.toInstant().toString());
        } else {
            order.put("shipped_at", null);
        }
        
        java.sql.Timestamp deliveredAt = rs.getTimestamp("delivered_at");
        if (deliveredAt != null) {
            order.put("delivered_at", deliveredAt.toInstant().toString());
        } else {
            order.put("delivered_at", null);
        }
        
        order.put("tracking_number", rs.getString("tracking_number"));
        order.put("courier", rs.getString("courier"));
        order.put("item_count", rs.getInt("item_count"));
        order.put("items_count", rs.getInt("item_count")); // Alternative name
        
        return order;
    }
    
    // ==================== MAP ORDER DETAIL FROM RESULTSET ====================
    private Map<String, Object> mapOrderDetailFromResultSet(ResultSet rs) throws SQLException {
        Map<String, Object> order = new HashMap<>();
        
        int orderId = rs.getInt("order_id");
        order.put("order_id", orderId);
        order.put("user_id", rs.getInt("user_id")); // buyer
        
        // Get order_number, if null generate one from order_id
        String orderNumber = rs.getString("order_number");
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            orderNumber = "ORD-" + String.format("%06d", orderId);
        }
        order.put("order_number", orderNumber);
        
        order.put("total_amount", rs.getDouble("total_amount"));
        order.put("order_status", rs.getString("order_status"));
        order.put("payment_status", rs.getString("payment_status"));
        order.put("payment_method", rs.getString("payment_method"));
        
        // Format timestamps as ISO string for JavaScript
        java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            order.put("created_at", createdAt.toInstant().toString());
        } else {
            // If created_at is null, use current timestamp as fallback
            order.put("created_at", java.time.Instant.now().toString());
        }
        
        java.sql.Timestamp shippedAt = rs.getTimestamp("shipped_at");
        if (shippedAt != null) {
            order.put("shipped_at", shippedAt.toInstant().toString());
        } else {
            order.put("shipped_at", null);
        }
        
        java.sql.Timestamp deliveredAt = rs.getTimestamp("delivered_at");
        if (deliveredAt != null) {
            order.put("delivered_at", deliveredAt.toInstant().toString());
        } else {
            order.put("delivered_at", null);
        }
        
        order.put("tracking_number", rs.getString("tracking_number"));
        order.put("courier", rs.getString("courier"));
        order.put("notes", rs.getString("notes"));
        
        // Handle nullable numeric fields
        double subtotal = rs.getDouble("subtotal");
        order.put("subtotal", rs.wasNull() ? 0.0 : subtotal);
        
        double shippingCost = rs.getDouble("shipping_cost");
        order.put("shipping_cost", rs.wasNull() ? 0.0 : shippingCost);
        
        double discountAmount = rs.getDouble("discount_amount");
        order.put("discount_amount", rs.wasNull() ? 0.0 : discountAmount);
        
        double taxAmount = rs.getDouble("tax_amount");
        order.put("tax_amount", rs.wasNull() ? 0.0 : taxAmount);
        
        // Shipping address
        Map<String, Object> address = new HashMap<>();
        address.put("recipient_name", rs.getString("recipient_name"));
        address.put("phone", rs.getString("phone"));
        address.put("postal_code", rs.getString("postal_code"));
        address.put("prefecture", rs.getString("prefecture"));
        address.put("city", rs.getString("city"));
        address.put("address_line1", rs.getString("address_line1"));
        address.put("address_line2", rs.getString("address_line2"));
        address.put("building_name", rs.getString("building_name"));
        order.put("shipping_address", address);
        
        return order;
    }
    
    // ==================== GET ORDER BY PRODUCT ID ====================
    private void getOrderByProduct(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        Integer userId = getSessionUserId(session);
        if (session == null || userId == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        String productIdParam = request.getParameter("product_id");
        
        if (productIdParam == null || productIdParam.isEmpty()) {
            sendError(response, "商品IDが必要です");
            return;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            int productId = Integer.parseInt(productIdParam);
            conn = DatabaseConnection.getConnection();
            
            // Find order that contains this product
            String sql = """
                SELECT DISTINCT o.order_id
                FROM orders o
                INNER JOIN order_items oi ON o.order_id = oi.order_id
                WHERE o.user_id = ? AND oi.product_id = ?
                ORDER BY o.created_at DESC
                LIMIT 1
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            stmt.setInt(2, productId);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                int orderId = rs.getInt("order_id");
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("order_id", orderId);
                sendJsonResponse(response, result);
            } else {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "注文が見つかりません");
                sendJsonResponse(response, result);
            }
            
        } catch (NumberFormatException e) {
            sendError(response, "無効な商品IDです");
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "データベースエラー: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== UPDATE ORDER STATUS ====================
    private void updateOrderStatus(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        Integer userId = getSessionUserId(session);
        if (session == null || userId == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        String orderIdStr = request.getParameter("order_id");
        String newStatus = request.getParameter("status");
        String trackingNumber = request.getParameter("tracking_number");
        String courier = request.getParameter("courier");
        
        if (orderIdStr == null || newStatus == null) {
            sendError(response, "注文IDとステータスが必要です");
            return;
        }
        
        int orderId;
        try {
            orderId = Integer.parseInt(orderIdStr);
        } catch (NumberFormatException e) {
            sendError(response, "無効な注文IDです");
            return;
        }
        
        // Validate and normalize status
        if (newStatus != null) newStatus = newStatus.trim().toLowerCase();
        String[] validStatuses = {"pending", "confirmed", "processing", "shipped", "delivered", "cancelled", "refunded"};
        boolean isValidStatus = false;
        for (String status : validStatuses) {
            if (status.equals(newStatus)) {
                isValidStatus = true;
                break;
            }
        }
        
        if (!isValidStatus) {
            sendError(response, "無効なステータスです: " + newStatus);
            return;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Verify order belongs to user (for buyer) or is seller's order (for seller)
            String checkSql = "SELECT o.order_id, o.user_id, o.order_status, o.payment_status " +
                            "FROM orders o WHERE o.order_id = ?";
            stmt = conn.prepareStatement(checkSql);
            stmt.setInt(1, orderId);
            rs = stmt.executeQuery();
            
            if (!rs.next()) {
                conn.rollback();
                sendError(response, "注文が見つかりません");
                return;
            }
            
            int orderUserId = rs.getInt("user_id");
            String currentStatus = rs.getString("order_status");
            if (currentStatus != null) currentStatus = currentStatus.trim().toLowerCase();
            String paymentStatus = rs.getString("payment_status");
            if (paymentStatus != null) paymentStatus = paymentStatus.trim().toLowerCase();
            
            // Check if user is buyer or seller
            boolean isBuyer = orderUserId == userId;
            boolean isSeller = false;
            
            // Check if user is seller of any item in this order
            String sellerCheckSql = "SELECT COUNT(*) FROM order_items WHERE order_id = ? AND seller_id = ?";
            PreparedStatement sellerStmt = conn.prepareStatement(sellerCheckSql);
            sellerStmt.setInt(1, orderId);
            sellerStmt.setInt(2, userId);
            ResultSet sellerRs = sellerStmt.executeQuery();
            if (sellerRs.next() && sellerRs.getInt(1) > 0) {
                isSeller = true;
            }
            sellerRs.close();
            sellerStmt.close();
            
            if (!isBuyer && !isSeller) {
                conn.rollback();
                String msg = "この注文を更新する権限がありません (userId=" + userId + ", orderBuyer=" + orderUserId + ")";
                System.out.println("[OrderServlet] updateOrderStatus: " + msg);
                sendError(response, "この注文を更新する権限がありません");
                return;
            }
            
            rs.close();
            stmt.close();
            
            // Validate status transition
            if (!isValidStatusTransition(currentStatus, newStatus, isSeller)) {
                conn.rollback();
                String msg = "無効なステータス遷移です: " + currentStatus + " → " + newStatus + " (isSeller=" + isSeller + ")";
                System.out.println("[OrderServlet] updateOrderStatus: " + msg);
                sendError(response, msg);
                return;
            }
            
            // Update order status
            StringBuilder updateSql = new StringBuilder("UPDATE orders SET order_status = ?, updated_at = NOW()");
            
            if ("shipped".equals(newStatus)) {
                updateSql.append(", shipped_at = NOW()");
                if (trackingNumber != null && !trackingNumber.trim().isEmpty()) {
                    updateSql.append(", tracking_number = ?");
                }
                if (courier != null && !courier.trim().isEmpty()) {
                    updateSql.append(", courier = ?");
                }
            } else if ("delivered".equals(newStatus)) {
                updateSql.append(", delivered_at = NOW()");
            } else if ("cancelled".equals(newStatus)) {
                updateSql.append(", cancelled_at = NOW()");
            }
            
            updateSql.append(" WHERE order_id = ?");
            
            stmt = conn.prepareStatement(updateSql.toString());
            int paramIndex = 1;
            stmt.setString(paramIndex++, newStatus);
            
            if ("shipped".equals(newStatus)) {
                if (trackingNumber != null && !trackingNumber.trim().isEmpty()) {
                    stmt.setString(paramIndex++, trackingNumber);
                }
                if (courier != null && !courier.trim().isEmpty()) {
                    stmt.setString(paramIndex++, courier);
                }
            }
            
            stmt.setInt(paramIndex, orderId);
            stmt.executeUpdate();
            stmt.close();
            
            // Update order items status
            String updateItemsSql = "UPDATE order_items SET status = ? WHERE order_id = ?";
            stmt = conn.prepareStatement(updateItemsSql);
            stmt.setString(1, newStatus);
            stmt.setInt(2, orderId);
            stmt.executeUpdate();
            stmt.close();
            
            // If status is confirmed and payment is paid, reduce stock
            if ("confirmed".equals(newStatus) && "paid".equals(paymentStatus)) {
                reduceStockForOrder(conn, orderId);
            }
            
            // If status is cancelled and payment is paid, restore stock
            if ("cancelled".equals(newStatus) && "paid".equals(paymentStatus)) {
                restoreStockForOrder(conn, orderId);
            }
            
            // Send notifications
            try {
                if ("confirmed".equals(newStatus) && isSeller) {
                    servlet.NotificationsServlet.createNotification(conn, orderUserId, "order",
                        "注文が確定しました",
                        "注文 #" + orderId + " が売り手により確定しました。",
                        "order-detail.jsp?id=" + orderId, "order", orderId);
                } else if ("shipped".equals(newStatus) && isSeller) {
                    servlet.NotificationsServlet.createNotification(conn, orderUserId, "order",
                        "商品が発送されました",
                        "注文 #" + orderId + " の商品が発送されました。" + (trackingNumber != null && !trackingNumber.isEmpty() ? "追跡番号: " + trackingNumber : ""),
                        "order-detail.jsp?id=" + orderId, "order", orderId);
                } else if ("delivered".equals(newStatus) && isSeller) {
                    servlet.NotificationsServlet.createNotification(conn, orderUserId, "order",
                        "お届けが完了しました",
                        "注文 #" + orderId + " の商品がお届けされました。",
                        "order-detail.jsp?id=" + orderId, "order", orderId);
                } else if ("cancelled".equals(newStatus)) {
                    servlet.NotificationsServlet.createNotification(conn, orderUserId, "order",
                        "注文がキャンセルされました",
                        "注文 #" + orderId + " がキャンセルされました。",
                        "order-detail.jsp?id=" + orderId, "order", orderId);
                    String sellerNotifySql = "SELECT DISTINCT seller_id FROM order_items WHERE order_id = ? AND seller_id != ?";
                    PreparedStatement sellerNotifyPs = conn.prepareStatement(sellerNotifySql);
                    sellerNotifyPs.setInt(1, orderId);
                    sellerNotifyPs.setInt(2, orderUserId);
                    ResultSet sellerNotifyRs = sellerNotifyPs.executeQuery();
                    while (sellerNotifyRs.next()) {
                        int sid = sellerNotifyRs.getInt("seller_id");
                        if (sid != userId) {
                            servlet.NotificationsServlet.createNotification(conn, sid, "order",
                                "注文がキャンセルされました",
                                "注文 #" + orderId + " がキャンセルされました。",
                                "order-detail.jsp?id=" + orderId, "order", orderId);
                        }
                    }
                    sellerNotifyRs.close();
                    sellerNotifyPs.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            conn.commit();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "注文ステータスを更新しました");
            result.put("order_id", orderId);
            result.put("new_status", newStatus);
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            e.printStackTrace();
            sendError(response, "ステータス更新エラー: " + e.getMessage());
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== REDUCE STOCK FOR ORDER ====================
    private void reduceStockForOrder(Connection conn, int orderId) throws SQLException {
        String sql = "SELECT product_id, quantity FROM order_items WHERE order_id = ?";
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setInt(1, orderId);
        ResultSet rs = stmt.executeQuery();
        
        String updateStockSql = "UPDATE products SET stock_quantity = stock_quantity - ? WHERE product_id = ?";
        PreparedStatement updateStmt = conn.prepareStatement(updateStockSql);
        
        while (rs.next()) {
            int productId = rs.getInt("product_id");
            int quantity = rs.getInt("quantity");
            
            updateStmt.setInt(1, quantity);
            updateStmt.setInt(2, productId);
            updateStmt.executeUpdate();
        }
        
        rs.close();
        stmt.close();
        updateStmt.close();
    }
    
    // ==================== RESTORE STOCK FOR ORDER ====================
    private void restoreStockForOrder(Connection conn, int orderId) throws SQLException {
        String sql = "SELECT product_id, quantity FROM order_items WHERE order_id = ?";
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setInt(1, orderId);
        ResultSet rs = stmt.executeQuery();
        
        String updateStockSql = "UPDATE products SET stock_quantity = stock_quantity + ? WHERE product_id = ?";
        PreparedStatement updateStmt = conn.prepareStatement(updateStockSql);
        
        while (rs.next()) {
            int productId = rs.getInt("product_id");
            int quantity = rs.getInt("quantity");
            
            updateStmt.setInt(1, quantity);
            updateStmt.setInt(2, productId);
            updateStmt.executeUpdate();
        }
        
        rs.close();
        stmt.close();
        updateStmt.close();
    }
    
    // ==================== VALIDATE STATUS TRANSITION ====================
    private boolean isValidStatusTransition(String currentStatus, String newStatus, boolean isSeller) {
        // Buyers can only cancel pending orders
        if (!isSeller && !"cancelled".equals(newStatus) && !"pending".equals(currentStatus)) {
            return false;
        }
        
        // Sellers can update status in sequence
        if (isSeller) {
            switch (currentStatus != null ? currentStatus : "") {
                case "pending":
                    return "confirmed".equals(newStatus) || "cancelled".equals(newStatus);
                case "confirmed":
                    return "processing".equals(newStatus) || "cancelled".equals(newStatus);
                case "processing":
                    return "shipped".equals(newStatus) || "cancelled".equals(newStatus);
                case "shipped":
                    return "delivered".equals(newStatus);
                case "delivered":
                    return false; // Cannot change delivered status
                case "cancelled":
                    return false; // Cannot change cancelled status
                default:
                    return false;
            }
        }
        
        // Buyers can cancel pending orders
        if (!isSeller && "pending".equals(currentStatus) && "cancelled".equals(newStatus)) {
            return true;
        }
        
        return false;
    }
    
    // ==================== UPDATE SHIPPING INFO ====================
    private void updateShippingInfo(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        Integer userId = getSessionUserId(session);
        if (session == null || userId == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        String orderIdStr = request.getParameter("order_id");
        String trackingNumber = request.getParameter("tracking_number");
        String courier = request.getParameter("courier");
        
        if (orderIdStr == null) {
            sendError(response, "注文IDが必要です");
            return;
        }
        
        int orderId;
        try {
            orderId = Integer.parseInt(orderIdStr);
        } catch (NumberFormatException e) {
            sendError(response, "無効な注文IDです");
            return;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            // Verify user is seller
            String checkSql = "SELECT COUNT(*) FROM order_items oi " +
                            "JOIN orders o ON oi.order_id = o.order_id " +
                            "WHERE o.order_id = ? AND oi.seller_id = ?";
            stmt = conn.prepareStatement(checkSql);
            stmt.setInt(1, orderId);
            stmt.setInt(2, userId);
            ResultSet rs = stmt.executeQuery();
            
            if (!rs.next() || rs.getInt(1) == 0) {
                rs.close();
                stmt.close();
                sendError(response, "この注文を更新する権限がありません");
                return;
            }
            
            rs.close();
            stmt.close();
            
            // Update shipping info
            String updateSql = "UPDATE orders SET tracking_number = ?, courier = ?, updated_at = NOW() " +
                             "WHERE order_id = ?";
            stmt = conn.prepareStatement(updateSql);
            stmt.setString(1, trackingNumber);
            stmt.setString(2, courier);
            stmt.setInt(3, orderId);
            stmt.executeUpdate();
            stmt.close();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "配送情報を更新しました");
            result.put("order_id", orderId);
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "配送情報更新エラー: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(null, stmt, conn);
        }
    }
    
    // ==================== CONFIRM ORDER ====================
    private void confirmOrder(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        Integer userId = getSessionUserId(session);
        if (session == null || userId == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        
        String orderIdStr = request.getParameter("order_id");
        
        if (orderIdStr == null) {
            sendError(response, "注文IDが必要です");
            return;
        }
        
        int orderId;
        try {
            orderId = Integer.parseInt(orderIdStr);
        } catch (NumberFormatException e) {
            sendError(response, "無効な注文IDです");
            return;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Verify order belongs to seller and payment is paid
            String checkSql = "SELECT o.order_id, o.user_id, o.payment_status, o.order_status " +
                            "FROM orders o " +
                            "JOIN order_items oi ON o.order_id = oi.order_id " +
                            "WHERE o.order_id = ? AND oi.seller_id = ? " +
                            "GROUP BY o.order_id, o.user_id, o.payment_status, o.order_status";
            stmt = conn.prepareStatement(checkSql);
            stmt.setInt(1, orderId);
            stmt.setInt(2, userId);
            ResultSet rs = stmt.executeQuery();
            
            if (!rs.next()) {
                conn.rollback();
                sendError(response, "注文が見つかりません");
                return;
            }
            
            int buyerId = rs.getInt("user_id");
            String paymentStatus = rs.getString("payment_status");
            String orderStatus = rs.getString("order_status");
            
            if (!"paid".equals(paymentStatus)) {
                conn.rollback();
                sendError(response, "支払いが完了していない注文は確定できません");
                return;
            }
            
            if (!"pending".equals(orderStatus)) {
                conn.rollback();
                sendError(response, "この注文は既に確定済みです");
                return;
            }
            
            rs.close();
            stmt.close();
            
            // Update order status to confirmed
            String updateSql = "UPDATE orders SET order_status = 'confirmed', updated_at = NOW() WHERE order_id = ?";
            stmt = conn.prepareStatement(updateSql);
            stmt.setInt(1, orderId);
            stmt.executeUpdate();
            stmt.close();
            
            // Update order items status
            String updateItemsSql = "UPDATE order_items SET status = 'confirmed' WHERE order_id = ?";
            stmt = conn.prepareStatement(updateItemsSql);
            stmt.setInt(1, orderId);
            stmt.executeUpdate();
            stmt.close();
            
            // Reduce stock
            reduceStockForOrder(conn, orderId);
            
            // Notify buyer that seller has confirmed the order
            try {
                servlet.NotificationsServlet.createNotification(conn, buyerId, "order",
                    "注文が確定しました",
                    "注文 #" + orderId + " が売り手により確定しました。",
                    "order-detail.jsp?id=" + orderId, "order", orderId);
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            conn.commit();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "注文を確定しました");
            result.put("order_id", orderId);
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            e.printStackTrace();
            sendError(response, "注文確定エラー: " + e.getMessage());
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            DatabaseConnection.closeResources(null, stmt, conn);
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
}