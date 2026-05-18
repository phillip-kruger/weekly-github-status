import jakarta.mail.*;
import jakarta.mail.internet.*;

import java.util.Properties;

public class Notifier {

    private final Config config;

    Notifier(Config config) {
        this.config = config;
    }

    void send(String body, String weekStart, String weekEnd) {
        String subject = "Status " + config.displayName + " - " + weekStart + " to " + weekEnd;

        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host", "smtp.gmail.com");
            props.put("mail.smtp.port", "587");

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(config.gmailAddress, config.gmailAppPassword);
                }
            });

            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(config.gmailAddress));

            for (String to : config.sendTo.split(",")) {
                msg.addRecipient(Message.RecipientType.TO, new InternetAddress(to.trim()));
            }

            msg.setSubject(subject);
            msg.setText(body, "utf-8");

            Transport.send(msg);
            System.out.println("Email sent to " + config.sendTo);
        } catch (Exception e) {
            System.err.println("Failed to send email: " + e.getMessage());
        }
    }
}
