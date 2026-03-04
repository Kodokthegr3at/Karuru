package servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
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
 * Enhanced Wallet Servlet with complete wallet management
 * Features: Balance check, transactions, top-up, withdrawal, transfer
 */
@WebServlet("/Wallet")
public class WalletServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        FilterEncodingUTF8.configureUTF8ForJSON(request, response);
        
        String action = request.getParameter("action");
        
        try {
            if (action == null) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Action parameter is required");
                return;
            }
            
            switch (action) {
                case "getBalance":
                    getBalance(request, response);
                    break;
                case "getTransactions":
                    getTransactions(request, response);
                    break;
                case "getTransactionHistory":
                    getTransactionHistory(request, response);
                    break;
                case "getWalletInfo":
                    getWalletInfo(request, response);
                    break;
                case "topup":
                    // Allow topup via GET for backward compatibility, but prefer POST
                    topupWallet(request, response);
                    break;
                default:
                    sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid action: " + action);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        FilterEncodingUTF8.configureUTF8ForJSON(request, response);
        
        String action = request.getParameter("action");
        
        try {
            if (action == null) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Action parameter is required");
                return;
            }
            
            switch (action) {
                case "topup":
                    topupWallet(request, response);
                    break;
                case "withdraw":
                    withdrawWallet(request, response);
                    break;
                case "transfer":
                    transferWallet(request, response);
                    break;
                case "requestPayout":
                    requestPayout(request, response);
                    break;
                default:
                    sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid action: " + action);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    // ==================== WALLET OPERATIONS ====================

    private void getBalance(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        Integer userId = getUserIdFromSession(request);
        if (userId == null) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Login required");
            return;
        }

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            // Get balance from user_wallets table
            String sql = "SELECT balance, frozen_balance, total_earned, total_spent " +
                        "FROM user_wallets WHERE user_id = ?";
            
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            
            Map<String, Object> result = new HashMap<>();
            
            if (rs.next()) {
                result.put("success", true);
                result.put("balance", rs.getBigDecimal("balance"));
                result.put("frozen_balance", rs.getBigDecimal("frozen_balance"));
                result.put("total_earned", rs.getBigDecimal("total_earned"));
                result.put("total_spent", rs.getBigDecimal("total_spent"));
                result.put("available_balance", rs.getBigDecimal("balance").subtract(rs.getBigDecimal("frozen_balance")));
                result.put("currency", "JPY");
            } else {
                // Create wallet if not exists
                createUserWallet(conn, userId);
                result.put("success", true);
                result.put("balance", BigDecimal.ZERO);
                result.put("frozen_balance", BigDecimal.ZERO);
                result.put("total_earned", BigDecimal.ZERO);
                result.put("total_spent", BigDecimal.ZERO);
                result.put("available_balance", BigDecimal.ZERO);
                result.put("currency", "JPY");
            }
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }

    private void getTransactions(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        Integer userId = getUserIdFromSession(request);
        if (userId == null) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Login required");
            return;
        }

        // Get pagination parameters
        int page = 1;
        int limit = 20;
        try {
            String pageParam = request.getParameter("page");
            if (pageParam != null) {
                page = Integer.parseInt(pageParam);
            }
            String limitParam = request.getParameter("limit");
            if (limitParam != null) {
                limit = Integer.parseInt(limitParam);
            }
        } catch (NumberFormatException e) {
            // Use defaults
        }
        
        int offset = (page - 1) * limit;
        
        // Get filter parameter
        String type = request.getParameter("type");
        
        Connection conn = null;
        PreparedStatement stmt = null;
        PreparedStatement countStmt = null;
        ResultSet rs = null;
        ResultSet countRs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            // Check if table exists first
            try {
                java.sql.DatabaseMetaData meta = conn.getMetaData();
                java.sql.ResultSet tables = meta.getTables(null, null, "wallet_transactions", null);
                if (!tables.next()) {
                    // Table doesn't exist, return empty result
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("transactions", new ArrayList<>());
                    result.put("count", 0);
                    result.put("total", 0);
                    result.put("page", page);
                    result.put("limit", limit);
                    result.put("totalPages", 0);
                    result.put("total_pages", 0);
                    sendJsonResponse(response, result);
                    return;
                }
                tables.close();
            } catch (SQLException e) {
                System.err.println("Error checking table existence: " + e.getMessage());
            }
            
            // First get wallet_id for this user
            String walletIdSql = "SELECT wallet_id FROM user_wallets WHERE user_id = ?";
            PreparedStatement walletStmt = conn.prepareStatement(walletIdSql);
            walletStmt.setInt(1, userId);
            ResultSet walletRs = walletStmt.executeQuery();
            
            Integer walletId = null;
            if (walletRs.next()) {
                walletId = walletRs.getInt("wallet_id");
            }
            walletRs.close();
            walletStmt.close();
            
            if (walletId == null) {
                // No wallet exists, return empty result
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("transactions", new ArrayList<>());
                result.put("count", 0);
                result.put("total", 0);
                result.put("page", page);
                result.put("limit", limit);
                result.put("totalPages", 0);
                result.put("total_pages", 0);
                sendJsonResponse(response, result);
                return;
            }
            
            // Build query with optional type filter
            StringBuilder sqlBuilder = new StringBuilder();
            sqlBuilder.append("SELECT transaction_id, type, amount, description, ");
            sqlBuilder.append("status, reference_type, reference_id, created_at ");
            sqlBuilder.append("FROM wallet_transactions ");
            sqlBuilder.append("WHERE wallet_id = ? ");
            
            List<Object> params = new ArrayList<>();
            params.add(walletId);
            
            // Add type filter if specified
            if (type != null && !type.isEmpty() && !"all".equals(type)) {
                // Map frontend types to database type
                String dbType = mapTransactionType(type);
                if (dbType != null) {
                    sqlBuilder.append("AND type = ? ");
                    params.add(dbType);
                }
            }
            
            sqlBuilder.append("ORDER BY created_at DESC LIMIT ? OFFSET ?");
            params.add(Integer.valueOf(limit));
            params.add(Integer.valueOf(offset));
            
            String sql = sqlBuilder.toString();
            System.out.println("Executing SQL: " + sql);
            System.out.println("Parameters: " + params);
            
            stmt = conn.prepareStatement(sql);
            for (int i = 0; i < params.size(); i++) {
                Object param = params.get(i);
                if (param instanceof Integer) {
                    stmt.setInt(i + 1, (Integer) param);
                } else if (param instanceof String) {
                    stmt.setString(i + 1, (String) param);
                } else {
                    stmt.setObject(i + 1, param);
                }
            }
            rs = stmt.executeQuery();
            
            List<Map<String, Object>> transactions = new ArrayList<>();
            
            while (rs.next()) {
                Map<String, Object> transaction = new HashMap<>();
                try {
                    transaction.put("transaction_id", rs.getInt("transaction_id"));
                    String dbType = rs.getString("type");
                    // Map database type to frontend type
                    transaction.put("type", mapToFrontendType(dbType));
                    transaction.put("transaction_type", dbType); // Keep for backward compatibility
                    
                    BigDecimal amount = rs.getBigDecimal("amount");
                    transaction.put("amount", amount != null ? amount : BigDecimal.ZERO);
                    
                    String description = rs.getString("description");
                    transaction.put("description", description != null ? description : "");
                    
                    String status = rs.getString("status");
                    transaction.put("status", status != null ? status : "unknown");
                    
                    String refType = rs.getString("reference_type");
                    transaction.put("reference_type", refType != null ? refType : null);
                    
                    Integer refId = null;
                    Object refIdObj = rs.getObject("reference_id");
                    if (refIdObj != null) {
                        if (refIdObj instanceof Integer) {
                            refId = (Integer) refIdObj;
                        } else if (refIdObj instanceof Number) {
                            refId = ((Number) refIdObj).intValue();
                        }
                    }
                    transaction.put("reference_id", refId);
                    
                    java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
                    transaction.put("created_at", createdAt != null ? createdAt.toString() : new java.util.Date().toString());
                    
                    transactions.add(transaction);
                } catch (SQLException e) {
                    System.err.println("Error processing transaction row: " + e.getMessage());
                    e.printStackTrace();
                    // Continue to next row
                }
            }
            
            rs.close();
            stmt.close();
            
            // Get total count for pagination
            StringBuilder countSqlBuilder = new StringBuilder();
            countSqlBuilder.append("SELECT COUNT(*) as total FROM wallet_transactions WHERE wallet_id = ? ");
            
            List<Object> countParams = new ArrayList<>();
            countParams.add(walletId);
            
            if (type != null && !type.isEmpty() && !"all".equals(type)) {
                String dbType = mapTransactionType(type);
                if (dbType != null) {
                    countSqlBuilder.append("AND type = ? ");
                    countParams.add(dbType);
                }
            }
            
            String countSql = countSqlBuilder.toString();
            System.out.println("Executing count SQL: " + countSql);
            System.out.println("Count parameters: " + countParams);
            
            countStmt = conn.prepareStatement(countSql);
            for (int i = 0; i < countParams.size(); i++) {
                Object param = countParams.get(i);
                if (param instanceof Integer) {
                    countStmt.setInt(i + 1, (Integer) param);
                } else if (param instanceof String) {
                    countStmt.setString(i + 1, (String) param);
                } else {
                    countStmt.setObject(i + 1, param);
                }
            }
            countRs = countStmt.executeQuery();
            
            int totalCount = 0;
            if (countRs.next()) {
                totalCount = countRs.getInt("total");
            }
            
            int totalPages = (int) Math.ceil((double) totalCount / limit);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("transactions", transactions);
            result.put("count", transactions.size());
            result.put("total", totalCount);
            result.put("page", page);
            result.put("limit", limit);
            result.put("totalPages", totalPages);
            result.put("total_pages", totalPages);
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("SQL Error in getTransactions: " + e.getMessage());
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Unexpected error in getTransactions: " + e.getMessage());
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unexpected error: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, null);
            DatabaseConnection.closeResources(countRs, countStmt, conn);
        }
    }
    
    // Map frontend type to database type (enum: 'deposit','withdrawal','purchase','refund','earning','fee')
    private String mapTransactionType(String frontendType) {
        switch (frontendType.toLowerCase()) {
            case "deposit":
            case "credit":
                return "deposit";
            case "withdrawal":
            case "withdraw":
            case "debit":
                return "withdrawal";
            case "purchase":
                return "purchase";
            case "earning":
                return "earning";
            case "refund":
                return "refund";
            case "fee":
                return "fee";
            default:
                return null;
        }
    }
    
    // Map database type to frontend type
    private String mapToFrontendType(String dbType) {
        if (dbType == null || dbType.trim().isEmpty()) {
            return "unknown";
        }
        String lowerType = dbType.toLowerCase().trim();
        switch (lowerType) {
            case "deposit":
                return "deposit";
            case "withdrawal":
            case "withdraw":
                return "withdrawal";
            case "purchase":
                return "purchase";
            case "earning":
                return "earning";
            case "refund":
                return "refund";
            case "fee":
                return "fee";
            default:
                return lowerType;
        }
    }

    private void getTransactionHistory(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        Integer userId = getUserIdFromSession(request);
        if (userId == null) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Login required");
            return;
        }

        String type = request.getParameter("type"); // credit, debit, all
        String status = request.getParameter("status"); // completed, pending, failed, all
        int page = 1;
        int pageSize = 10;
        
        try {
            page = Integer.parseInt(request.getParameter("page"));
            pageSize = Integer.parseInt(request.getParameter("pageSize"));
        } catch (NumberFormatException e) {
            // Use defaults
        }
        
        int offset = (page - 1) * pageSize;

        Connection conn = null;
        PreparedStatement stmt = null;
        PreparedStatement countStmt = null;
        ResultSet rs = null;
        ResultSet countRs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            // Get wallet_id first
            String walletIdSql = "SELECT wallet_id FROM user_wallets WHERE user_id = ?";
            PreparedStatement walletStmt = conn.prepareStatement(walletIdSql);
            walletStmt.setInt(1, userId);
            ResultSet walletRs = walletStmt.executeQuery();
            
            Integer walletId = null;
            if (walletRs.next()) {
                walletId = walletRs.getInt("wallet_id");
            }
            walletRs.close();
            walletStmt.close();
            
            if (walletId == null) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("transactions", new ArrayList<>());
                result.put("count", 0);
                result.put("total", 0);
                sendJsonResponse(response, result);
                return;
            }
            
            // Build query dynamically
            StringBuilder sqlBuilder = new StringBuilder();
            sqlBuilder.append("SELECT transaction_id, type, amount, description, ");
            sqlBuilder.append("status, reference_type, reference_id, created_at ");
            sqlBuilder.append("FROM wallet_transactions ");
            sqlBuilder.append("WHERE wallet_id = ? ");
            
            List<Object> params = new ArrayList<>();
            params.add(walletId);
            
            if (type != null && !"all".equals(type)) {
                String dbType = mapTransactionType(type);
                if (dbType != null) {
                    sqlBuilder.append("AND type = ? ");
                    params.add(dbType);
                }
            }
            
            if (status != null && !"all".equals(status)) {
                sqlBuilder.append("AND status = ? ");
                params.add(status);
            }
            
            sqlBuilder.append("ORDER BY created_at DESC LIMIT ? OFFSET ?");
            params.add(pageSize);
            params.add(offset);
            
            stmt = conn.prepareStatement(sqlBuilder.toString());
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            
            rs = stmt.executeQuery();
            
            List<Map<String, Object>> transactions = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> transaction = new HashMap<>();
                transaction.put("transaction_id", rs.getInt("transaction_id"));
                String dbType = rs.getString("type");
                transaction.put("type", mapToFrontendType(dbType));
                transaction.put("transaction_type", dbType); // Keep for backward compatibility
                transaction.put("amount", rs.getBigDecimal("amount"));
                transaction.put("description", rs.getString("description"));
                transaction.put("status", rs.getString("status"));
                transaction.put("reference_type", rs.getString("reference_type"));
                Integer refId = rs.getObject("reference_id") != null ? rs.getInt("reference_id") : null;
                transaction.put("reference_id", refId);
                transaction.put("created_at", rs.getTimestamp("created_at").toString());
                transactions.add(transaction);
            }
            
            // Get total count
            StringBuilder countSqlBuilder = new StringBuilder();
            countSqlBuilder.append("SELECT COUNT(*) as total FROM wallet_transactions WHERE wallet_id = ? ");
            
            List<Object> countParams = new ArrayList<>();
            countParams.add(walletId);
            
            if (type != null && !"all".equals(type)) {
                String dbType = mapTransactionType(type);
                if (dbType != null) {
                    countSqlBuilder.append("AND type = ? ");
                    countParams.add(dbType);
                }
            }
            
            if (status != null && !"all".equals(status)) {
                countSqlBuilder.append("AND status = ? ");
                countParams.add(status);
            }
            
            countStmt = conn.prepareStatement(countSqlBuilder.toString());
            for (int i = 0; i < countParams.size(); i++) {
                countStmt.setObject(i + 1, countParams.get(i));
            }
            
            countRs = countStmt.executeQuery();
            int totalCount = 0;
            if (countRs.next()) {
                totalCount = countRs.getInt("total");
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("transactions", transactions);
            result.put("pagination", Map.of(
                "page", page,
                "pageSize", pageSize,
                "total", totalCount,
                "totalPages", (int) Math.ceil((double) totalCount / pageSize)
            ));
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error");
        } finally {
            DatabaseConnection.closeResources(rs, stmt, null);
            DatabaseConnection.closeResources(countRs, countStmt, conn);
        }
    }

    private void getWalletInfo(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        Integer userId = getUserIdFromSession(request);
        if (userId == null) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Login required");
            return;
        }

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            // Get wallet summary with recent transactions
            String walletSql = "SELECT balance, frozen_balance, total_earned, total_spent, " +
                             "updated_at FROM user_wallets WHERE user_id = ?";
            
            stmt = conn.prepareStatement(walletSql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            
            Map<String, Object> walletInfo = new HashMap<>();
            
            if (rs.next()) {
                BigDecimal balance = rs.getBigDecimal("balance");
                BigDecimal frozen = rs.getBigDecimal("frozen_balance");
                
                walletInfo.put("balance", balance);
                walletInfo.put("frozen_balance", frozen);
                walletInfo.put("available_balance", balance.subtract(frozen));
                walletInfo.put("total_earned", rs.getBigDecimal("total_earned"));
                walletInfo.put("total_spent", rs.getBigDecimal("total_spent"));
                walletInfo.put("last_updated", rs.getTimestamp("updated_at").toString());
            } else {
                createUserWallet(conn, userId);
                walletInfo.put("balance", BigDecimal.ZERO);
                walletInfo.put("frozen_balance", BigDecimal.ZERO);
                walletInfo.put("available_balance", BigDecimal.ZERO);
                walletInfo.put("total_earned", BigDecimal.ZERO);
                walletInfo.put("total_spent", BigDecimal.ZERO);
                walletInfo.put("last_updated", new java.util.Date().toString());
            }
            
            rs.close();
            stmt.close();
            
            // Get recent transactions
            // Get wallet_id first
            String walletIdSql = "SELECT wallet_id FROM user_wallets WHERE user_id = ?";
            PreparedStatement walletStmt = conn.prepareStatement(walletIdSql);
            walletStmt.setInt(1, userId);
            ResultSet walletRs = walletStmt.executeQuery();
            
            if (!walletRs.next()) {
                walletRs.close();
                walletStmt.close();
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("recent_transactions", new ArrayList<>());
                sendJsonResponse(response, result);
                return;
            }
            
            int walletId = walletRs.getInt("wallet_id");
            walletRs.close();
            walletStmt.close();
            
            String transactionSql = "SELECT type, amount, description, status, created_at " +
                                  "FROM wallet_transactions WHERE wallet_id = ? " +
                                  "ORDER BY created_at DESC LIMIT 5";
            
            stmt = conn.prepareStatement(transactionSql);
            stmt.setInt(1, walletId);
            rs = stmt.executeQuery();
            
            List<Map<String, Object>> recentTransactions = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> transaction = new HashMap<>();
                String dbType = rs.getString("type");
                transaction.put("type", mapToFrontendType(dbType));
                transaction.put("transaction_type", dbType); // Keep for backward compatibility
                transaction.put("amount", rs.getBigDecimal("amount"));
                transaction.put("description", rs.getString("description"));
                transaction.put("status", rs.getString("status"));
                transaction.put("created_at", rs.getTimestamp("created_at").toString());
                recentTransactions.add(transaction);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("wallet", walletInfo);
            result.put("recent_transactions", recentTransactions);
            
            sendJsonResponse(response, result);
            
        } catch (SQLException e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error");
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }

    private void topupWallet(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        Integer userId = getUserIdFromSession(request);
        if (userId == null) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Login required");
            return;
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(request.getParameter("amount"));
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Amount must be greater than 0");
                return;
            }
        } catch (Exception e) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid amount format");
            return;
        }

        String paymentMethod = request.getParameter("payment_method");
        String description = "Top-up via " + (paymentMethod != null ? paymentMethod : "unknown");

        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Get or create wallet and get current balance
            String getWalletSql = "SELECT wallet_id, balance FROM user_wallets WHERE user_id = ?";
            stmt = conn.prepareStatement(getWalletSql);
            stmt.setInt(1, userId);
            ResultSet walletRs = stmt.executeQuery();
            
            int walletId;
            BigDecimal balanceBefore;
            
            if (!walletRs.next()) {
                // Create wallet if not exists
                walletRs.close();
                stmt.close();
                createUserWallet(conn, userId);
                
                // Get wallet_id after creation
                stmt = conn.prepareStatement(getWalletSql);
                stmt.setInt(1, userId);
                walletRs = stmt.executeQuery();
                if (!walletRs.next()) {
                    walletRs.close();
                    stmt.close();
                    sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to create wallet");
                    return;
                }
            }
            
            walletId = walletRs.getInt("wallet_id");
            balanceBefore = walletRs.getBigDecimal("balance");
            if (balanceBefore == null) {
                balanceBefore = BigDecimal.ZERO;
            }
            walletRs.close();
            stmt.close();
            
            BigDecimal balanceAfter = balanceBefore.add(amount);
            
            // Update wallet balance
            String updateSql = "UPDATE user_wallets SET balance = ?, " +
                             "total_earned = COALESCE(total_earned, 0) + ?, updated_at = NOW() " +
                             "WHERE wallet_id = ?";
            
            stmt = conn.prepareStatement(updateSql);
            stmt.setBigDecimal(1, balanceAfter);
            stmt.setBigDecimal(2, amount);
            stmt.setInt(3, walletId);
            stmt.executeUpdate();
            stmt.close();
            
            // Record transaction
            String transactionSql = "INSERT INTO wallet_transactions " +
                                  "(wallet_id, type, amount, balance_before, balance_after, description, status, reference_type, reference_id, created_at) " +
                                  "VALUES (?, 'deposit', ?, ?, ?, ?, 'completed', ?, ?, NOW())";
            
            stmt = conn.prepareStatement(transactionSql);
            stmt.setInt(1, walletId);
            stmt.setBigDecimal(2, amount);
            stmt.setBigDecimal(3, balanceBefore);
            stmt.setBigDecimal(4, balanceAfter);
            stmt.setString(5, description);
            stmt.setString(6, "topup");
            stmt.setNull(7, java.sql.Types.INTEGER);
            stmt.executeUpdate();
            
            conn.commit();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Top-up completed successfully");
            result.put("amount", amount);
            result.put("transaction_type", "deposit");
            result.put("type", "deposit");
            
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
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Top-up failed: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(null, stmt, conn);
        }
    }

    private void withdrawWallet(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        Integer userId = getUserIdFromSession(request);
        if (userId == null) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Login required");
            return;
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(request.getParameter("amount"));
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Amount must be greater than 0");
                return;
            }
        } catch (Exception e) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid amount format");
            return;
        }

        String bankAccount = request.getParameter("bank_account");
        String bankName = request.getParameter("bank_name");
        String accountHolder = request.getParameter("account_holder");

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Check available balance
            String balanceSql = "SELECT balance, frozen_balance FROM user_wallets WHERE user_id = ?";
            stmt = conn.prepareStatement(balanceSql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            
            if (!rs.next()) {
                sendError(response, HttpServletResponse.SC_NOT_FOUND, "Wallet not found");
                return;
            }
            
            BigDecimal balance = rs.getBigDecimal("balance");
            BigDecimal frozen = rs.getBigDecimal("frozen_balance");
            BigDecimal available = balance.subtract(frozen);
            
            if (available.compareTo(amount) < 0) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, 
                         "Insufficient available balance. Available: " + available);
                return;
            }
            
            rs.close();
            stmt.close();
            
            // Update wallet balance
            String updateSql = "UPDATE user_wallets SET balance = balance - ?, " +
                             "total_spent = COALESCE(total_spent, 0) + ?, updated_at = NOW() " +
                             "WHERE user_id = ?";
            
            stmt = conn.prepareStatement(updateSql);
            stmt.setBigDecimal(1, amount);
            stmt.setBigDecimal(2, amount);
            stmt.setInt(3, userId);
            stmt.executeUpdate();
            
            stmt.close();
            
            // Record withdrawal request
            String description = String.format("Withdrawal to %s (%s - %s)", 
                bankName != null ? bankName : "Unknown Bank", 
                bankAccount, 
                accountHolder != null ? accountHolder : "Unknown"
            );
            
            // Get wallet_id
            String getWalletIdSql = "SELECT wallet_id, balance FROM user_wallets WHERE user_id = ?";
            PreparedStatement getWalletStmt = conn.prepareStatement(getWalletIdSql);
            getWalletStmt.setInt(1, userId);
            ResultSet walletRs = getWalletStmt.executeQuery();
            
            if (!walletRs.next()) {
                walletRs.close();
                getWalletStmt.close();
                sendError(response, HttpServletResponse.SC_NOT_FOUND, "Wallet not found");
                return;
            }
            
            int walletId = walletRs.getInt("wallet_id");
            BigDecimal balanceBefore = walletRs.getBigDecimal("balance");
            if (balanceBefore == null) {
                balanceBefore = BigDecimal.ZERO;
            }
            BigDecimal balanceAfter = balanceBefore.subtract(amount);
            walletRs.close();
            getWalletStmt.close();
            
            String transactionSql = "INSERT INTO wallet_transactions " +
                                  "(wallet_id, type, amount, balance_before, balance_after, description, status, created_at) " +
                                  "VALUES (?, 'withdrawal', ?, ?, ?, ?, 'pending', NOW())";
            
            stmt = conn.prepareStatement(transactionSql);
            stmt.setInt(1, walletId);
            stmt.setBigDecimal(2, amount);
            stmt.setBigDecimal(3, balanceBefore);
            stmt.setBigDecimal(4, balanceAfter);
            stmt.setString(5, description);
            stmt.executeUpdate();
            
            conn.commit();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Withdrawal request submitted for review");
            result.put("amount", amount);
            result.put("status", "pending");
            
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
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Withdrawal failed: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }

    private void transferWallet(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        Integer fromUserId = getUserIdFromSession(request);
        if (fromUserId == null) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Login required");
            return;
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(request.getParameter("amount"));
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Amount must be greater than 0");
                return;
            }
        } catch (Exception e) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid amount format");
            return;
        }

        String toUsername = request.getParameter("to_username");
        String description = request.getParameter("description");
        
        if (toUsername == null || toUsername.trim().isEmpty()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Recipient username is required");
            return;
        }

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Find recipient user
            String findUserSql = "SELECT user_id, username FROM users WHERE username = ? AND deleted_at IS NULL";
            stmt = conn.prepareStatement(findUserSql);
            stmt.setString(1, toUsername);
            rs = stmt.executeQuery();
            
            if (!rs.next()) {
                sendError(response, HttpServletResponse.SC_NOT_FOUND, "Recipient user not found");
                return;
            }
            
            int toUserId = rs.getInt("user_id");
            String toUser = rs.getString("username");
            
            if (toUserId == fromUserId) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Cannot transfer to yourself");
                return;
            }
            
            rs.close();
            stmt.close();
            
            // Check sender's balance
            String balanceSql = "SELECT balance, frozen_balance FROM user_wallets WHERE user_id = ?";
            stmt = conn.prepareStatement(balanceSql);
            stmt.setInt(1, fromUserId);
            rs = stmt.executeQuery();
            
            if (!rs.next()) {
                sendError(response, HttpServletResponse.SC_NOT_FOUND, "Sender wallet not found");
                return;
            }
            
            BigDecimal senderBalance = rs.getBigDecimal("balance");
            BigDecimal senderFrozen = rs.getBigDecimal("frozen_balance");
            BigDecimal senderAvailable = senderBalance.subtract(senderFrozen);
            
            if (senderAvailable.compareTo(amount) < 0) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, 
                         "Insufficient available balance. Available: " + senderAvailable);
                return;
            }
            
            rs.close();
            stmt.close();
            
            // Get wallet_ids and balances for both users
            String getWalletIdSql = "SELECT wallet_id, balance FROM user_wallets WHERE user_id = ?";
            
            // Sender wallet
            PreparedStatement getSenderWalletStmt = conn.prepareStatement(getWalletIdSql);
            getSenderWalletStmt.setInt(1, fromUserId);
            ResultSet senderWalletRs = getSenderWalletStmt.executeQuery();
            if (!senderWalletRs.next()) {
                senderWalletRs.close();
                getSenderWalletStmt.close();
                sendError(response, HttpServletResponse.SC_NOT_FOUND, "Sender wallet not found");
                return;
            }
            int senderWalletId = senderWalletRs.getInt("wallet_id");
            BigDecimal senderBalanceBefore = senderWalletRs.getBigDecimal("balance");
            if (senderBalanceBefore == null) {
                senderBalanceBefore = BigDecimal.ZERO;
            }
            BigDecimal senderBalanceAfter = senderBalanceBefore.subtract(amount);
            senderWalletRs.close();
            getSenderWalletStmt.close();
            
            // Recipient wallet (create if not exists)
            PreparedStatement getRecipientWalletStmt = conn.prepareStatement(getWalletIdSql);
            getRecipientWalletStmt.setInt(1, toUserId);
            ResultSet recipientWalletRs = getRecipientWalletStmt.executeQuery();
            
            int recipientWalletId;
            BigDecimal recipientBalanceBefore;
            
            if (!recipientWalletRs.next()) {
                // Create recipient wallet
                recipientWalletRs.close();
                getRecipientWalletStmt.close();
                createUserWallet(conn, toUserId);
                
                // Get wallet_id after creation
                getRecipientWalletStmt = conn.prepareStatement(getWalletIdSql);
                getRecipientWalletStmt.setInt(1, toUserId);
                recipientWalletRs = getRecipientWalletStmt.executeQuery();
                if (!recipientWalletRs.next()) {
                    recipientWalletRs.close();
                    getRecipientWalletStmt.close();
                    sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to create recipient wallet");
                    return;
                }
            }
            
            recipientWalletId = recipientWalletRs.getInt("wallet_id");
            recipientBalanceBefore = recipientWalletRs.getBigDecimal("balance");
            if (recipientBalanceBefore == null) {
                recipientBalanceBefore = BigDecimal.ZERO;
            }
            BigDecimal recipientBalanceAfter = recipientBalanceBefore.add(amount);
            recipientWalletRs.close();
            getRecipientWalletStmt.close();
            
            // Update sender balance
            String updateSenderSql = "UPDATE user_wallets SET balance = ?, " +
                                   "total_spent = COALESCE(total_spent, 0) + ?, updated_at = NOW() " +
                                   "WHERE wallet_id = ?";
            
            stmt = conn.prepareStatement(updateSenderSql);
            stmt.setBigDecimal(1, senderBalanceAfter);
            stmt.setBigDecimal(2, amount);
            stmt.setInt(3, senderWalletId);
            stmt.executeUpdate();
            stmt.close();
            
            // Update recipient balance
            String updateRecipientSql = "UPDATE user_wallets SET balance = ?, " +
                                      "total_earned = COALESCE(total_earned, 0) + ?, updated_at = NOW() " +
                                      "WHERE wallet_id = ?";
            
            stmt = conn.prepareStatement(updateRecipientSql);
            stmt.setBigDecimal(1, recipientBalanceAfter);
            stmt.setBigDecimal(2, amount);
            stmt.setInt(3, recipientWalletId);
            stmt.executeUpdate();
            stmt.close();
            
            // Record transactions
            String senderDesc = "送金先: " + toUser + (description != null && !description.trim().isEmpty() ? " - " + description : "");
            String recipientDesc = "送金元: " + fromUserId + (description != null && !description.trim().isEmpty() ? " - " + description : "");
            
            // Sender transaction (type: withdrawal)
            String senderTransactionSql = "INSERT INTO wallet_transactions " +
                                        "(wallet_id, type, amount, balance_before, balance_after, description, status, reference_type, reference_id, created_at) " +
                                        "VALUES (?, 'withdrawal', ?, ?, ?, ?, 'completed', 'transfer', ?, NOW())";
            
            stmt = conn.prepareStatement(senderTransactionSql);
            stmt.setInt(1, senderWalletId);
            stmt.setBigDecimal(2, amount);
            stmt.setBigDecimal(3, senderBalanceBefore);
            stmt.setBigDecimal(4, senderBalanceAfter);
            stmt.setString(5, senderDesc);
            stmt.setString(6, "transfer");
            stmt.setInt(7, toUserId);
            stmt.executeUpdate();
            stmt.close();
            
            // Recipient transaction (type: deposit)
            String recipientTransactionSql = "INSERT INTO wallet_transactions " +
                                           "(wallet_id, type, amount, balance_before, balance_after, description, status, reference_type, reference_id, created_at) " +
                                           "VALUES (?, 'deposit', ?, ?, ?, ?, 'completed', 'transfer', ?, NOW())";
            
            stmt = conn.prepareStatement(recipientTransactionSql);
            stmt.setInt(1, recipientWalletId);
            stmt.setBigDecimal(2, amount);
            stmt.setBigDecimal(3, recipientBalanceBefore);
            stmt.setBigDecimal(4, recipientBalanceAfter);
            stmt.setString(5, recipientDesc);
            stmt.setString(6, "transfer");
            stmt.setInt(7, fromUserId);
            stmt.executeUpdate();
            
            conn.commit();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "送金が完了しました");
            result.put("amount", amount);
            result.put("to_user", toUser);
            result.put("to_user_id", toUserId);
            
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
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Transfer failed: " + e.getMessage());
        } finally {
            DatabaseConnection.closeResources(rs, stmt, conn);
        }
    }

    private void requestPayout(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        // Similar to withdraw but for sellers requesting payout of their earnings
        Integer userId = getUserIdFromSession(request);
        if (userId == null) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Login required");
            return;
        }

        // Implementation for seller payout requests
        // This would handle business logic for sellers to withdraw their earnings
        sendError(response, HttpServletResponse.SC_NOT_IMPLEMENTED, "Payout feature coming soon");
    }

    // ==================== HELPER METHODS ====================

    private void createUserWallet(Connection conn, int userId) throws SQLException {
        // Check if wallet already exists
        String checkSql = "SELECT COUNT(*) FROM user_wallets WHERE user_id = ?";
        PreparedStatement checkStmt = conn.prepareStatement(checkSql);
        checkStmt.setInt(1, userId);
        java.sql.ResultSet rs = checkStmt.executeQuery();
        boolean exists = rs.next() && rs.getInt(1) > 0;
        rs.close();
        checkStmt.close();
        
        if (!exists) {
            String sql = "INSERT INTO user_wallets (user_id, balance, frozen_balance, " +
                        "total_earned, total_spent, created_at, updated_at) " +
                        "VALUES (?, 0.00, 0.00, 0.00, 0.00, NOW(), NOW())";
            
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            stmt.executeUpdate();
            stmt.close();
        }
    }

    private Integer getUserIdFromSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        
        Object userIdObj = session.getAttribute("user_id");
        if (userIdObj instanceof Integer) {
            return (Integer) userIdObj;
        } else if (userIdObj instanceof String) {
            try {
                return Integer.parseInt((String) userIdObj);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }


    private void sendJsonResponse(HttpServletResponse response, Object data) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        PrintWriter out = response.getWriter();
        out.print(gson.toJson(data));
        out.flush();
    }

    private void sendError(HttpServletResponse response, int status, String message) 
            throws IOException {
        response.setStatus(status);
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        error.put("status", status);
        
        PrintWriter out = response.getWriter();
        out.print(gson.toJson(error));
        out.flush();
    }

}