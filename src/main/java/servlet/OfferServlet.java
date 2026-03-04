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
import util.FilterEncodingUTF8;

@WebServlet({"/OfferServlet", "/Offer"})
public class OfferServlet extends HttpServlet {
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
                case "getOffers":
                    getOffers(request, response);
                    break;
                case "getMyOffers":
                    getMyOffers(request, response);
                    break;
                case "getProductOffers":
                    getProductOffers(request, response);
                    break;
                case "getOffer":
                    getOffer(request, response);
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
                case "create":
                    createOffer(request, response);
                    break;
                case "accept":
                    acceptOffer(request, response);
                    break;
                case "reject":
                    rejectOffer(request, response);
                    break;
                case "cancel":
                    cancelOffer(request, response);
                    break;
                case "counter":
                    counterOffer(request, response);
                    break;
                default:
                    sendError(response, "Invalid action: " + action);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage());
        }
    }
    
    // ==================== INITIALIZE OFFERS TABLE ====================
    private void initializeOffersTable(Connection conn) throws SQLException {
        // Check if table exists
        String checkTableSql = "SHOW TABLES LIKE 'offers'";
        PreparedStatement stmt = conn.prepareStatement(checkTableSql);
        ResultSet rs = stmt.executeQuery();
        boolean hasTable = rs.next();
        rs.close();
        stmt.close();
        
        if (!hasTable) {
            // Create offers table
            String createTableSql = "CREATE TABLE IF NOT EXISTS offers (" +
                "offer_id INT AUTO_INCREMENT PRIMARY KEY, " +
                "product_id INT NOT NULL, " +
                "buyer_id INT NOT NULL, " +
                "seller_id INT NOT NULL, " +
                "offer_price DECIMAL(10,2) NOT NULL, " +
                "message TEXT, " +
                "status VARCHAR(20) DEFAULT 'pending', " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE, " +
                "FOREIGN KEY (buyer_id) REFERENCES users(user_id) ON DELETE CASCADE, " +
                "FOREIGN KEY (seller_id) REFERENCES users(user_id) ON DELETE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
            
            stmt = conn.prepareStatement(createTableSql);
            stmt.execute();
            stmt.close();
            
            System.out.println("Offers table created successfully");
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
    
    // ==================== CREATE OFFER ====================
    private void createOffer(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        Integer buyerId = getSessionUserId(session);
        if (session == null || buyerId == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        String productIdStr = request.getParameter("product_id");
        String offerPriceStr = request.getParameter("offer_price");
        String message = request.getParameter("message");
        
        if (productIdStr == null || offerPriceStr == null) {
            sendError(response, "商品IDとオファー価格が必要です");
            return;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            initializeOffersTable(conn);
            
            int productId = Integer.parseInt(productIdStr);
            double offerPrice = Double.parseDouble(offerPriceStr);
            
            // Validate offer price is positive
            if (offerPrice <= 0) {
                sendError(response, "オファー価格は0より大きい必要があります");
                return;
            }
            
            // Get product info
            String productSql = "SELECT p.user_id as seller_id, p.price, p.status " +
                              "FROM products p WHERE p.product_id = ?";
            stmt = conn.prepareStatement(productSql);
            stmt.setInt(1, productId);
            rs = stmt.executeQuery();
            
            if (!rs.next()) {
                sendError(response, "商品が見つかりません");
                return;
            }
            
            int sellerId = rs.getInt("seller_id");
            double productPrice = rs.getDouble("price");
            String productStatus = rs.getString("status");
            
            rs.close();
            stmt.close();
            
            // Check if product is available (allow offers for all available products)
            if (!"available".equals(productStatus)) {
                sendError(response, "この商品は現在利用できません");
                return;
            }
            
            // Check if buyer is not the seller
            if (buyerId == sellerId) {
                sendError(response, "自分の商品にはオファーできません");
                return;
            }
            
            // Check if there's already a pending offer from this buyer
            String checkOfferSql = "SELECT offer_id FROM offers " +
                                 "WHERE product_id = ? AND buyer_id = ? AND status = 'pending'";
            stmt = conn.prepareStatement(checkOfferSql);
            stmt.setInt(1, productId);
            stmt.setInt(2, buyerId);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                sendError(response, "既にこの商品に保留中のオファーがあります");
                return;
            }
            
            rs.close();
            stmt.close();
            
            // Create offer
            String insertSql = "INSERT INTO offers (product_id, buyer_id, seller_id, offer_price, message, status) " +
                            "VALUES (?, ?, ?, ?, ?, 'pending')";
            stmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
            stmt.setInt(1, productId);
            stmt.setInt(2, buyerId);
            stmt.setInt(3, sellerId);
            stmt.setDouble(4, offerPrice);
            stmt.setString(5, message);
            
            int rowsInserted = stmt.executeUpdate();
            
            if (rowsInserted > 0) {
                rs = stmt.getGeneratedKeys();
                int offerId = 0;
                if (rs.next()) {
                    offerId = rs.getInt(1);
                }
                
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "オファーを送信しました");
                result.put("offer_id", offerId);
                result.put("seller_id", sellerId);
                result.put("product_id", productId);
                
                sendJsonResponse(response, result);
            } else {
                sendError(response, "オファーの作成に失敗しました");
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "データベースエラー: " + e.getMessage());
        } catch (NumberFormatException e) {
            sendError(response, "無効な数値形式です");
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "エラーが発生しました: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== GET OFFERS (for seller) ====================
    private void getOffers(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        Integer sellerId = getSessionUserId(session);
        if (session == null || sellerId == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        String productIdStr = request.getParameter("product_id");
        String status = request.getParameter("status");
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            initializeOffersTable(conn);
            
            StringBuilder sql = new StringBuilder("""
                SELECT o.offer_id, o.product_id, o.buyer_id, o.seller_id, 
                       o.offer_price, o.message, o.status, o.created_at, o.updated_at,
                       p.product_name, p.price as product_price, p.image_url,
                       u.username as buyer_username, u.full_name as buyer_name, 
                       u.avatar_url as buyer_avatar
                FROM offers o
                JOIN products p ON o.product_id = p.product_id
                JOIN users u ON o.buyer_id = u.user_id
                WHERE o.seller_id = ?
                """);
            
            List<Object> params = new ArrayList<>();
            params.add(sellerId);
            
            if (productIdStr != null && !productIdStr.isEmpty()) {
                sql.append(" AND o.product_id = ?");
                params.add(Integer.parseInt(productIdStr));
            }
            
            if (status != null && !status.isEmpty()) {
                sql.append(" AND o.status = ?");
                params.add(status);
            }
            
            sql.append(" ORDER BY o.created_at DESC");
            
            stmt = conn.prepareStatement(sql.toString());
            for (int i = 0; i < params.size(); i++) {
                if (params.get(i) instanceof Integer) {
                    stmt.setInt(i + 1, (Integer) params.get(i));
                } else {
                    stmt.setString(i + 1, (String) params.get(i));
                }
            }
            
            rs = stmt.executeQuery();
            
            List<Map<String, Object>> offers = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> offer = new HashMap<>();
                offer.put("offer_id", rs.getInt("offer_id"));
                offer.put("product_id", rs.getInt("product_id"));
                offer.put("buyer_id", rs.getInt("buyer_id"));
                offer.put("seller_id", rs.getInt("seller_id"));
                offer.put("offer_price", rs.getDouble("offer_price"));
                offer.put("message", rs.getString("message"));
                offer.put("status", rs.getString("status"));
                offer.put("created_at", rs.getTimestamp("created_at").toString());
                offer.put("updated_at", rs.getTimestamp("updated_at").toString());
                offer.put("product_name", rs.getString("product_name"));
                offer.put("product_price", rs.getDouble("product_price"));
                offer.put("image_url", rs.getString("image_url"));
                offer.put("buyer_username", rs.getString("buyer_username"));
                offer.put("buyer_name", rs.getString("buyer_name"));
                offer.put("buyer_avatar", rs.getString("buyer_avatar"));
                
                offers.add(offer);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("offers", offers);
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "データベースエラー: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "エラーが発生しました: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== GET MY OFFERS (for buyer) ====================
    private void getMyOffers(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        Integer buyerId = getSessionUserId(session);
        if (session == null || buyerId == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        String status = request.getParameter("status");
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            initializeOffersTable(conn);
            
            StringBuilder sql = new StringBuilder("""
                SELECT o.offer_id, o.product_id, o.buyer_id, o.seller_id, 
                       o.offer_price, o.message, o.status, o.created_at, o.updated_at,
                       p.product_name, p.price as product_price, p.image_url,
                       u.username as seller_username, u.full_name as seller_name, 
                       u.avatar_url as seller_avatar
                FROM offers o
                JOIN products p ON o.product_id = p.product_id
                JOIN users u ON o.seller_id = u.user_id
                WHERE o.buyer_id = ?
                """);
            
            if (status != null && !status.isEmpty()) {
                sql.append(" AND o.status = ?");
            }
            
            sql.append(" ORDER BY o.created_at DESC");
            
            stmt = conn.prepareStatement(sql.toString());
            stmt.setInt(1, buyerId);
            if (status != null && !status.isEmpty()) {
                stmt.setString(2, status);
            }
            
            rs = stmt.executeQuery();
            
            List<Map<String, Object>> offers = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> offer = new HashMap<>();
                offer.put("offer_id", rs.getInt("offer_id"));
                offer.put("product_id", rs.getInt("product_id"));
                offer.put("buyer_id", rs.getInt("buyer_id"));
                offer.put("seller_id", rs.getInt("seller_id"));
                offer.put("offer_price", rs.getDouble("offer_price"));
                offer.put("message", rs.getString("message"));
                offer.put("status", rs.getString("status"));
                offer.put("created_at", rs.getTimestamp("created_at").toString());
                offer.put("updated_at", rs.getTimestamp("updated_at").toString());
                offer.put("product_name", rs.getString("product_name"));
                offer.put("product_price", rs.getDouble("product_price"));
                offer.put("image_url", rs.getString("image_url"));
                offer.put("seller_username", rs.getString("seller_username"));
                offer.put("seller_name", rs.getString("seller_name"));
                offer.put("seller_avatar", rs.getString("seller_avatar"));
                
                offers.add(offer);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("offers", offers);
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "データベースエラー: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "エラーが発生しました: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== GET PRODUCT OFFERS ====================
    private void getProductOffers(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        String productIdStr = request.getParameter("product_id");
        if (productIdStr == null) {
            sendError(response, "商品IDが必要です");
            return;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            initializeOffersTable(conn);
            
            String sql = """
                SELECT o.offer_id, o.product_id, o.buyer_id, o.seller_id, 
                       o.offer_price, o.message, o.status, o.created_at, o.updated_at,
                       u.username as buyer_username, u.full_name as buyer_name
                FROM offers o
                JOIN users u ON o.buyer_id = u.user_id
                WHERE o.product_id = ? AND o.status = 'pending'
                ORDER BY o.created_at DESC
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, Integer.parseInt(productIdStr));
            rs = stmt.executeQuery();
            
            List<Map<String, Object>> offers = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> offer = new HashMap<>();
                offer.put("offer_id", rs.getInt("offer_id"));
                offer.put("offer_price", rs.getDouble("offer_price"));
                offer.put("message", rs.getString("message"));
                offer.put("created_at", rs.getTimestamp("created_at").toString());
                offer.put("buyer_username", rs.getString("buyer_username"));
                offer.put("buyer_name", rs.getString("buyer_name"));
                
                offers.add(offer);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("offers", offers);
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "データベースエラー: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "エラーが発生しました: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== GET SINGLE OFFER ====================
    private void getOffer(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        String offerIdStr = request.getParameter("offer_id");
        if (offerIdStr == null) {
            sendError(response, "オファーIDが必要です");
            return;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            initializeOffersTable(conn);
            
            String sql = """
                SELECT o.offer_id, o.product_id, o.buyer_id, o.seller_id, 
                       o.offer_price, o.message, o.status, o.created_at, o.updated_at,
                       p.product_name, p.price as product_price, p.image_url,
                       u1.username as buyer_username, u1.full_name as buyer_name, u1.avatar_url as buyer_avatar,
                       u2.username as seller_username, u2.full_name as seller_name, u2.avatar_url as seller_avatar
                FROM offers o
                JOIN products p ON o.product_id = p.product_id
                JOIN users u1 ON o.buyer_id = u1.user_id
                JOIN users u2 ON o.seller_id = u2.user_id
                WHERE o.offer_id = ?
                """;
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, Integer.parseInt(offerIdStr));
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                Map<String, Object> offer = new HashMap<>();
                offer.put("offer_id", rs.getInt("offer_id"));
                offer.put("product_id", rs.getInt("product_id"));
                offer.put("buyer_id", rs.getInt("buyer_id"));
                offer.put("seller_id", rs.getInt("seller_id"));
                offer.put("offer_price", rs.getDouble("offer_price"));
                offer.put("message", rs.getString("message"));
                offer.put("status", rs.getString("status"));
                offer.put("created_at", rs.getTimestamp("created_at").toString());
                offer.put("updated_at", rs.getTimestamp("updated_at").toString());
                offer.put("product_name", rs.getString("product_name"));
                offer.put("product_price", rs.getDouble("product_price"));
                offer.put("image_url", rs.getString("image_url"));
                offer.put("buyer_username", rs.getString("buyer_username"));
                offer.put("buyer_name", rs.getString("buyer_name"));
                offer.put("buyer_avatar", rs.getString("buyer_avatar"));
                offer.put("seller_username", rs.getString("seller_username"));
                offer.put("seller_name", rs.getString("seller_name"));
                offer.put("seller_avatar", rs.getString("seller_avatar"));
                
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("offer", offer);
                
                sendJsonResponse(response, result);
            } else {
                sendError(response, "オファーが見つかりません");
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "データベースエラー: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "エラーが発生しました: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== ACCEPT OFFER ====================
    private void acceptOffer(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        Integer sellerId = getSessionUserId(session);
        if (session == null || sellerId == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        String offerIdStr = request.getParameter("offer_id");
        
        if (offerIdStr == null) {
            sendError(response, "オファーIDが必要です");
            return;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            initializeOffersTable(conn);
            
            int offerId = Integer.parseInt(offerIdStr);
            
            // Get offer info and verify seller
            String getOfferSql = "SELECT * FROM offers WHERE offer_id = ? AND seller_id = ? AND status = 'pending'";
            stmt = conn.prepareStatement(getOfferSql);
            stmt.setInt(1, offerId);
            stmt.setInt(2, sellerId);
            rs = stmt.executeQuery();
            
            if (!rs.next()) {
                conn.rollback();
                sendError(response, "オファーが見つからないか、既に処理されています");
                return;
            }
            
            int productId = rs.getInt("product_id");
            int buyerId = rs.getInt("buyer_id");
            double offerPrice = rs.getDouble("offer_price");
            
            rs.close();
            stmt.close();
            
            // Update offer status to accepted
            String updateOfferSql = "UPDATE offers SET status = 'accepted', updated_at = NOW() WHERE offer_id = ?";
            stmt = conn.prepareStatement(updateOfferSql);
            stmt.setInt(1, offerId);
            stmt.executeUpdate();
            stmt.close();
            
            // Reject all other pending offers for this product
            String rejectOtherOffersSql = "UPDATE offers SET status = 'rejected', updated_at = NOW() " +
                                        "WHERE product_id = ? AND offer_id != ? AND status = 'pending'";
            stmt = conn.prepareStatement(rejectOtherOffersSql);
            stmt.setInt(1, productId);
            stmt.setInt(2, offerId);
            stmt.executeUpdate();
            stmt.close();
            
            // Update product status to reserved
            String updateProductSql = "UPDATE products SET status = 'reserved' WHERE product_id = ?";
            stmt = conn.prepareStatement(updateProductSql);
            stmt.setInt(1, productId);
            stmt.executeUpdate();
            stmt.close();
            
            // Notify buyer that offer was accepted
            try {
                servlet.NotificationsServlet.createNotification(conn, buyerId, "offer",
                    "オファーが承認されました",
                    "あなたのオファーが承認されました。購入を続けるにはオファー管理ページから「購入する」をクリックしてください。",
                    "offers.jsp", "offer", offerId);
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            conn.commit();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "オファーを受け入れました");
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            try {
                if (conn != null) conn.rollback();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            e.printStackTrace();
            sendError(response, "データベースエラー: " + e.getMessage());
        } catch (Exception e) {
            try {
                if (conn != null) conn.rollback();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            e.printStackTrace();
            sendError(response, "エラーが発生しました: " + e.getMessage());
        } finally {
            try {
                if (conn != null) conn.setAutoCommit(true);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== REJECT OFFER ====================
    private void rejectOffer(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        Integer sellerId = getSessionUserId(session);
        if (session == null || sellerId == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        String offerIdStr = request.getParameter("offer_id");
        
        if (offerIdStr == null) {
            sendError(response, "オファーIDが必要です");
            return;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            initializeOffersTable(conn);
            
            int offerId = Integer.parseInt(offerIdStr);
            
            // Verify seller owns this offer
            String verifySql = "SELECT offer_id FROM offers WHERE offer_id = ? AND seller_id = ? AND status = 'pending'";
            stmt = conn.prepareStatement(verifySql);
            stmt.setInt(1, offerId);
            stmt.setInt(2, sellerId);
            rs = stmt.executeQuery();
            
            if (!rs.next()) {
                sendError(response, "オファーが見つからないか、既に処理されています");
                return;
            }
            
            rs.close();
            stmt.close();
            
            // Update offer status to rejected
            String updateSql = "UPDATE offers SET status = 'rejected', updated_at = NOW() WHERE offer_id = ?";
            stmt = conn.prepareStatement(updateSql);
            stmt.setInt(1, offerId);
            int rowsUpdated = stmt.executeUpdate();
            
            if (rowsUpdated > 0) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "オファーを拒否しました");
                
                sendJsonResponse(response, result);
            } else {
                sendError(response, "オファーの更新に失敗しました");
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "データベースエラー: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "エラーが発生しました: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== CANCEL OFFER ====================
    private void cancelOffer(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        HttpSession session = request.getSession(false);
        Integer buyerId = getSessionUserId(session);
        if (session == null || buyerId == null) {
            sendError(response, "ログインが必要です");
            return;
        }
        String offerIdStr = request.getParameter("offer_id");
        
        if (offerIdStr == null) {
            sendError(response, "オファーIDが必要です");
            return;
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            initializeOffersTable(conn);
            
            int offerId = Integer.parseInt(offerIdStr);
            
            // Verify buyer owns this offer
            String verifySql = "SELECT offer_id FROM offers WHERE offer_id = ? AND buyer_id = ? AND status = 'pending'";
            stmt = conn.prepareStatement(verifySql);
            stmt.setInt(1, offerId);
            stmt.setInt(2, buyerId);
            rs = stmt.executeQuery();
            
            if (!rs.next()) {
                sendError(response, "オファーが見つからないか、既に処理されています");
                return;
            }
            
            rs.close();
            stmt.close();
            
            // Update offer status to cancelled
            String updateSql = "UPDATE offers SET status = 'cancelled', updated_at = NOW() WHERE offer_id = ?";
            stmt = conn.prepareStatement(updateSql);
            stmt.setInt(1, offerId);
            int rowsUpdated = stmt.executeUpdate();
            
            if (rowsUpdated > 0) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "オファーをキャンセルしました");
                
                sendJsonResponse(response, result);
            } else {
                sendError(response, "オファーの更新に失敗しました");
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, "データベースエラー: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "エラーが発生しました: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }
    
    // ==================== COUNTER OFFER ====================
    private void counterOffer(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        // For future implementation - seller can counter with a different price
        sendError(response, "カウンターオファー機能は今後実装予定です");
    }
    
    // ==================== HELPER METHODS ====================
    private void sendJsonResponse(HttpServletResponse response, Object data) throws IOException {
        response.setContentType("application/json; charset=UTF-8");
        PrintWriter out = response.getWriter();
        out.print(gson.toJson(data));
        out.flush();
    }
    
    private void sendError(HttpServletResponse response, String message) throws IOException {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        sendJsonResponse(response, error);
    }
}

