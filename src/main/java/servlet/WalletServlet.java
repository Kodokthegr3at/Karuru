package servlet;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.annotation.WebServlet;

import dao.Wallets;
import dao.Wallets.Entry;
import dao.Wallets.Wallet;
import util.Json;

/**
 * The user's in-app wallet (JPY): balance, transaction history, top-up, withdrawal and transfers to other users.
 * Top-up is simulated — there is no payment gateway yet, so it credits the wallet directly.
 */
@WebServlet("/Wallet")
public class WalletServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000");
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    /** Filter values accepted from the page → transaction type stored in the database. */
    private static final Map<String, String> TYPE_FILTERS = Map.of(
            "deposit", "deposit", "credit", "deposit",
            "withdrawal", "withdrawal", "withdraw", "withdrawal", "debit", "withdrawal",
            "purchase", "purchase", "earning", "earning", "refund", "refund", "fee", "fee");

    public WalletServlet() {
        route("GET", "getBalance", Access.USER, this::balance);
        route("GET", "getTransactions", Access.USER, this::transactions);
        route("POST", "topup", Access.USER, this::topup);
        route("POST", "withdraw", Access.USER, this::withdraw);
        route("POST", "transfer", Access.USER, this::transfer);
    }

    private void balance(Call call) throws IOException, SQLException {
        String sql = "SELECT balance, frozen_balance, total_earned, total_spent FROM user_wallets WHERE user_id = ?";
        BigDecimal balance = BigDecimal.ZERO;
        BigDecimal frozen = BigDecimal.ZERO;
        BigDecimal earned = BigDecimal.ZERO;
        BigDecimal spent = BigDecimal.ZERO;
        try (PreparedStatement stmt = call.db().prepareStatement(sql)) {
            stmt.setInt(1, call.userId());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    balance = rs.getBigDecimal("balance");
                    frozen = rs.getBigDecimal("frozen_balance");
                    earned = rs.getBigDecimal("total_earned");
                    spent = rs.getBigDecimal("total_spent");
                }
            }
        }
        call.ok(Json.obj(
                "success", true,
                "balance", balance,
                "frozen_balance", frozen,
                "available_balance", balance.subtract(frozen),
                "total_earned", earned,
                "total_spent", spent,
                "currency", "JPY"));
    }

    private void transactions(Call call) throws IOException, SQLException {
        Integer page = call.intParam("page");
        Integer limit = call.intParam("limit");
        int pageSize = limit == null ? DEFAULT_PAGE_SIZE : Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
        int pageNumber = page == null || page < 1 ? 1 : page;
        String type = TYPE_FILTERS.get(call.param("type", "all"));

        Wallet wallet = Wallets.find(call.db(), call.userId());
        List<Map<String, Object>> transactions = new ArrayList<>();
        int total = 0;
        if (wallet != null) {
            String where = " FROM wallet_transactions WHERE wallet_id = ?" + (type == null ? "" : " AND type = ?");
            try (PreparedStatement stmt = call.db().prepareStatement(
                    "SELECT transaction_id, type, amount, description, status, reference_type, reference_id, created_at"
                            + where + " ORDER BY created_at DESC, transaction_id DESC LIMIT ? OFFSET ?")) {
                int i = bindFilter(stmt, wallet.id(), type);
                stmt.setInt(i++, pageSize);
                stmt.setInt(i, (pageNumber - 1) * pageSize);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        transactions.add(Json.obj(
                                "transaction_id", rs.getInt("transaction_id"),
                                "type", rs.getString("type"),
                                "amount", rs.getBigDecimal("amount"),
                                "description", rs.getString("description") == null ? "" : rs.getString("description"),
                                "status", rs.getString("status"),
                                "reference_type", rs.getString("reference_type"),
                                "reference_id", rs.getObject("reference_id"),
                                "created_at", rs.getTimestamp("created_at").toString()));
                    }
                }
            }
            try (PreparedStatement stmt = call.db().prepareStatement("SELECT COUNT(*)" + where)) {
                bindFilter(stmt, wallet.id(), type);
                try (ResultSet rs = stmt.executeQuery()) {
                    rs.next();
                    total = rs.getInt(1);
                }
            }
        }
        call.ok(Json.obj(
                "success", true,
                "transactions", transactions,
                "count", transactions.size(),
                "total", total,
                "page", pageNumber,
                "limit", pageSize,
                "totalPages", (total + pageSize - 1) / pageSize));
    }

    private void topup(Call call) throws IOException, SQLException {
        BigDecimal amount = requireAmount(call);
        if (amount == null) {
            return;
        }
        String method = call.param("payment_method");
        Connection db = call.beginTransaction();
        Wallet wallet = Wallets.lock(db, call.userId());
        Wallets.credit(db, wallet, amount,
                Entry.completed("deposit", "Top-up via " + (method == null ? "unknown" : method), "topup", null));
        db.commit();
        call.ok(Json.obj("success", true, "message", "Top-up completed successfully", "amount", amount));
    }

    /** The money leaves the wallet immediately; the bank payout itself is handled manually (status "pending"). */
    private void withdraw(Call call) throws IOException, SQLException {
        BigDecimal amount = requireAmount(call);
        if (amount == null) {
            return;
        }
        Connection db = call.beginTransaction();
        Wallet wallet = Wallets.lock(db, call.userId());
        if (wallet.available().compareTo(amount) < 0) {
            call.error(SC_BAD_REQUEST, "Insufficient available balance. Available: " + wallet.available());
            return;
        }
        String description = String.format("Withdrawal to %s (%s - %s)",
                orDefault(call.param("bank_name"), "Unknown Bank"),
                call.param("bank_account"),
                orDefault(call.param("account_holder"), "Unknown"));
        Wallets.debit(db, wallet, amount, new Entry("withdrawal", "pending", description, null, null));
        db.commit();
        call.ok(Json.obj("success", true, "message", "Withdrawal request submitted for review", "amount", amount,
                "status", "pending"));
    }

    private void transfer(Call call) throws IOException, SQLException {
        BigDecimal amount = requireAmount(call);
        if (amount == null) {
            return;
        }
        String toUsername = call.param("to_username");
        if (toUsername == null) {
            call.error(SC_BAD_REQUEST, "Recipient username is required");
            return;
        }
        Connection db = call.beginTransaction();
        Integer toUserId = null;
        try (PreparedStatement stmt = db.prepareStatement("SELECT user_id FROM users WHERE username = ? AND deleted_at IS NULL")) {
            stmt.setString(1, toUsername);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    toUserId = rs.getInt(1);
                }
            }
        }
        if (toUserId == null) {
            call.error(SC_NOT_FOUND, "Recipient user not found");
            return;
        }
        int fromUserId = call.userId();
        if (toUserId == fromUserId) {
            call.error(SC_BAD_REQUEST, "Cannot transfer to yourself");
            return;
        }
        // Always lock the lower user id first so two opposite transfers cannot deadlock.
        Wallet first = Wallets.lock(db, Math.min(fromUserId, toUserId));
        Wallet second = Wallets.lock(db, Math.max(fromUserId, toUserId));
        Wallet sender = fromUserId < toUserId ? first : second;
        Wallet recipient = fromUserId < toUserId ? second : first;
        if (sender.available().compareTo(amount) < 0) {
            call.error(SC_BAD_REQUEST, "Insufficient available balance. Available: " + sender.available());
            return;
        }
        String note = call.param("description");
        String suffix = note == null ? "" : " - " + note;
        Wallets.debit(db, sender, amount, Entry.completed("withdrawal", "送金先: " + toUsername + suffix, "transfer", toUserId));
        Wallets.credit(db, recipient, amount, Entry.completed("deposit", "送金元: " + fromUserId + suffix, "transfer", fromUserId));
        db.commit();
        call.ok(Json.obj("success", true, "message", "送金が完了しました", "amount", amount,
                "to_user", toUsername, "to_user_id", toUserId));
    }

    /** A positive amount of at most ¥1,000,000 with at most two decimals; otherwise sends a 400 and returns null. */
    private static BigDecimal requireAmount(Call call) throws IOException {
        BigDecimal amount;
        try {
            amount = new BigDecimal(call.param("amount"));
        } catch (NumberFormatException | NullPointerException e) {
            call.error(SC_BAD_REQUEST, "Invalid amount format");
            return null;
        }
        if (amount.signum() <= 0) {
            call.error(SC_BAD_REQUEST, "Amount must be greater than 0");
            return null;
        }
        if (amount.compareTo(MAX_AMOUNT) > 0 || amount.scale() > 2) {
            call.error(SC_BAD_REQUEST, "Amount must be at most ¥1,000,000 with up to 2 decimals");
            return null;
        }
        return amount;
    }

    private static int bindFilter(PreparedStatement stmt, int walletId, String type) throws SQLException {
        stmt.setInt(1, walletId);
        if (type == null) {
            return 2;
        }
        stmt.setString(2, type);
        return 3;
    }

    private static String orDefault(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
