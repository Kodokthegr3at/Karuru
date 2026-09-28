package dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * All balance changes go through here so every change is locked and recorded in wallet_transactions.
 * Usage inside one transaction (autocommit off): lock → check available() → debit/credit → commit.
 */
public final class Wallets {

    public record Wallet(int id, BigDecimal balance, BigDecimal frozen) {
        public BigDecimal available() {
            return balance.subtract(frozen);
        }
    }

    /** What a balance change is for, as stored in wallet_transactions. */
    public record Entry(String type, String status, String description, String referenceType, Integer referenceId) {
        public static Entry completed(String type, String description, String referenceType, Integer referenceId) {
            return new Entry(type, "completed", description, referenceType, referenceId);
        }
    }

    private Wallets() {
    }

    /** The user's wallet without locking it, or null if the user has none yet. */
    public static Wallet find(Connection conn, int userId) throws SQLException {
        return select(conn, userId, "");
    }

    /**
     * Locks the user's wallet row until the transaction ends, creating an empty wallet first if needed.
     * Must run with autocommit off, otherwise the lock is released immediately.
     */
    public static Wallet lock(Connection conn, int userId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("INSERT IGNORE INTO user_wallets (user_id) VALUES (?)")) {
            stmt.setInt(1, userId);
            stmt.executeUpdate();
        }
        return select(conn, userId, " FOR UPDATE");
    }

    /** Takes money out of a locked wallet. The caller checks {@link Wallet#available()} first. */
    public static Wallet debit(Connection conn, Wallet wallet, BigDecimal amount, Entry entry) throws SQLException {
        String sql = "UPDATE user_wallets SET balance = balance - ?, total_spent = total_spent + ?, "
                + "last_transaction_at = NOW() WHERE wallet_id = ?";
        return apply(conn, wallet, amount.negate(), sql, amount, entry);
    }

    /** Adds money to a locked wallet. */
    public static Wallet credit(Connection conn, Wallet wallet, BigDecimal amount, Entry entry) throws SQLException {
        String sql = "UPDATE user_wallets SET balance = balance + ?, total_earned = total_earned + ?, "
                + "last_transaction_at = NOW() WHERE wallet_id = ?";
        return apply(conn, wallet, amount, sql, amount, entry);
    }

    private static Wallet apply(Connection conn, Wallet wallet, BigDecimal change, String updateSql, BigDecimal amount,
            Entry entry) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
            stmt.setBigDecimal(1, amount);
            stmt.setBigDecimal(2, amount);
            stmt.setInt(3, wallet.id());
            stmt.executeUpdate();
        }
        Wallet after = new Wallet(wallet.id(), wallet.balance().add(change), wallet.frozen());
        String logSql = "INSERT INTO wallet_transactions "
                + "(wallet_id, type, amount, balance_before, balance_after, description, status, reference_type, reference_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(logSql)) {
            stmt.setInt(1, wallet.id());
            stmt.setString(2, entry.type());
            stmt.setBigDecimal(3, amount);
            stmt.setBigDecimal(4, wallet.balance());
            stmt.setBigDecimal(5, after.balance());
            stmt.setString(6, entry.description());
            stmt.setString(7, entry.status());
            stmt.setString(8, entry.referenceType());
            if (entry.referenceId() == null) {
                stmt.setNull(9, Types.INTEGER);
            } else {
                stmt.setInt(9, entry.referenceId());
            }
            stmt.executeUpdate();
        }
        return after;
    }

    private static Wallet select(Connection conn, int userId, String lockClause) throws SQLException {
        String sql = "SELECT wallet_id, balance, frozen_balance FROM user_wallets WHERE user_id = ?" + lockClause;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next()
                        ? new Wallet(rs.getInt("wallet_id"), rs.getBigDecimal("balance"), rs.getBigDecimal("frozen_balance"))
                        : null;
            }
        }
    }
}
