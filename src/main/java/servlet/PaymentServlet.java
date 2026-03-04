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

@WebServlet({"/Payment", "/PaymentServlet"})
public class PaymentServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        FilterEncodingUTF8.configureUTF8ForJSON(request, response);
        
        String action = request.getParameter("action");
        
        try {
            if ("processPayment".equals(action)) {
                processPayment(request, response);
            } else if ("confirmPayment".equals(action)) {
                confirmPayment(request, response);
            } else {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "無効なアクションです");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "システムエラーが発生しました: " + e.getMessage());
        }
    }

    /**
     * Process payment for an order - Complete flow with all payment methods
     */
    private void processPayment(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        HttpSession session = request.getSession(false);
        
        if (session == null || session.getAttribute("user_id") == null) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "ログインが必要です");
            return;
        }
        
        Integer userId = (Integer) session.getAttribute("user_id");
        String orderIdStr = request.getParameter("order_id");
        String paymentMethod = request.getParameter("payment_method");
        String paymentDetails = request.getParameter("payment_details");
        
        if (orderIdStr == null || paymentMethod == null) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "注文IDと支払い方法が必要です");
            return;
        }
        
        int orderId;
        try {
            orderId = Integer.parseInt(orderIdStr);
        } catch (NumberFormatException e) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "無効な注文IDです");
            return;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Get order details
            String orderSql = "SELECT order_id, user_id, total_amount, payment_method, payment_status, order_status " +
                            "FROM orders WHERE order_id = ? AND user_id = ?";
            stmt = conn.prepareStatement(orderSql);
            stmt.setInt(1, orderId);
            stmt.setInt(2, userId);
            rs = stmt.executeQuery();
            
            if (!rs.next()) {
                conn.rollback();
                sendError(response, HttpServletResponse.SC_NOT_FOUND, "注文が見つかりません");
                return;
            }
            
            double totalAmount = rs.getDouble("total_amount");
            String currentPaymentStatus = rs.getString("payment_status");
            String currentOrderStatus = rs.getString("order_status");
            
            // Check if already paid
            if ("paid".equals(currentPaymentStatus)) {
                conn.rollback();
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "この注文は既に支払い済みです");
                return;
            }
            
            rs.close();
            stmt.close();
            
            // Process payment based on method
            boolean paymentSuccess = false;
            String transactionId = null;
            
            switch (paymentMethod) {
                case "wallet":
                    String walletError = processWalletPayment(conn, userId, orderId, totalAmount);
                    if (walletError != null) {
                        conn.rollback();
                        sendError(response, HttpServletResponse.SC_BAD_REQUEST, walletError);
                        return;
                    }
                    paymentSuccess = true;
                    break;
                case "credit_card":
                case "ewallet":
                    // Simulate payment gateway - in production, integrate with real payment gateway
                    paymentSuccess = simulatePaymentGateway(paymentMethod, totalAmount, paymentDetails);
                    if (paymentSuccess) {
                        transactionId = "TXN-" + System.currentTimeMillis();
                    }
                    break;
                case "bank_transfer":
                    // Bank transfer - payment is pending until confirmed manually
                    paymentSuccess = true; // Will be marked as pending
                    break;
                case "cod":
                    // Cash on delivery - payment will be collected on delivery
                    paymentSuccess = true; // Will be marked as pending
                    break;
                default:
                    conn.rollback();
                    sendError(response, HttpServletResponse.SC_BAD_REQUEST, "無効な支払い方法です");
                    return;
            }
            
            if (!paymentSuccess) {
                conn.rollback();
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "支払い処理に失敗しました");
                return;
            }
            
            // Update order payment status
            String paymentStatus = "paid";
            if ("bank_transfer".equals(paymentMethod) || "cod".equals(paymentMethod)) {
                paymentStatus = "pending"; // Will be confirmed later
            }
            
            String updateOrderSql = "UPDATE orders SET payment_status = ?, payment_method = ?, paid_at = NOW(), " +
                                  "order_status = CASE WHEN ? = 'paid' THEN 'confirmed' ELSE order_status END, " +
                                  "updated_at = NOW() WHERE order_id = ?";
            stmt = conn.prepareStatement(updateOrderSql);
            stmt.setString(1, paymentStatus);
            stmt.setString(2, paymentMethod);
            stmt.setString(3, paymentStatus);
            stmt.setInt(4, orderId);
            stmt.executeUpdate();
            stmt.close();
            
            // If payment is paid, update order items status to confirmed
            if ("paid".equals(paymentStatus)) {
                String updateItemsSql = "UPDATE order_items SET status = 'confirmed' WHERE order_id = ?";
                stmt = conn.prepareStatement(updateItemsSql);
                stmt.setInt(1, orderId);
                stmt.executeUpdate();
                stmt.close();
            }
            
            // Record wallet transaction if wallet payment
            if ("wallet".equals(paymentMethod) && paymentSuccess) {
                recordWalletTransaction(conn, userId, orderId, totalAmount, "purchase");
            }
            
            // Notify seller(s) when payment is completed
            if ("paid".equals(paymentStatus)) {
                try {
                    String sellerSql = "SELECT DISTINCT seller_id FROM order_items WHERE order_id = ?";
                    PreparedStatement sellerStmt = conn.prepareStatement(sellerSql);
                    sellerStmt.setInt(1, orderId);
                    ResultSet sellerRs = sellerStmt.executeQuery();
                    java.util.Set<Integer> notifiedSellers = new java.util.HashSet<>();
                    while (sellerRs.next()) {
                        int sellerId = sellerRs.getInt("seller_id");
                        if (sellerId != userId && notifiedSellers.add(sellerId)) {
                            servlet.NotificationsServlet.createNotification(conn, sellerId, "order",
                                "支払いが完了しました",
                                "注文 #" + orderId + " の支払いが完了しました。発送の準備をしてください。",
                                "order-detail.jsp?id=" + orderId, "order", orderId);
                        }
                    }
                    sellerRs.close();
                    sellerStmt.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            
            conn.commit();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "paid".equals(paymentStatus) ? "お支払いが完了しました" : "支払い処理が完了しました（確認待ち）");
            result.put("order_id", orderId);
            result.put("payment_status", paymentStatus);
            result.put("order_status", "paid".equals(paymentStatus) ? "confirmed" : currentOrderStatus);
            if (transactionId != null) {
                result.put("transaction_id", transactionId);
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
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "支払い処理エラー: " + e.getMessage());
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

    /**
     * Process wallet payment
     * @return null if success, error message if failed
     */
    private String processWalletPayment(Connection conn, int userId, int orderId, double amount) 
            throws SQLException {
        // Get or create wallet
        String walletSql = "SELECT wallet_id, balance FROM user_wallets WHERE user_id = ? FOR UPDATE";
        PreparedStatement stmt = conn.prepareStatement(walletSql);
        stmt.setInt(1, userId);
        ResultSet rs = stmt.executeQuery();
        
        int walletId;
        double currentBalance;
        
        if (rs.next()) {
            walletId = rs.getInt("wallet_id");
            currentBalance = rs.getDouble("balance");
        } else {
            // Create wallet if doesn't exist (use full schema like RegisterServlet/WalletServlet)
            rs.close();
            stmt.close();
            String createWalletSql = "INSERT INTO user_wallets (user_id, balance, frozen_balance, " +
                    "total_earned, total_spent, created_at, updated_at) " +
                    "VALUES (?, 0.00, 0.00, 0.00, 0.00, NOW(), NOW())";
            stmt = conn.prepareStatement(createWalletSql, PreparedStatement.RETURN_GENERATED_KEYS);
            stmt.setInt(1, userId);
            stmt.executeUpdate();
            rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                walletId = rs.getInt(1);
            } else {
                rs.close();
                stmt.close();
                return "ウォレットの作成に失敗しました";
            }
            currentBalance = 0;
        }
        rs.close();
        stmt.close();
        
        // Check balance
        if (currentBalance < amount) {
            return String.format("ウォレットの残高が不足しています。残高: ¥%,.0f、必要: ¥%,.0f。ウォレットにチャージしてください。",
                    currentBalance, amount);
        }
        
        // Deduct from wallet (COALESCE for total_spent in case column is NULL)
        double newBalance = currentBalance - amount;
        String updateWalletSql = "UPDATE user_wallets SET balance = ?, total_spent = COALESCE(total_spent, 0) + ?, " +
                                "last_transaction_at = NOW(), updated_at = NOW() WHERE wallet_id = ?";
        stmt = conn.prepareStatement(updateWalletSql);
        stmt.setDouble(1, newBalance);
        stmt.setDouble(2, amount);
        stmt.setInt(3, walletId);
        stmt.executeUpdate();
        stmt.close();
        
        return null; // success
    }

    /**
     * Record wallet transaction
     */
    private void recordWalletTransaction(Connection conn, int userId, int orderId, double amount, String type) 
            throws SQLException {
        // Get wallet_id
        String walletSql = "SELECT wallet_id, balance FROM user_wallets WHERE user_id = ?";
        PreparedStatement stmt = conn.prepareStatement(walletSql);
        stmt.setInt(1, userId);
        ResultSet rs = stmt.executeQuery();
        
        if (!rs.next()) {
            rs.close();
            stmt.close();
            return;
        }
        
        int walletId = rs.getInt("wallet_id");
        double balanceAfter = rs.getDouble("balance");
        double balanceBefore = balanceAfter + amount; // Since we already deducted
        
        rs.close();
        stmt.close();
        
        // Insert transaction record
        String transSql = "INSERT INTO wallet_transactions (wallet_id, type, amount, balance_before, " +
                         "balance_after, description, reference_type, reference_id, status) " +
                         "VALUES (?, ?, ?, ?, ?, ?, 'order', ?, 'completed')";
        stmt = conn.prepareStatement(transSql);
        stmt.setInt(1, walletId);
        stmt.setString(2, type);
        stmt.setDouble(3, amount);
        stmt.setDouble(4, balanceBefore);
        stmt.setDouble(5, balanceAfter);
        stmt.setString(6, "注文 #" + orderId + " の支払い");
        stmt.setInt(7, orderId);
        stmt.executeUpdate();
        stmt.close();
    }

    /**
     * Simulate payment gateway (for credit_card and ewallet)
     * In production, replace with actual payment gateway integration
     */
    private boolean simulatePaymentGateway(String paymentMethod, double amount, String paymentDetails) {
        // Simulate payment processing
        // In production, integrate with Stripe, PayPal, etc.
        try {
            Thread.sleep(100); // Simulate processing time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // For demo purposes, always return true
        // In production, check actual payment gateway response
        return true;
    }

    /**
     * Confirm payment (for bank_transfer and cod)
     */
    private void confirmPayment(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        HttpSession session = request.getSession(false);
        
        if (session == null || session.getAttribute("user_id") == null) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "ログインが必要です");
            return;
        }
        
        Integer userId = (Integer) session.getAttribute("user_id");
        String orderIdStr = request.getParameter("order_id");
        
        if (orderIdStr == null) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "注文IDが必要です");
            return;
        }
        
        int orderId;
        try {
            orderId = Integer.parseInt(orderIdStr);
        } catch (NumberFormatException e) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "無効な注文IDです");
            return;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Verify order belongs to user and payment is pending
            String checkSql = "SELECT order_id, payment_status, order_status FROM orders " +
                            "WHERE order_id = ? AND user_id = ?";
            stmt = conn.prepareStatement(checkSql);
            stmt.setInt(1, orderId);
            stmt.setInt(2, userId);
            ResultSet rs = stmt.executeQuery();
            
            if (!rs.next()) {
                conn.rollback();
                sendError(response, HttpServletResponse.SC_NOT_FOUND, "注文が見つかりません");
                return;
            }
            
            String paymentStatus = rs.getString("payment_status");
            if (!"pending".equals(paymentStatus)) {
                conn.rollback();
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "この注文は確認待ちではありません");
                return;
            }
            
            rs.close();
            stmt.close();
            
            // Update payment status to paid and order status to confirmed
            String updateSql = "UPDATE orders SET payment_status = 'paid', order_status = 'confirmed', " +
                             "paid_at = NOW(), updated_at = NOW() WHERE order_id = ?";
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
            
            conn.commit();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "支払いが確認されました");
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
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "支払い確認エラー: " + e.getMessage());
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

    private void sendJsonResponse(HttpServletResponse response, Object data) throws IOException {
        response.setContentType("application/json; charset=UTF-8");
        PrintWriter out = response.getWriter();
        out.print(gson.toJson(data));
        out.flush();
    }

    private void sendError(HttpServletResponse response, int status, String message) 
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json; charset=UTF-8");
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        error.put("status", status);
        PrintWriter out = response.getWriter();
        out.print(gson.toJson(error));
        out.flush();
    }
}
