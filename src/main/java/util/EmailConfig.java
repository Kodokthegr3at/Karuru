package util;

/**
 * Email configuration utility
 * Store email credentials separately for better security
 * In production, move these to environment variables or config files
 */
public class EmailConfig {
    
    // Email Configuration
    public static final String MAIL_FROM = "karurufleamarket@gmail.com";
    public static final String MAIL_PASS = "bpte whux lqlr vfvr"; // App password
    public static final String SMTP_HOST = "smtp.gmail.com";
    public static final String SMTP_PORT = "587";
    
    // Application URL
    public static final String BASE_URL = "http://localhost:8085/KaruruFleaMarket";
    
    /**
     * Private constructor to prevent instantiation
     */
    private EmailConfig() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}