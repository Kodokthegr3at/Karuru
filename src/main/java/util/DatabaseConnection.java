package util;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolProperties;

/**
 * Centralized Database Connection Manager
 * Semua servlet harus menggunakan class ini untuk koneksi database
 * 
 * Konfigurasi: copy src/main/resources/db.properties.example ke db.properties.
 * Kalau db.properties tidak ada, aplikasi gagal start (lihat AppConfig).
 */
public class DatabaseConnection {
    private static final String CONNECTION_PROPERTIES = "?useUnicode=true&characterEncoding=UTF-8" +
            "&serverTimezone=Asia/Tokyo" +
            "&useSSL=false" +
            "&characterSetResults=utf8mb4" +
            "&connectionCollation=utf8mb4_unicode_ci";

    private static final DataSource POOL;

    static {
        Properties props = AppConfig.load("db.properties");
        String url = AppConfig.require(props, "db.url", "db.properties");

        PoolProperties p = new PoolProperties();
        p.setUrl(url.contains("?") ? url + "&" + CONNECTION_PROPERTIES.substring(1) : url + CONNECTION_PROPERTIES);
        p.setDriverClassName("com.mysql.cj.jdbc.Driver");
        p.setUsername(AppConfig.require(props, "db.user", "db.properties"));
        p.setPassword(props.getProperty("db.password", ""));
        p.setMaxActive(Integer.parseInt(props.getProperty("db.pool.max", "20")));
        p.setInitialSize(0);
        p.setTestOnBorrow(true);
        p.setValidationQuery("SELECT 1");
        p.setValidationInterval(30_000);
        // Servlets use setAutoCommit(false) and sometimes return early without rollback:
        // reset autocommit on borrow and roll back anything uncommitted on close().
        p.setDefaultAutoCommit(true);
        p.setRollbackOnReturn(true);
        p.setJdbcInterceptors("ConnectionState;StatementFinalizer");
        // Reclaim connections a servlet forgot to close instead of exhausting the pool.
        p.setRemoveAbandoned(true);
        p.setRemoveAbandonedTimeout(60);
        p.setLogAbandoned(true);
        POOL = new DataSource(p);
    }
    
    /**
     * Get a pooled database connection. close() returns it to the pool.
     * @return Connection object
     * @throws SQLException if connection fails
     */
    public static Connection getConnection() throws SQLException {
        return POOL.getConnection();
    }

    /** Closes the pool; called on undeploy by AppConfig. */
    public static void shutdown() {
        POOL.close();
    }
    
    /**
     * Test connection
     * @return true if connection successful
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
