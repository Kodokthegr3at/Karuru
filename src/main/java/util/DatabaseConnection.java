package util;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Centralized Database Connection Manager
 * Semua servlet harus menggunakan class ini untuk koneksi database
 * 
 * Konfigurasi: Buat file db.properties di src/main/resources/ dengan:
 *   db.url=jdbc:mysql://localhost:3306/karuru_db
 *   db.user=root
 *   db.password=
 * Jika file tidak ada, menggunakan default (localhost, root, password kosong)
 */
public class DatabaseConnection {
    private static final String CONNECTION_PROPERTIES = "?useUnicode=true&characterEncoding=UTF-8" +
            "&serverTimezone=Asia/Tokyo" +
            "&useSSL=false" +
            "&characterSetResults=utf8mb4" +
            "&connectionCollation=utf8mb4_unicode_ci";

    private static final String URL;
    private static final String USER;
    private static final String PASS;

    static {
        String url = "jdbc:mysql://localhost:3306/karuru_db" + CONNECTION_PROPERTIES;
        String user = "root";
        String pass = "";

        try (InputStream is = DatabaseConnection.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                String propUrl = props.getProperty("db.url");
                String propUser = props.getProperty("db.user");
                String propPass = props.getProperty("db.password", "");
                if (propUrl != null && !propUrl.isEmpty()) {
                    url = propUrl.contains("?") ? propUrl + "&" + CONNECTION_PROPERTIES.substring(1) : propUrl + CONNECTION_PROPERTIES;
                }
                if (propUser != null && !propUser.isEmpty()) user = propUser;
                if (propPass != null) pass = propPass;
            }
        } catch (Exception e) {
            System.err.println("DatabaseConnection: db.properties not found or invalid, using defaults. " + e.getMessage());
        }

        URL = url;
        USER = user;
        PASS = pass;
    }
    
    static {
        try {
            // Load MySQL JDBC Driver
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("MySQL JDBC Driver not found", e);
        }
    }
    
    /**
     * Get database connection
     * @return Connection object
     * @throws SQLException if connection fails
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
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
    
    /**
     * Close resources safely
     */
    public static void closeResources(AutoCloseable... resources) {
        for (AutoCloseable resource : resources) {
            if (resource != null) {
                try {
                    resource.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}