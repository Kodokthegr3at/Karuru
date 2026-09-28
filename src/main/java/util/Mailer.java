package util;

import java.io.UnsupportedEncodingException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/** Sends plain-text mail through the SMTP server configured in email.properties (STARTTLS). */
public final class Mailer {
    private static final Logger LOG = Logger.getLogger(Mailer.class.getName());
    private static final String TIMEOUT_MS = "30000";

    private Mailer() {
    }

    /** Returns false (and logs why) instead of throwing, so callers can degrade gracefully. */
    public static boolean send(String to, String subject, String body) {
        try {
            Message message = new MimeMessage(session());
            message.setFrom(new InternetAddress(EmailConfig.MAIL_FROM, "Karuru Flea Market"));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
            message.setSubject(subject);
            message.setText(body);
            Transport.send(message);
            return true;
        } catch (MessagingException | UnsupportedEncodingException e) {
            LOG.log(Level.WARNING, "Could not send \"" + subject + "\" to " + to, e);
            return false;
        }
    }

    private static Session session() {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");
        props.put("mail.smtp.ssl.checkserveridentity", "true");
        props.put("mail.smtp.host", EmailConfig.SMTP_HOST);
        props.put("mail.smtp.port", EmailConfig.SMTP_PORT);
        props.put("mail.smtp.connectiontimeout", TIMEOUT_MS);
        props.put("mail.smtp.timeout", TIMEOUT_MS);
        props.put("mail.smtp.writetimeout", TIMEOUT_MS);
        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(EmailConfig.MAIL_FROM, EmailConfig.MAIL_PASS);
            }
        });
    }
}
