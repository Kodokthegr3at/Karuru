package servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import util.DatabaseConnection;
import util.FilterEncodingUTF8;

@WebServlet("/RentalServlet")
public class RentalServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Gson gson = new Gson();

    /* ===== レンタルステータス定義 ===== */
    private static final String STATUS_PENDING   = "pending";
    private static final String STATUS_CONFIRMED = "confirmed";
    private static final String STATUS_CANCELLED = "cancelled";

    /* ===== デポジットステータス ===== */
    private static final String DEPOSIT_HELD = "held";

    /* ===================== 共通処理 ===================== */

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        FilterEncodingUTF8.configureUTF8ForJSON(request, response);

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            sendError(response, "ログインが必要です");
            return;
        }

        int userId = (Integer) session.getAttribute("user_id");
        String action = request.getParameter("action");

        try {
            if ("getRentals".equals(action)) {
                String role = request.getParameter("role");
                if ("owner".equals(role)) {
                    getRentalsForOwner(userId, request, response);
                } else {
                    getRentalsForUser(userId, request, response);
                }
            } else if ("getRentalDetail".equals(action)) {
                getRentalDetail(userId, request, response);
            } else {
                sendError(response, "無効なアクションです");
            }
        } catch (Exception e) {
            e.printStackTrace();
            String msg = e.getMessage();
            if (msg != null && !msg.isEmpty() && !msg.contains("SQL") && !msg.contains("database")) {
                sendError(response, msg);
            } else {
                sendError(response, "サーバーエラーが発生しました");
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        FilterEncodingUTF8.configureUTF8ForJSON(request, response);

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            sendError(response, "ログインが必要です");
            return;
        }

        int userId = (Integer) session.getAttribute("user_id");

        JsonObject json = parseRequestToJson(request);
        String action = json.has("action") ? json.get("action").getAsString() : "";

        try {
            switch (action) {
                case "create":
                    createRental(userId, json, response);
                    break;
                case "cancel":
                case "cancelRental":
                    cancelRental(userId, json, response);
                    break;
                case "payRental":
                    payRental(userId, json, response);
                    break;
                case "confirmRental":
                    confirmRental(userId, json, response);
                    break;
                case "completeRental":
                    completeRental(userId, json, response);
                    break;
                default:
                    sendError(response, "無効なアクションです");
            }
        } catch (Exception e) {
            e.printStackTrace();
            String msg = e.getMessage();
            if (msg != null && !msg.isEmpty() && !msg.contains("SQL") && !msg.contains("database")) {
                sendError(response, msg);
            } else {
                sendError(response, "サーバーエラーが発生しました");
            }
        }
    }

    /* ===================== レンタル作成 ===================== */

    /**
     * レンタルを新規作成する
     * 1. 商品チェック
     * 2. 金額計算
     * 3. rentals 登録
     * 4. rental_deposits 登録
     * 5. 在庫更新
     */
    private void createRental(int userId, JsonObject json, HttpServletResponse response) throws Exception {

        int productId = json.get("product_id").getAsInt();
        String startDate = json.get("start_date").getAsString();
        String endDate   = json.get("end_date").getAsString();
        String rentalType = json.get("rental_type").getAsString();
        int quantity = json.has("quantity") ? json.get("quantity").getAsInt() : 1;

        Connection conn = null;

        try {
            conn = DatabaseConnection.getConnection();
            initializeRentalTables(conn);
            conn.setAutoCommit(false);

            /* ===== 商品情報取得 ===== */
            ProductInfo product = getRentalProduct(conn, productId, userId, quantity);

            /* ===== 日付 & 金額計算 ===== */
            long rentalDays = calculateDays(startDate, endDate);
            double rentalPrice = calculateRentalPrice(product, rentalType, rentalDays);
            double totalAmount = rentalPrice * quantity;

            /* ===== rentals 登録 ===== */
            int rentalId = insertRental(
                    conn, userId, product.ownerId, productId,
                    startDate, endDate, rentalPrice, totalAmount, quantity
            );

            /* ===== デポジット作成 ===== */
            insertDeposit(conn, rentalId, product.depositAmount);

            /* ===== 在庫更新 ===== */
            updateStock(conn, productId, quantity);

            /* ===== オーナーに通知 ===== */
            try {
                servlet.NotificationsServlet.createNotification(conn, product.ownerId, "rental",
                    "新しいレンタル予約がありました",
                    "レンタル予約が届きました。支払い完了後に確定してください。",
                    "rental-detail.jsp?id=" + rentalId, "rental", rentalId);
            } catch (Exception e) {
                e.printStackTrace();
            }

            conn.commit();

            sendJson(response, Map.of(
                    "success", true,
                    "rental_id", rentalId,
                    "message", "レンタル予約が完了しました"
            ));

        } catch (Exception e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (conn != null) conn.close();
        }
    }

    /* ===================== レンタル支払い ===================== */

    /**
     * レンタル料金を支払う
     */
    private void payRental(int userId, JsonObject json, HttpServletResponse response) throws Exception {

        int rentalId = json.get("rental_id").getAsInt();
        String paymentMethod = json.has("payment_method") ? json.get("payment_method").getAsString() : "wallet";

        Connection conn = null;

        try {
            conn = DatabaseConnection.getConnection();
            initializeRentalTables(conn);
            conn.setAutoCommit(false);

            RentalPaymentInfo rental = getRentalForPayment(conn, rentalId, userId);

            if (!STATUS_PENDING.equals(rental.paymentStatus)) {
                sendError(response, "このレンタルは既に支払い済みです");
                conn.rollback();
                return;
            }

            double totalAmount = rental.totalAmount;

            switch (paymentMethod) {
                case "wallet":
                    String walletError = processWalletPaymentForRental(conn, userId, rentalId, totalAmount);
                    if (walletError != null) {
                        sendError(response, walletError);
                        conn.rollback();
                        return;
                    }
                    recordWalletTransactionForRental(conn, userId, rentalId, totalAmount);
                    break;
                case "credit_card":
                case "bank_transfer":
                case "cod":
                case "ewallet":
                    // Simulate payment success
                    break;
                default:
                    sendError(response, "無効な支払い方法です");
                    conn.rollback();
                    return;
            }

            updateRentalPaymentStatus(conn, rentalId, userId, paymentMethod);

            /* ===== オーナーに支払い完了通知 ===== */
            try {
                int ownerId = getRentalOwnerId(conn, rentalId);
                if (ownerId > 0 && ownerId != userId) {
                    servlet.NotificationsServlet.createNotification(conn, ownerId, "rental",
                        "レンタル料金の支払いが完了しました",
                        "レンタル #" + rentalId + " の支払いが完了しました。確定して貸し出しの準備をしてください。",
                        "rental-detail.jsp?id=" + rentalId, "rental", rentalId);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            conn.commit();

            sendJson(response, Map.of(
                    "success", true,
                    "payment_status", "paid",
                    "message", "支払いが完了しました"
            ));

        } catch (Exception e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (conn != null) conn.close();
        }
    }

    private int getRentalOwnerId(Connection conn, int rentalId) throws SQLException {
        String sql = "SELECT owner_id FROM rentals WHERE rental_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, rentalId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt("owner_id") : 0;
        }
    }

    private int getRentalRenterId(Connection conn, int rentalId) throws SQLException {
        String sql = "SELECT renter_id FROM rentals WHERE rental_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, rentalId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt("renter_id") : 0;
        }
    }

    /** 支払い対象のレンタル情報を取得 */
    private RentalPaymentInfo getRentalForPayment(Connection conn, int rentalId, int userId) throws Exception {
        String sql = """
            SELECT rental_id, total_amount, payment_status
            FROM rentals
            WHERE rental_id = ? AND renter_id = ?
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, rentalId);
            ps.setInt(2, userId);
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                throw new Exception("レンタルが見つかりません");
            }

            return new RentalPaymentInfo(rs);
        }
    }

    /** レンタル用ウォレット支払い処理 */
    private String processWalletPaymentForRental(Connection conn, int userId, int rentalId, double amount)
            throws SQLException {
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

        if (currentBalance < amount) {
            return String.format("ウォレットの残高が不足しています。残高: ¥%,.0f、必要: ¥%,.0f。ウォレットにチャージしてください。",
                    currentBalance, amount);
        }

        double newBalance = currentBalance - amount;
        String updateWalletSql = "UPDATE user_wallets SET balance = ?, total_spent = COALESCE(total_spent, 0) + ?, " +
                "last_transaction_at = NOW(), updated_at = NOW() WHERE wallet_id = ?";
        stmt = conn.prepareStatement(updateWalletSql);
        stmt.setDouble(1, newBalance);
        stmt.setDouble(2, amount);
        stmt.setInt(3, walletId);
        stmt.executeUpdate();
        stmt.close();

        return null;
    }

    /** レンタル用ウォレット取引記録 */
    private void recordWalletTransactionForRental(Connection conn, int userId, int rentalId, double amount)
            throws SQLException {
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
        double balanceBefore = balanceAfter + amount;

        rs.close();
        stmt.close();

        String transSql = "INSERT INTO wallet_transactions (wallet_id, type, amount, balance_before, " +
                "balance_after, description, reference_type, reference_id, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'rental', ?, 'completed')";
        stmt = conn.prepareStatement(transSql);
        stmt.setInt(1, walletId);
        stmt.setString(2, "purchase");
        stmt.setDouble(3, amount);
        stmt.setDouble(4, balanceBefore);
        stmt.setDouble(5, balanceAfter);
        stmt.setString(6, "レンタル #" + rentalId + " の支払い");
        stmt.setInt(7, rentalId);
        stmt.executeUpdate();
        stmt.close();
    }

    /** レンタル支払いステータスを更新 */
    private void updateRentalPaymentStatus(Connection conn, int rentalId, int userId, String paymentMethod)
            throws SQLException {
        String sql = "UPDATE rentals SET payment_status = 'paid', updated_at = NOW() WHERE rental_id = ? AND renter_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, rentalId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    /* ===================== レンタルキャンセル ===================== */

    /**
     * レンタルをキャンセルする（pending / confirmed のみ）
     */
    private void cancelRental(int userId, JsonObject json, HttpServletResponse response) throws Exception {

        int rentalId = json.get("rental_id").getAsInt();
        Connection conn = null;

        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            RentalInfo rental = getRentalForCancel(conn, rentalId, userId);

            if (!STATUS_PENDING.equals(rental.status) && !STATUS_CONFIRMED.equals(rental.status)) {
                sendError(response, "このレンタルはキャンセルできません");
                conn.rollback();
                return;
            }

            int ownerId = getRentalOwnerId(conn, rentalId);
            cancelRentalRecord(conn, rentalId, userId);
            restoreStock(conn, rental.productId, rental.quantity);

            if (ownerId > 0 && ownerId != userId) {
                try {
                    servlet.NotificationsServlet.createNotification(conn, ownerId, "rental",
                        "レンタルがキャンセルされました",
                        "レンタル #" + rentalId + " が借り手によりキャンセルされました。",
                        "rental-detail.jsp?id=" + rentalId, "rental", rentalId);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            conn.commit();

            sendJson(response, Map.of(
                    "success", true,
                    "message", "レンタルをキャンセルしました"
            ));

        } catch (Exception e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (conn != null) conn.close();
        }
    }

    /* ===================== レンタル確定（オーナー） ===================== */
    private void confirmRental(int userId, JsonObject json, HttpServletResponse response) throws Exception {
        int rentalId = json.get("rental_id").getAsInt();
        try (Connection conn = DatabaseConnection.getConnection()) {
            String checkSql = "SELECT rental_id, owner_id, status, payment_status FROM rentals WHERE rental_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setInt(1, rentalId);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) {
                    sendError(response, "レンタルが見つかりません");
                    return;
                }
                if (rs.getInt("owner_id") != userId) {
                    sendError(response, "このレンタルを確定する権限がありません");
                    return;
                }
                if (!STATUS_PENDING.equals(rs.getString("status"))) {
                    sendError(response, "このレンタルは既に確定済みです");
                    return;
                }
                if (!"paid".equals(rs.getString("payment_status"))) {
                    sendError(response, "支払いが完了していないため確定できません");
                    return;
                }
            }
            String updateSql = "UPDATE rentals SET status = ?, updated_at = NOW() WHERE rental_id = ? AND owner_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setString(1, STATUS_CONFIRMED);
                ps.setInt(2, rentalId);
                ps.setInt(3, userId);
                ps.executeUpdate();
            }
            try {
                int renterId = getRentalRenterId(conn, rentalId);
                if (renterId > 0) {
                    servlet.NotificationsServlet.createNotification(conn, renterId, "rental",
                        "レンタルが確定しました",
                        "レンタル #" + rentalId + " が確定しました。出品者に連絡して受け取りを手配してください。",
                        "rental-detail.jsp?id=" + rentalId, "rental", rentalId);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            sendJson(response, Map.of("success", true, "message", "レンタルを確定しました"));
        }
    }

    /* ===================== 返却完了（借り手） ===================== */
    private void completeRental(int userId, JsonObject json, HttpServletResponse response) throws Exception {
        int rentalId = json.get("rental_id").getAsInt();
        try (Connection conn = DatabaseConnection.getConnection()) {
            String checkSql = "SELECT rental_id, renter_id, owner_id, status, product_id, quantity FROM rentals WHERE rental_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setInt(1, rentalId);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) {
                    sendError(response, "レンタルが見つかりません");
                    return;
                }
                if (rs.getInt("renter_id") != userId) {
                    sendError(response, "このレンタルを返却する権限がありません");
                    return;
                }
                String status = rs.getString("status");
                if (STATUS_CANCELLED.equals(status) || "completed".equals(status)) {
                    sendError(response, "このレンタルは既に完了しています");
                    return;
                }
                int productId = rs.getInt("product_id");
                int quantity = rs.getInt("quantity");
                conn.setAutoCommit(false);
                try {
                    String updateSql = "UPDATE rentals SET status = 'completed', updated_at = NOW() WHERE rental_id = ? AND renter_id = ?";
                    try (PreparedStatement ups = conn.prepareStatement(updateSql)) {
                        ups.setInt(1, rentalId);
                        ups.setInt(2, userId);
                        ups.executeUpdate();
                    }
                    String stockSql = "UPDATE products SET stock_quantity = stock_quantity + ? WHERE product_id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(stockSql)) {
                        stmt.setInt(1, quantity);
                        stmt.setInt(2, productId);
                        stmt.executeUpdate();
                    }
                    conn.commit();
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
                try {
                    int ownerId = getRentalOwnerId(conn, rentalId);
                    if (ownerId > 0) {
                        servlet.NotificationsServlet.createNotification(conn, ownerId, "rental",
                            "レンタル返却が完了しました",
                            "レンタル #" + rentalId + " の返却が完了しました。",
                            "rental-detail.jsp?id=" + rentalId, "rental", rentalId);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            sendJson(response, Map.of("success", true, "message", "返却が完了しました"));
        }
    }

    /* ===================== レンタル一覧取得 ===================== */

    private void getRentalsForUser(int userId, HttpServletRequest request, HttpServletResponse response)
            throws IOException, SQLException {

        try (Connection conn = DatabaseConnection.getConnection()) {
            initializeRentalTables(conn);
        }

        String statusFilter = request.getParameter("status");

        String sql = """
            SELECT r.rental_id, r.rental_number, r.product_id, r.owner_id, r.start_date, r.end_date,
                   r.rental_price, r.total_amount, r.payment_status, r.status, r.created_at,
                   COALESCE(p.product_name, 'Unknown') as product_name,
                   COALESCE(p.image_url, '') as product_image
            FROM rentals r
            LEFT JOIN products p ON r.product_id = p.product_id
            WHERE r.renter_id = ?
            """;

        if (statusFilter != null && !statusFilter.trim().isEmpty()) {
            sql += " AND r.status = ?";
        }
        sql += " ORDER BY r.rental_id DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            if (statusFilter != null && !statusFilter.trim().isEmpty()) {
                ps.setString(2, statusFilter.trim());
            }

            try (ResultSet rs = ps.executeQuery()) {
                java.util.List<Map<String, Object>> rentals = new java.util.ArrayList<>();

                while (rs.next()) {
                    Map<String, Object> rental = new java.util.HashMap<>();
                    rental.put("rental_id", rs.getInt("rental_id"));
                    rental.put("rental_number", rs.getString("rental_number"));
                    rental.put("product_id", rs.getInt("product_id"));
                    rental.put("owner_id", rs.getInt("owner_id"));
                    rental.put("product_name", rs.getString("product_name"));
                    rental.put("product_image", rs.getString("product_image"));
                    rental.put("start_date", rs.getDate("start_date") != null ? rs.getDate("start_date").toString() : null);
                    rental.put("end_date", rs.getDate("end_date") != null ? rs.getDate("end_date").toString() : null);
                    rental.put("rental_price", rs.getDouble("rental_price"));
                    rental.put("total_amount", rs.getDouble("total_amount"));
                    rental.put("payment_status", rs.getString("payment_status"));
                    rental.put("status", rs.getString("status"));
                    rental.put("created_at", rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toString() : null);
                    rentals.add(rental);
                }

                sendJson(response, Map.of("success", true, "rentals", rentals));
            }
        }
    }

    /* ===================== オーナー向けレンタル一覧 ===================== */
    private void getRentalsForOwner(int userId, HttpServletRequest request, HttpServletResponse response)
            throws IOException, SQLException {
        String statusFilter = request.getParameter("status");
        String sql = """
            SELECT r.rental_id, r.rental_number, r.product_id, r.renter_id, r.start_date, r.end_date,
                   r.rental_price, r.total_amount, r.payment_status, r.status, r.created_at,
                   COALESCE(p.product_name, 'Unknown') as product_name,
                   COALESCE(p.image_url, '') as product_image
            FROM rentals r
            LEFT JOIN products p ON r.product_id = p.product_id
            WHERE r.owner_id = ?
            """;
        if (statusFilter != null && !statusFilter.trim().isEmpty()) {
            sql += " AND r.status = ?";
        }
        sql += " ORDER BY r.rental_id DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            if (statusFilter != null && !statusFilter.trim().isEmpty()) {
                ps.setString(2, statusFilter.trim());
            }
            try (ResultSet rs = ps.executeQuery()) {
                java.util.List<Map<String, Object>> rentals = new java.util.ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> rental = new java.util.HashMap<>();
                    rental.put("rental_id", rs.getInt("rental_id"));
                    rental.put("rental_number", rs.getString("rental_number"));
                    rental.put("product_id", rs.getInt("product_id"));
                    rental.put("renter_id", rs.getInt("renter_id"));
                    rental.put("product_name", rs.getString("product_name"));
                    rental.put("product_image", rs.getString("product_image"));
                    rental.put("start_date", rs.getDate("start_date") != null ? rs.getDate("start_date").toString() : null);
                    rental.put("end_date", rs.getDate("end_date") != null ? rs.getDate("end_date").toString() : null);
                    rental.put("rental_price", rs.getDouble("rental_price"));
                    rental.put("total_amount", rs.getDouble("total_amount"));
                    rental.put("payment_status", rs.getString("payment_status"));
                    rental.put("status", rs.getString("status"));
                    rental.put("created_at", rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toString() : null);
                    rental.put("is_owner_view", true);
                    rentals.add(rental);
                }
                sendJson(response, Map.of("success", true, "rentals", rentals));
            }
        }
    }

    /* ===================== レンタル詳細取得 ===================== */
    private void getRentalDetail(int userId, HttpServletRequest request, HttpServletResponse response)
            throws IOException, SQLException {
        String rentalIdStr = request.getParameter("rental_id");
        if (rentalIdStr == null || rentalIdStr.isEmpty()) {
            sendError(response, "レンタルIDが必要です");
            return;
        }
        int rentalId;
        try {
            rentalId = Integer.parseInt(rentalIdStr);
        } catch (NumberFormatException e) {
            sendError(response, "無効なレンタルIDです");
            return;
        }
        String sql = """
            SELECT r.rental_id, r.rental_number, r.product_id, r.owner_id, r.renter_id, r.start_date, r.end_date,
                   r.rental_price, r.total_amount, r.payment_status, r.status, r.quantity, r.created_at,
                   COALESCE(p.product_name, 'Unknown') as product_name,
                   COALESCE(p.image_url, '') as product_image
            FROM rentals r
            LEFT JOIN products p ON r.product_id = p.product_id
            WHERE r.rental_id = ? AND (r.renter_id = ? OR r.owner_id = ?)
            """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, rentalId);
            ps.setInt(2, userId);
            ps.setInt(3, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    sendError(response, "レンタルが見つかりません");
                    return;
                }
                Map<String, Object> rental = new java.util.HashMap<>();
                rental.put("rental_id", rs.getInt("rental_id"));
                rental.put("rental_number", rs.getString("rental_number"));
                rental.put("product_id", rs.getInt("product_id"));
                rental.put("owner_id", rs.getInt("owner_id"));
                rental.put("renter_id", rs.getInt("renter_id"));
                rental.put("product_name", rs.getString("product_name"));
                rental.put("product_image", rs.getString("product_image"));
                rental.put("start_date", rs.getDate("start_date") != null ? rs.getDate("start_date").toString() : null);
                rental.put("end_date", rs.getDate("end_date") != null ? rs.getDate("end_date").toString() : null);
                rental.put("rental_price", rs.getDouble("rental_price"));
                rental.put("total_amount", rs.getDouble("total_amount"));
                rental.put("payment_status", rs.getString("payment_status"));
                rental.put("status", rs.getString("status"));
                rental.put("quantity", rs.getInt("quantity"));
                rental.put("created_at", rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toString() : null);
                rental.put("is_renter", rs.getInt("renter_id") == userId);
                rental.put("is_owner", rs.getInt("owner_id") == userId);
                sendJson(response, Map.of("success", true, "rental", rental));
            }
        }
    }

    /* ===================== テーブル初期化 ===================== */

    private void initializeRentalTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS rentals (
                    rental_id INT AUTO_INCREMENT PRIMARY KEY,
                    rental_number VARCHAR(50) NOT NULL,
                    renter_id INT NOT NULL,
                    owner_id INT NOT NULL,
                    product_id INT NOT NULL,
                    start_date DATE NOT NULL,
                    end_date DATE NOT NULL,
                    rental_price DECIMAL(10,2) NOT NULL,
                    total_amount DECIMAL(10,2) NOT NULL,
                    quantity INT NOT NULL DEFAULT 1,
                    status VARCHAR(20) DEFAULT 'pending',
                    payment_status VARCHAR(20) DEFAULT 'pending',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )
                """);
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS rental_deposits (
                    deposit_id INT AUTO_INCREMENT PRIMARY KEY,
                    rental_id INT NOT NULL,
                    deposit_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
                    deposit_status VARCHAR(20) DEFAULT 'held',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        }
    }

    /* ===================== DBロジック（内部メソッド） ===================== */

    /** レンタル可能な商品を取得 */
    private ProductInfo getRentalProduct(Connection conn, int productId, int userId, int quantity) throws Exception {

        // deposit_amount may not exist in products; use 0 as default
        String sql = """
            SELECT user_id AS owner_id,
                   COALESCE(rental_price_daily, 0) AS rental_price_daily,
                   COALESCE(rental_price_weekly, 0) AS rental_price_weekly,
                   COALESCE(rental_price_monthly, 0) AS rental_price_monthly,
                   0 AS deposit_amount,
                   stock_quantity
            FROM products
            WHERE product_id = ?
              AND is_rental = 1
              AND status = 'available'
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) throw new Exception("レンタル商品が存在しません");
            if (rs.getInt("owner_id") == userId) throw new Exception("自分の商品は借りられません");
            if (rs.getInt("stock_quantity") < quantity) throw new Exception("在庫不足です");

            return new ProductInfo(rs);
        }
    }

    /** rentals テーブルに登録 */
    private int insertRental(Connection conn, int renterId, int ownerId, int productId,
                             String start, String end, double price, double total, int qty) throws SQLException {

        String sql = """
            INSERT INTO rentals
            (rental_number, renter_id, owner_id, product_id,
             start_date, end_date, rental_price, total_amount,
             quantity, status, payment_status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, "RENT-" + System.currentTimeMillis());
            ps.setInt(2, renterId);
            ps.setInt(3, ownerId);
            ps.setInt(4, productId);
            ps.setDate(5, Date.valueOf(start));
            ps.setDate(6, Date.valueOf(end));
            ps.setDouble(7, price);
            ps.setDouble(8, total);
            ps.setInt(9, qty);
            ps.setString(10, STATUS_PENDING);
            ps.setString(11, STATUS_PENDING);

            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            rs.next();
            return rs.getInt(1);
        }
    }

    /** デポジット作成 */
    private void insertDeposit(Connection conn, int rentalId, double deposit) throws SQLException {

        String sql = """
            INSERT INTO rental_deposits (rental_id, deposit_amount, deposit_status)
            VALUES (?, ?, ?)
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, rentalId);
            ps.setDouble(2, deposit);
            ps.setString(3, DEPOSIT_HELD);
            ps.executeUpdate();
        }
    }

    /* ===================== 計算系 ===================== */

    private long calculateDays(String start, String end) {
        long diff = Date.valueOf(end).getTime() - Date.valueOf(start).getTime();
        return (diff / (1000 * 60 * 60 * 24)) + 1;
    }

    private double calculateRentalPrice(ProductInfo p, String type, long days) {
        return switch (type) {
            case "daily"   -> p.daily * days;
            case "weekly"  -> p.weekly * Math.ceil(days / 7.0);
            case "monthly" -> p.monthly * Math.ceil(days / 30.0);
            default -> throw new IllegalArgumentException("無効なレンタルタイプ");
        };
    }

    /* ===================== ヘルパー ===================== */

    private JsonObject parseJson(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = request.getReader()) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return JsonParser.parseString(sb.toString()).getAsJsonObject();
    }

    /**
     * Parse request body to JsonObject. Supports both JSON and form-urlencoded.
     */
    private JsonObject parseRequestToJson(HttpServletRequest request) throws IOException {
        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase().contains("application/json")) {
            String body = "";
            try (BufferedReader br = request.getReader()) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                body = sb.toString().trim();
            }
            if (!body.isEmpty()) {
                try {
                    return JsonParser.parseString(body).getAsJsonObject();
                } catch (Exception e) {
                    // Fall through to form params
                }
            }
        }
        // Fallback: build from form parameters
        JsonObject obj = new JsonObject();
        String action = request.getParameter("action");
        if (action != null) obj.addProperty("action", action);
        String rentalId = request.getParameter("rental_id");
        if (rentalId != null) {
            try {
                obj.addProperty("rental_id", Integer.parseInt(rentalId));
            } catch (NumberFormatException e) {
                obj.addProperty("rental_id", 0);
            }
        }
        String paymentMethod = request.getParameter("payment_method");
        if (paymentMethod != null) obj.addProperty("payment_method", paymentMethod);
        String productId = request.getParameter("product_id");
        if (productId != null) {
            try {
                obj.addProperty("product_id", Integer.parseInt(productId));
            } catch (NumberFormatException e) {}
        }
        String startDate = request.getParameter("start_date");
        if (startDate != null) obj.addProperty("start_date", startDate);
        String endDate = request.getParameter("end_date");
        if (endDate != null) obj.addProperty("end_date", endDate);
        String rentalType = request.getParameter("rental_type");
        if (rentalType != null) obj.addProperty("rental_type", rentalType);
        String quantity = request.getParameter("quantity");
        if (quantity != null) {
            try {
                obj.addProperty("quantity", Integer.parseInt(quantity));
            } catch (NumberFormatException e) {}
        }
        return obj;
    }

    private void sendJson(HttpServletResponse res, Object data) throws IOException {
        res.setContentType("application/json; charset=UTF-8");
        PrintWriter out = res.getWriter();
        out.print(gson.toJson(data));
        out.flush();
    }

    private void sendError(HttpServletResponse res, String msg) throws IOException {
        res.setStatus(400);
        sendJson(res, Map.of("success", false, "error", msg));
    }

    /* ===================== 内部クラス ===================== */

    /** 商品情報保持用 */
    private static class ProductInfo {
        int ownerId;
        double daily, weekly, monthly, depositAmount;

        ProductInfo(ResultSet rs) throws SQLException {
            ownerId = rs.getInt("owner_id");
            daily = rs.getDouble("rental_price_daily");
            weekly = rs.getDouble("rental_price_weekly");
            monthly = rs.getDouble("rental_price_monthly");
            depositAmount = rs.getDouble("deposit_amount");
        }
    }

    /** キャンセル用レンタル情報 */
    private static class RentalInfo {
        String status;
        int productId;
        int quantity;

        RentalInfo(ResultSet rs) throws SQLException {
            status = rs.getString("status");
            productId = rs.getInt("product_id");
            quantity = rs.getInt("quantity");
        }
    }

    /** 支払い用レンタル情報 */
    private static class RentalPaymentInfo {
        double totalAmount;
        String paymentStatus;

        RentalPaymentInfo(ResultSet rs) throws SQLException {
            totalAmount = rs.getDouble("total_amount");
            paymentStatus = rs.getString("payment_status");
        }
    }

    /* ===================== 在庫管理 ===================== */

    /** 在庫を減らす */
    private void updateStock(Connection conn, int productId, int quantity) throws SQLException {
        String sql = "UPDATE products SET stock_quantity = stock_quantity - ? WHERE product_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, quantity);
            ps.setInt(2, productId);
            ps.executeUpdate();
        }
    }

    /** 在庫を戻す */
    private void restoreStock(Connection conn, int productId, int quantity) throws SQLException {
        String sql = "UPDATE products SET stock_quantity = stock_quantity + ? WHERE product_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, quantity);
            ps.setInt(2, productId);
            ps.executeUpdate();
        }
    }

    /* ===================== キャンセル処理 ===================== */

    /** キャンセル対象のレンタル情報を取得 */
    private RentalInfo getRentalForCancel(Connection conn, int rentalId, int userId) throws Exception {
        String sql = """
            SELECT status, product_id, quantity
            FROM rentals
            WHERE rental_id = ? AND renter_id = ?
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, rentalId);
            ps.setInt(2, userId);
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                throw new Exception("レンタルが見つかりません");
            }

            return new RentalInfo(rs);
        }
    }

    /** レンタルをキャンセル状態に更新 */
    private void cancelRentalRecord(Connection conn, int rentalId, int userId) throws SQLException {
        String sql = """
            UPDATE rentals
            SET status = ?, updated_at = NOW()
            WHERE rental_id = ? AND renter_id = ?
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, STATUS_CANCELLED);
            ps.setInt(2, rentalId);
            ps.setInt(3, userId);
            ps.executeUpdate();
        }
    }
}
