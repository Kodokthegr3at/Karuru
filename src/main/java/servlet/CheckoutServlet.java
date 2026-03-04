package servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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

@WebServlet("/CheckoutServlet")
public class CheckoutServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        configureUTF8ForJSON(request, response);
        response.setHeader("Access-Control-Allow-Origin", "*");
        
        String action = request.getParameter("action");
        
        try {
            if ("getCheckoutData".equals(action)) {
                getCheckoutData(request, response);
            } else if ("getUserAddresses".equals(action)) {
                getUserAddresses(request, response);
            } else {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "無効なアクションです");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "システムエラーが発生しました: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        configureUTF8ForJSON(request, response);
        response.setHeader("Access-Control-Allow-Origin", "*");
        
        String action = request.getParameter("action");
        
        try {
            if ("placeOrder".equals(action)) {
                placeOrder(request, response);
            } else if ("saveAddress".equals(action)) {
                saveAddress(request, response);
            } else {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "無効なアクションです");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "システムエラーが発生しました: " + e.getMessage());
        }
    }

    /** Get user ID from session, trying user_id and userId. */
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

    /**
     * Get checkout data (cart items and summary)
     */
    private void getCheckoutData(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        HttpSession session = request.getSession(false);
        Integer userId = getSessionUserId(session);
        if (session == null || userId == null) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "ログインが必要です");
            return;
        }
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = "SELECT c.cart_id, c.product_id, c.quantity, c.price_snapshot, " +
                        "p.product_name, p.image_url, p.status, p.stock_quantity, p.user_id as seller_id, " +
                        "p.original_price, p.discount_percentage, " +
                        "u.username as seller_name, u.full_name as seller_full_name " +
                        "FROM carts c " +
                        "JOIN products p ON c.product_id = p.product_id " +
                        "JOIN users u ON p.user_id = u.user_id " +
                        "WHERE c.user_id = ? AND (p.status = 'available' OR (p.status = 'reserved' AND EXISTS (" +
                        "SELECT 1 FROM offers o WHERE o.product_id = p.product_id AND o.buyer_id = c.user_id AND o.status = 'accepted'))) " +
                        "ORDER BY c.added_at DESC";
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            
            List<Map<String, Object>> items = new ArrayList<>();
            double subtotal = 0;
            boolean hasStockIssue = false;
            List<String> stockIssues = new ArrayList<>();
            
            while (rs.next()) {
                int stockQuantity = rs.getInt("stock_quantity");
                int quantity = rs.getInt("quantity");
                double price = rs.getDouble("price_snapshot");
                String productName = rs.getString("product_name");
                
                // Check stock availability
                if (stockQuantity < quantity) {
                    hasStockIssue = true;
                    stockIssues.add(productName + " (在庫: " + stockQuantity + "個, 注文数: " + quantity + "個)");
                    continue;
                }
                
                Map<String, Object> item = new HashMap<>();
                item.put("cart_id", rs.getInt("cart_id"));
                item.put("product_id", rs.getInt("product_id"));
                item.put("product_name", productName);
                item.put("image_url", rs.getString("image_url"));
                item.put("quantity", quantity);
                item.put("price", price);
                item.put("seller_id", rs.getInt("seller_id"));
                item.put("seller_name", rs.getString("seller_name"));
                item.put("seller_full_name", rs.getString("seller_full_name"));
                item.put("stock_quantity", stockQuantity);
                item.put("subtotal", price * quantity);
                
                // Add discount information
                double originalPrice = rs.getDouble("original_price");
                if (!rs.wasNull() && originalPrice > 0) {
                    item.put("original_price", originalPrice);
                }
                int discountPercentage = rs.getInt("discount_percentage");
                if (!rs.wasNull() && discountPercentage > 0) {
                    item.put("discount_percentage", discountPercentage);
                }
                
                items.add(item);
                subtotal += price * quantity;
            }
            
            if (items.isEmpty()) {
                if (hasStockIssue) {
                    sendError(response, HttpServletResponse.SC_BAD_REQUEST, 
                             "在庫不足の商品があります: " + String.join(", ", stockIssues));
                } else {
                    sendError(response, HttpServletResponse.SC_BAD_REQUEST, "カートが空です");
                }
                return;
            }
            
            // Calculate discount from items
            double discount = 0.0;
            for (Map<String, Object> item : items) {
                Object originalPriceObj = item.get("original_price");
                Object priceObj = item.get("price");
                Object quantityObj = item.get("quantity");
                
                if (originalPriceObj != null && priceObj != null && quantityObj != null) {
                    try {
                        double originalPrice = ((Number) originalPriceObj).doubleValue();
                        double price = ((Number) priceObj).doubleValue();
                        int quantity = ((Number) quantityObj).intValue();
                        
                        if (originalPrice > price) {
                            double itemDiscount = (originalPrice - price) * quantity;
                            discount += itemDiscount;
                        }
                    } catch (Exception e) {
                        // Skip if conversion fails
                    }
                }
            }
            
            // Calculate fees
            double shipping = calculateShippingFee(items);
            double fee = Math.round(subtotal * 0.03);
            double total = subtotal + shipping + fee - discount;
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("items", items);
            result.put("subtotal", subtotal);
            result.put("shipping", shipping);
            result.put("discount", discount);
            result.put("fee", fee);
            result.put("total", total);
            
            if (hasStockIssue) {
                result.put("warning", "一部の商品が在庫不足のため注文から除外されました: " + String.join(", ", stockIssues));
            }
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "データベースエラーが発生しました");
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }

    /**
     * Calculate shipping fee based on items
     */
    private double calculateShippingFee(List<Map<String, Object>> items) {
        double baseFee = 500.0;
        
        int sellerCount = (int) items.stream()
                .map(item -> item.get("seller_id"))
                .distinct()
                .count();
        
        if (sellerCount > 1) {
            baseFee += 200 * (sellerCount - 1);
        }
        
        return baseFee;
    }

    /**
     * Get user's saved addresses
     */
    private void getUserAddresses(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        HttpSession session = request.getSession(false);
        Integer userId = getSessionUserId(session);
        if (session == null || userId == null) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "ログインが必要です");
            return;
        }
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            String sql = "SELECT address_id, " +
                        "COALESCE(address_label, '自宅') as address_label, " +
                        "recipient_name as full_name, " +
                        "postal_code, prefecture, city, " +
                        "address_line1 as address_line, " +
                        "building_name as building, " +
                        "phone, is_default " +
                        "FROM user_addresses " +
                        "WHERE user_id = ? " +
                        "ORDER BY is_default DESC, created_at DESC";
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            
            List<Map<String, Object>> addresses = new ArrayList<>();
            
            while (rs.next()) {
                Map<String, Object> address = new HashMap<>();
                address.put("address_id", rs.getInt("address_id"));
                address.put("address_label", rs.getString("address_label"));
                address.put("full_name", rs.getString("full_name"));
                address.put("postal_code", rs.getString("postal_code"));
                address.put("prefecture", rs.getString("prefecture"));
                address.put("city", rs.getString("city"));
                address.put("address_line", rs.getString("address_line"));
                address.put("building", rs.getString("building"));
                address.put("phone", rs.getString("phone"));
                address.put("is_default", rs.getBoolean("is_default"));
                
                addresses.add(address);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("addresses", addresses);
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "データベースエラーが発生しました: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }

    /**
     * Save new address
     */
    private void saveAddress(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        HttpSession session = request.getSession(false);
        Integer userId = getSessionUserId(session);
        if (session == null || userId == null) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "ログインが必要です");
            return;
        }
        
        String addressLabel = request.getParameter("address_label");
        String fullName = request.getParameter("full_name");
        String postalCode = request.getParameter("postal_code");
        String prefecture = request.getParameter("prefecture");
        String city = request.getParameter("city");
        String addressLine1 = request.getParameter("address_line");
        String addressLine2 = request.getParameter("address_line2");
        String building = request.getParameter("building");
        String phone = request.getParameter("phone");
        boolean isDefault = "true".equals(request.getParameter("is_default"));
        
        // Validation
        if (fullName == null || fullName.trim().isEmpty() ||
            postalCode == null || postalCode.trim().isEmpty() ||
            prefecture == null || prefecture.trim().isEmpty() ||
            city == null || city.trim().isEmpty() ||
            addressLine1 == null || addressLine1.trim().isEmpty() ||
            phone == null || phone.trim().isEmpty()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "必須項目が入力されていません");
            return;
        }
        
        // Set default label if not provided
        if (addressLabel == null || addressLabel.trim().isEmpty()) {
            addressLabel = "自宅";
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // If this is default, unset other defaults
            if (isDefault) {
                String updateSql = "UPDATE user_addresses SET is_default = 0 WHERE user_id = ?";
                stmt = conn.prepareStatement(updateSql);
                stmt.setInt(1, userId);
                stmt.executeUpdate();
                stmt.close();
            }
            
            // Insert new address
            String insertSql = "INSERT INTO user_addresses (user_id, address_label, recipient_name, postal_code, prefecture, " +
                              "city, address_line1, address_line2, building_name, phone, is_default, country) " +
                              "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '日本')";
            
            stmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
            stmt.setInt(1, userId);
            stmt.setString(2, addressLabel);
            stmt.setString(3, fullName.trim());
            stmt.setString(4, postalCode.trim());
            stmt.setString(5, prefecture.trim());
            stmt.setString(6, city.trim());
            stmt.setString(7, addressLine1.trim());
            stmt.setString(8, addressLine2 != null ? addressLine2.trim() : null);
            stmt.setString(9, building != null ? building.trim() : null);
            stmt.setString(10, phone.trim());
            stmt.setBoolean(11, isDefault);
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows == 0) {
                throw new SQLException("住所の保存に失敗しました");
            }
            
            ResultSet rs = stmt.getGeneratedKeys();
            int addressId = 0;
            if (rs.next()) {
                addressId = rs.getInt(1);
            }
            rs.close();
            
            conn.commit();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("address_id", addressId);
            result.put("message", "住所を保存しました");
            
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
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "住所の保存に失敗しました: " + e.getMessage());
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

    /**
     * Place order
     */
    private void placeOrder(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        HttpSession session = request.getSession(false);
        Integer userId = getSessionUserId(session);
        if (session == null || userId == null) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "ログインが必要です");
            return;
        }
        String addressIdStr = request.getParameter("address_id");
        String paymentMethod = request.getParameter("payment_method");
        
        if (addressIdStr == null || paymentMethod == null) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "配送先住所と支払い方法を選択してください");
            return;
        }
        
        int addressId;
        try {
            addressId = Integer.parseInt(addressIdStr);
        } catch (NumberFormatException e) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "無効な住所IDです");
            return;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Verify address belongs to user
            String addressCheckSql = "SELECT address_id FROM user_addresses WHERE address_id = ? AND user_id = ?";
            stmt = conn.prepareStatement(addressCheckSql);
            stmt.setInt(1, addressId);
            stmt.setInt(2, userId);
            rs = stmt.executeQuery();
            
            if (!rs.next()) {
                conn.rollback();
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "無効な配送先住所です");
                return;
            }
            rs.close();
            stmt.close();
            
            // Get cart items
            String cartSql = "SELECT c.cart_id, c.product_id, c.quantity, c.price_snapshot, " +
                           "p.stock_quantity, p.user_id as seller_id, p.product_name " +
                           "FROM carts c " +
                           "JOIN products p ON c.product_id = p.product_id " +
                           "WHERE c.user_id = ? AND (p.status = 'available' OR (p.status = 'reserved' AND EXISTS (" +
                           "SELECT 1 FROM offers o WHERE o.product_id = p.product_id AND o.buyer_id = c.user_id AND o.status = 'accepted')))";
            
            stmt = conn.prepareStatement(cartSql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            
            List<Map<String, Object>> cartItems = new ArrayList<>();
            double subtotal = 0;
            List<String> stockIssues = new ArrayList<>();
            
            while (rs.next()) {
                int productId = rs.getInt("product_id");
                int quantity = rs.getInt("quantity");
                double price = rs.getDouble("price_snapshot");
                int stock = rs.getInt("stock_quantity");
                int sellerId = rs.getInt("seller_id");
                String productName = rs.getString("product_name");
                
                // Check stock
                if (stock < quantity) {
                    stockIssues.add(productName + " (在庫: " + stock + "個, 注文数: " + quantity + "個)");
                    continue;
                }
                
                Map<String, Object> item = new HashMap<>();
                item.put("cart_id", rs.getInt("cart_id"));
                item.put("product_id", productId);
                item.put("product_name", productName);
                item.put("quantity", quantity);
                item.put("price", price);
                item.put("seller_id", sellerId);
                item.put("stock_quantity", stock);
                
                cartItems.add(item);
                subtotal += price * quantity;
            }
            
            if (cartItems.isEmpty()) {
                conn.rollback();
                if (!stockIssues.isEmpty()) {
                    sendError(response, HttpServletResponse.SC_BAD_REQUEST, 
                             "在庫不足の商品があります: " + String.join(", ", stockIssues));
                } else {
                    sendError(response, HttpServletResponse.SC_BAD_REQUEST, "カートが空です");
                }
                return;
            }
            
            rs.close();
            stmt.close();
            
            // Calculate fees
            double shipping = calculateShippingFee(cartItems);
            double taxAmount = Math.round(subtotal * 0.03 * 100.0) / 100.0; // 3% fee, rounded to 2 decimals
            double discountAmount = 0.00; // No discount by default
            double totalAmount = subtotal + shipping + taxAmount - discountAmount;
            
            // Generate order number
            String orderNumber = "ORD-" + System.currentTimeMillis();
            
            // Create order - sesuai dengan schema database orders table
            String orderSql = "INSERT INTO orders (user_id, order_number, subtotal, shipping_cost, " +
                            "discount_amount, tax_amount, total_amount, payment_method, payment_status, " +
                            "order_status, shipping_address_id, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'pending', 'pending', ?, NOW(), NOW())";
            
            stmt = conn.prepareStatement(orderSql, Statement.RETURN_GENERATED_KEYS);
            stmt.setInt(1, userId);
            stmt.setString(2, orderNumber);
            stmt.setDouble(3, subtotal);
            stmt.setDouble(4, shipping);
            stmt.setDouble(5, discountAmount);
            stmt.setDouble(6, taxAmount);
            stmt.setDouble(7, totalAmount);
            stmt.setString(8, paymentMethod);
            stmt.setInt(9, addressId);
            
            stmt.executeUpdate();
            
            rs = stmt.getGeneratedKeys();
            int orderId = 0;
            if (rs.next()) {
                orderId = rs.getInt(1);
            }
            rs.close();
            stmt.close();
            
            // Create order items
            // Note: Stock will be reduced when order is confirmed (after payment)
            String itemSql = "INSERT INTO order_items (order_id, product_id, seller_id, product_name, " +
                           "quantity, price, subtotal, status) VALUES (?, ?, ?, ?, ?, ?, ?, 'pending')";
            stmt = conn.prepareStatement(itemSql);
            
            for (Map<String, Object> item : cartItems) {
                int quantity = (Integer) item.get("quantity");
                double price = (Double) item.get("price");
                double itemSubtotal = price * quantity;
                
                stmt.setInt(1, orderId);
                stmt.setInt(2, (Integer) item.get("product_id"));
                stmt.setInt(3, (Integer) item.get("seller_id"));
                stmt.setString(4, (String) item.get("product_name"));
                stmt.setInt(5, quantity);
                stmt.setDouble(6, price);
                stmt.setDouble(7, itemSubtotal);
                stmt.executeUpdate();
            }
            
            stmt.close();
            
            // Notify each seller about new order
            java.util.Set<Integer> notifiedSellers = new java.util.HashSet<>();
            for (Map<String, Object> item : cartItems) {
                int sellerId = (Integer) item.get("seller_id");
                if (sellerId != userId && notifiedSellers.add(sellerId)) {
                    try {
                        servlet.NotificationsServlet.createNotification(conn, sellerId, "order",
                            "新しい注文がありました",
                            "注文 #" + orderNumber + " が届きました。支払い待ちです。",
                            "order-detail.jsp?id=" + orderId, "order", orderId);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            
            // Clear cart
            String clearCartSql = "DELETE FROM carts WHERE user_id = ?";
            stmt = conn.prepareStatement(clearCartSql);
            stmt.setInt(1, userId);
            stmt.executeUpdate();
            
            conn.commit();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("order_id", orderId);
            result.put("order_number", orderNumber);
            result.put("message", "注文が完了しました");
            
            if (!stockIssues.isEmpty()) {
                result.put("warning", "一部の商品が在庫不足のため注文から除外されました: " + String.join(", ", stockIssues));
            }
            
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
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "注文処理に失敗しました: " + e.getMessage());
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

    private void sendJsonResponse(HttpServletResponse response, Object data) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        out.print(gson.toJson(data));
        out.flush();
    }

    private void sendError(HttpServletResponse response, int status, String message) 
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        error.put("status", status);
        PrintWriter out = response.getWriter();
        out.print(gson.toJson(error));
        out.flush();
    }

    private void configureUTF8ForJSON(HttpServletRequest request, HttpServletResponse response) {
        try {
            request.setCharacterEncoding("UTF-8");
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}