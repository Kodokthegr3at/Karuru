package util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

/**
 * Loads config files from the classpath and fails deployment at startup
 * if any required config is missing, instead of failing on the first request.
 */
@WebListener
public class AppConfig implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        // Touching the classes runs their static initializers, which throw on bad config.
        String from = EmailConfig.MAIL_FROM;
        if (!DatabaseConnection.testConnection()) {
            sce.getServletContext().log("WARNING: database is not reachable, check db.properties");
        }
        sce.getServletContext().log("Config OK (mail from " + from + ")");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        try {
            DatabaseConnection.shutdown();
        } catch (NoClassDefFoundError e) {
            // pool was never created because config was missing
        }
        // Unregister the JDBC driver loaded by this webapp to avoid a classloader leak on redeploy.
        java.util.Collections.list(java.sql.DriverManager.getDrivers()).stream()
                .filter(d -> d.getClass().getClassLoader() == AppConfig.class.getClassLoader())
                .forEach(d -> {
                    try {
                        java.sql.DriverManager.deregisterDriver(d);
                    } catch (java.sql.SQLException e) {
                        sce.getServletContext().log("Could not deregister " + d, e);
                    }
                });
        com.mysql.cj.jdbc.AbandonedConnectionCleanupThread.checkedShutdown();
    }

    public static Properties load(String fileName) {
        try (InputStream is = AppConfig.class.getClassLoader().getResourceAsStream(fileName)) {
            if (is == null) {
                throw new IllegalStateException(fileName + " not found on classpath. Copy "
                        + fileName + ".example in src/main/resources/ to " + fileName + " and fill it in.");
            }
            Properties props = new Properties();
            props.load(is);
            return props;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + fileName, e);
        }
    }

    public static String require(Properties props, String key, String fileName) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required key '" + key + "' in " + fileName);
        }
        return value.trim();
    }
}
