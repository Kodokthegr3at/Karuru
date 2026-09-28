package util;

import java.util.Properties;

/**
 * Email configuration, loaded from email.properties on the classpath (src/main/resources/).
 * Copy email.properties.example to email.properties and fill in the credentials.
 * email.properties is git-ignored — never commit real credentials.
 */
public class EmailConfig {

    public static final String MAIL_FROM;
    public static final String MAIL_PASS;
    public static final String SMTP_HOST;
    public static final String SMTP_PORT;
    public static final String BASE_URL;

    static {
        Properties props = AppConfig.load("email.properties");
        MAIL_FROM = AppConfig.require(props, "mail.from", "email.properties");
        MAIL_PASS = AppConfig.require(props, "mail.password", "email.properties");
        SMTP_HOST = props.getProperty("mail.smtp.host", "smtp.gmail.com");
        SMTP_PORT = props.getProperty("mail.smtp.port", "587");
        BASE_URL = AppConfig.require(props, "app.base_url", "email.properties");
    }

    private EmailConfig() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
