package servlet;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import javax.servlet.annotation.WebServlet;

import dao.Notifications;
import dao.Wallets;
import dao.Wallets.Entry;
import dao.Wallets.Wallet;
import util.Json;

/**
 * Rentals. Lifecycle: create (pending, stock reserved) → payRental (renter) → confirmRental (owner)
 * → completeRental (renter returns the item, stock released). The renter may cancel while pending or
 * confirmed; a paid rental is then refunded to the renter's wallet.
 */
@WebServlet("/RentalServlet")
public class RentalServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;
    private static final int MAX_RENTAL_DAYS = 365;

    public RentalServlet() {
        route("GET", "getRentals", Access.USER, this::list);
        route("GET", "getRentalDetail", Access.USER, this::detail);
        route("POST", "create", Access.USER, this::create);
        route("POST", "payRental", Access.USER, this::pay);
        route("POST", "confirmRental", Access.USER, this::confirm);
        route("POST", "completeRental", Access.USER, this::complete);
        route("POST", "cancel", Access.USER, this::cancel);
        route("POST", "cancelRental", Access.USER, this::cancel);
    }

    /** The columns of a rental row that the lifecycle actions need, read under a row lock. */
    private record Rental(int id, int renterId, int ownerId, int productId, int quantity, BigDecimal total,
            String status, String paymentStatus) {
    }

    private void list(Call call) throws IOException, SQLException {
        boolean asOwner = "owner".equals(call.param("role"));
        String self = asOwner ? "owner_id" : "renter_id";
        String other = asOwner ? "renter_id" : "owner_id";
        String status = call.param("status");
        String sql = "SELECT r.rental_id, r.rental_number, r.product_id, r." + other + ", r.start_date, r.end_date, "
                + "r.rental_price, r.total_amount, r.payment_status, r.status, r.created_at, "
                + "COALESCE(p.product_name, 'Unknown') AS product_name, COALESCE(p.image_url, '') AS product_image "
                + "FROM rentals r LEFT JOIN products p ON r.product_id = p.product_id WHERE r." + self + " = ?"
                + (status == null ? "" : " AND r.status = ?") + " ORDER BY r.rental_id DESC";
        List<Map<String, Object>> rentals = new ArrayList<>();
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            stmt.setInt(1, call.userId());
            if (status != null) {
                stmt.setString(2, status);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> rental = toJson(rs);
                    rental.put(other, rs.getInt(other));
                    if (asOwner) {
                        rental.put("is_owner_view", true);
                    }
                    rentals.add(rental);
                }
            }
        }
        call.ok(Json.obj("success", true, "rentals", rentals));
    }

    private void detail(Call call) throws IOException, SQLException {
        Integer rentalId = requireRentalId(call);
        if (rentalId == null) {
            return;
        }
        String sql = "SELECT r.rental_id, r.rental_number, r.product_id, r.owner_id, r.renter_id, r.start_date, r.end_date, "
                + "r.rental_price, r.total_amount, r.payment_status, r.status, r.quantity, r.created_at, "
                + "COALESCE(p.product_name, 'Unknown') AS product_name, COALESCE(p.image_url, '') AS product_image "
                + "FROM rentals r LEFT JOIN products p ON r.product_id = p.product_id "
                + "WHERE r.rental_id = ? AND (r.renter_id = ? OR r.owner_id = ?)";
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            stmt.setInt(1, rentalId);
            stmt.setInt(2, call.userId());
            stmt.setInt(3, call.userId());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    call.error(SC_NOT_FOUND, "レンタルが見つかりません");
                    return;
                }
                Map<String, Object> rental = toJson(rs);
                rental.put("owner_id", rs.getInt("owner_id"));
                rental.put("renter_id", rs.getInt("renter_id"));
                rental.put("quantity", rs.getInt("quantity"));
                rental.put("is_renter", rs.getInt("renter_id") == call.userId());
                rental.put("is_owner", rs.getInt("owner_id") == call.userId());
                call.ok(Json.obj("success", true, "rental", rental));
            }
        }
    }

    private void create(Call call) throws IOException, SQLException {
        Integer productId = call.intParam("product_id");
        Integer quantityParam = call.intParam("quantity");
        int quantity = call.param("quantity") == null ? 1 : quantityParam == null ? 0 : quantityParam;
        String type = call.param("rental_type", "");
        if (productId == null || quantity < 1) {
            call.error(SC_BAD_REQUEST, "商品IDと1以上の数量が必要です");
            return;
        }
        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(call.param("start_date", ""));
            end = LocalDate.parse(call.param("end_date", ""));
        } catch (DateTimeParseException e) {
            call.error(SC_BAD_REQUEST, "開始日と終了日を正しく入力してください");
            return;
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (start.isBefore(LocalDate.now()) || days < 1 || days > MAX_RENTAL_DAYS) {
            call.error(SC_BAD_REQUEST, "レンタル期間は本日以降、" + MAX_RENTAL_DAYS + "日以内で指定してください");
            return;
        }

        Connection db = call.beginTransaction();
        int ownerId;
        BigDecimal unitPrice;
        String sql = "SELECT user_id, rental_price_daily, rental_price_weekly, rental_price_monthly FROM products "
                + "WHERE product_id = ? AND is_rental = 1 AND status = 'available' FOR UPDATE";
        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            stmt.setInt(1, productId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    call.error(SC_NOT_FOUND, "レンタル商品が存在しません");
                    return;
                }
                ownerId = rs.getInt("user_id");
                unitPrice = switch (type) {
                    case "daily" -> multiply(rs.getBigDecimal("rental_price_daily"), days);
                    case "weekly" -> multiply(rs.getBigDecimal("rental_price_weekly"), (days + 6) / 7);
                    case "monthly" -> multiply(rs.getBigDecimal("rental_price_monthly"), (days + 29) / 30);
                    default -> null;
                };
            }
        }
        if (ownerId == call.userId()) {
            call.error(SC_BAD_REQUEST, "自分の商品は借りられません");
            return;
        }
        // A missing or zero price for the chosen period means the owner does not offer that period.
        if (unitPrice == null || unitPrice.signum() <= 0) {
            call.error(SC_BAD_REQUEST, "この商品はこの料金タイプでレンタルできません");
            return;
        }
        if (!takeStock(db, productId, quantity)) {
            call.error(SC_CONFLICT, "在庫不足です");
            return;
        }
        BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(quantity));
        int rentalId = insertRental(db, call.userId(), ownerId, productId, start, end, unitPrice, total, quantity);
        try (PreparedStatement stmt = db.prepareStatement(
                "INSERT INTO rental_deposits (rental_id, deposit_amount, deposit_status) VALUES (?, 0, 'held')")) {
            stmt.setInt(1, rentalId);
            stmt.executeUpdate();
        }
        Notifications.create(db, ownerId, "rental", "新しいレンタル予約がありました",
                "レンタル予約が届きました。支払い完了後に確定してください。",
                "rental-detail.jsp?id=" + rentalId, "rental", rentalId);
        db.commit();
        call.ok(Json.obj("success", true, "rental_id", rentalId, "message", "レンタル予約が完了しました"));
    }

    /** Wallet payments are real; the other methods are recorded as paid (no payment gateway yet). */
    private void pay(Call call) throws IOException, SQLException {
        Integer rentalId = requireRentalId(call);
        if (rentalId == null) {
            return;
        }
        String method = call.param("payment_method", "wallet");
        if (!List.of("wallet", "credit_card", "ewallet", "bank_transfer", "cod").contains(method)) {
            call.error(SC_BAD_REQUEST, "無効な支払い方法です");
            return;
        }
        Connection db = call.beginTransaction();
        Rental rental = lock(db, rentalId);
        if (rental == null || rental.renterId() != call.userId()) {
            call.error(SC_NOT_FOUND, "レンタルが見つかりません");
            return;
        }
        if ("cancelled".equals(rental.status())) {
            call.error(SC_CONFLICT, "キャンセル済みのレンタルは支払いできません");
            return;
        }
        if (!"pending".equals(rental.paymentStatus())) {
            call.error(SC_CONFLICT, "このレンタルは既に支払い済みです");
            return;
        }
        if (method.equals("wallet")) {
            Wallet wallet = Wallets.lock(db, call.userId());
            if (wallet.available().compareTo(rental.total()) < 0) {
                call.error(SC_BAD_REQUEST, String.format(
                        "ウォレットの残高が不足しています。残高: ¥%,.0f、必要: ¥%,.0f。ウォレットにチャージしてください。",
                        wallet.available(), rental.total()));
                return;
            }
            Wallets.debit(db, wallet, rental.total(),
                    Entry.completed("purchase", "レンタル #" + rentalId + " の支払い", "rental", rentalId));
        }
        setColumn(db, rentalId, "payment_status", "paid");
        Notifications.create(db, rental.ownerId(), "rental", "レンタル料金の支払いが完了しました",
                "レンタル #" + rentalId + " の支払いが完了しました。確定して貸し出しの準備をしてください。",
                "rental-detail.jsp?id=" + rentalId, "rental", rentalId);
        db.commit();
        call.ok(Json.obj("success", true, "payment_status", "paid", "message", "支払いが完了しました"));
    }

    private void confirm(Call call) throws IOException, SQLException {
        Integer rentalId = requireRentalId(call);
        if (rentalId == null) {
            return;
        }
        Connection db = call.beginTransaction();
        Rental rental = lock(db, rentalId);
        if (rental == null) {
            call.error(SC_NOT_FOUND, "レンタルが見つかりません");
            return;
        }
        if (rental.ownerId() != call.userId()) {
            call.error(SC_FORBIDDEN, "このレンタルを確定する権限がありません");
            return;
        }
        if (!"pending".equals(rental.status())) {
            call.error(SC_CONFLICT, "このレンタルは既に確定済みです");
            return;
        }
        if (!"paid".equals(rental.paymentStatus())) {
            call.error(SC_CONFLICT, "支払いが完了していないため確定できません");
            return;
        }
        setColumn(db, rentalId, "status", "confirmed");
        Notifications.create(db, rental.renterId(), "rental", "レンタルが確定しました",
                "レンタル #" + rentalId + " が確定しました。出品者に連絡して受け取りを手配してください。",
                "rental-detail.jsp?id=" + rentalId, "rental", rentalId);
        db.commit();
        call.ok(Json.obj("success", true, "message", "レンタルを確定しました"));
    }

    /** The renter returns the item; only a confirmed (active) rental can be completed. */
    private void complete(Call call) throws IOException, SQLException {
        Integer rentalId = requireRentalId(call);
        if (rentalId == null) {
            return;
        }
        Connection db = call.beginTransaction();
        Rental rental = lock(db, rentalId);
        if (rental == null) {
            call.error(SC_NOT_FOUND, "レンタルが見つかりません");
            return;
        }
        if (rental.renterId() != call.userId()) {
            call.error(SC_FORBIDDEN, "このレンタルを返却する権限がありません");
            return;
        }
        if (!"confirmed".equals(rental.status())) {
            call.error(SC_CONFLICT, "確定済みのレンタルのみ返却できます");
            return;
        }
        setColumn(db, rentalId, "status", "completed");
        releaseStock(db, rental.productId(), rental.quantity());
        Notifications.create(db, rental.ownerId(), "rental", "レンタル返却が完了しました",
                "レンタル #" + rentalId + " の返却が完了しました。", "rental-detail.jsp?id=" + rentalId, "rental", rentalId);
        db.commit();
        call.ok(Json.obj("success", true, "message", "返却が完了しました"));
    }

    private void cancel(Call call) throws IOException, SQLException {
        Integer rentalId = requireRentalId(call);
        if (rentalId == null) {
            return;
        }
        Connection db = call.beginTransaction();
        Rental rental = lock(db, rentalId);
        if (rental == null || rental.renterId() != call.userId()) {
            call.error(SC_NOT_FOUND, "レンタルが見つかりません");
            return;
        }
        if (!"pending".equals(rental.status()) && !"confirmed".equals(rental.status())) {
            call.error(SC_CONFLICT, "このレンタルはキャンセルできません");
            return;
        }
        setColumn(db, rentalId, "status", "cancelled");
        releaseStock(db, rental.productId(), rental.quantity());
        boolean refunded = "paid".equals(rental.paymentStatus());
        if (refunded) {
            Wallet wallet = Wallets.lock(db, call.userId());
            Wallets.credit(db, wallet, rental.total(),
                    Entry.completed("refund", "レンタル #" + rentalId + " のキャンセル返金", "rental", rentalId));
            setColumn(db, rentalId, "payment_status", "refunded");
        }
        Notifications.create(db, rental.ownerId(), "rental", "レンタルがキャンセルされました",
                "レンタル #" + rentalId + " が借り手によりキャンセルされました。",
                "rental-detail.jsp?id=" + rentalId, "rental", rentalId);
        db.commit();
        call.ok(Json.obj("success", true,
                "message", refunded ? "レンタルをキャンセルし、ウォレットに返金しました" : "レンタルをキャンセルしました"));
    }

    private static Rental lock(Connection db, int rentalId) throws SQLException {
        String sql = "SELECT rental_id, renter_id, owner_id, product_id, quantity, total_amount, status, payment_status "
                + "FROM rentals WHERE rental_id = ? FOR UPDATE";
        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            stmt.setInt(1, rentalId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new Rental(rs.getInt("rental_id"), rs.getInt("renter_id"), rs.getInt("owner_id"),
                        rs.getInt("product_id"), rs.getInt("quantity"), rs.getBigDecimal("total_amount"),
                        rs.getString("status"), rs.getString("payment_status"));
            }
        }
    }

    /** Reserves stock only if enough is left; false means another rental or order took it first. */
    private static boolean takeStock(Connection db, int productId, int quantity) throws SQLException {
        String sql = "UPDATE products SET stock_quantity = stock_quantity - ? WHERE product_id = ? AND stock_quantity >= ?";
        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            stmt.setInt(1, quantity);
            stmt.setInt(2, productId);
            stmt.setInt(3, quantity);
            return stmt.executeUpdate() == 1;
        }
    }

    private static void releaseStock(Connection db, int productId, int quantity) throws SQLException {
        try (PreparedStatement stmt = db.prepareStatement(
                "UPDATE products SET stock_quantity = stock_quantity + ? WHERE product_id = ?")) {
            stmt.setInt(1, quantity);
            stmt.setInt(2, productId);
            stmt.executeUpdate();
        }
    }

    private static int insertRental(Connection db, int renterId, int ownerId, int productId, LocalDate start, LocalDate end,
            BigDecimal price, BigDecimal total, int quantity) throws SQLException {
        String sql = "INSERT INTO rentals (rental_number, renter_id, owner_id, product_id, start_date, end_date, "
                + "rental_price, total_amount, quantity, status, payment_status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending', 'pending')";
        try (PreparedStatement stmt = db.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, "RENT-" + System.currentTimeMillis() + "-" + ThreadLocalRandom.current().nextInt(1000, 10000));
            stmt.setInt(2, renterId);
            stmt.setInt(3, ownerId);
            stmt.setInt(4, productId);
            stmt.setDate(5, Date.valueOf(start));
            stmt.setDate(6, Date.valueOf(end));
            stmt.setBigDecimal(7, price);
            stmt.setBigDecimal(8, total);
            stmt.setInt(9, quantity);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    /** Sets one of the rental's status columns. The column name is always a constant from this class. */
    private static void setColumn(Connection db, int rentalId, String column, String value) throws SQLException {
        try (PreparedStatement stmt = db.prepareStatement("UPDATE rentals SET " + column + " = ? WHERE rental_id = ?")) {
            stmt.setString(1, value);
            stmt.setInt(2, rentalId);
            stmt.executeUpdate();
        }
    }

    private static Integer requireRentalId(Call call) throws IOException {
        if (call.param("rental_id") == null) {
            call.error(SC_BAD_REQUEST, "レンタルIDが必要です");
            return null;
        }
        Integer id = call.intParam("rental_id");
        if (id == null) {
            call.error(SC_BAD_REQUEST, "無効なレンタルIDです");
        }
        return id;
    }

    private static BigDecimal multiply(BigDecimal price, long units) {
        return price == null ? null : price.multiply(BigDecimal.valueOf(units));
    }

    private static Map<String, Object> toJson(ResultSet rs) throws SQLException {
        Date start = rs.getDate("start_date");
        Date end = rs.getDate("end_date");
        return Json.obj(
                "rental_id", rs.getInt("rental_id"),
                "rental_number", rs.getString("rental_number"),
                "product_id", rs.getInt("product_id"),
                "product_name", rs.getString("product_name"),
                "product_image", rs.getString("product_image"),
                "start_date", start == null ? null : start.toString(),
                "end_date", end == null ? null : end.toString(),
                "rental_price", rs.getBigDecimal("rental_price"),
                "total_amount", rs.getBigDecimal("total_amount"),
                "payment_status", rs.getString("payment_status"),
                "status", rs.getString("status"),
                "created_at", rs.getTimestamp("created_at").toString());
    }
}
