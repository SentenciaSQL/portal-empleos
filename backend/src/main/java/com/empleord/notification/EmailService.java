package com.empleord.notification;

import com.empleord.config.AppProperties;
import com.empleord.domain.entity.User;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final AppProperties properties;

    public EmailService(JavaMailSender mailSender, AppProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    public void sendVerification(User user, String token) {
        String link = properties.getPublicUrl() + "/verificar-correo?token=" + token;
        if (english(user)) {
            send(user.getEmail(), "Confirm your EmpleoRD account", """
                    Hello %s,

                    Thanks for registering on EmpleoRD. Confirm your email to activate the account:

                    %s

                    The link expires in 48 hours. If you did not create this account, you can ignore this message.
                    """.formatted(user.getFirstName(), link), link);
            return;
        }
        send(user.getEmail(), "Confirma tu cuenta en EmpleoRD", """
                Hola %s,

                Gracias por registrarte en EmpleoRD. Confirma tu correo para activar la cuenta:

                %s

                El enlace vence en 48 horas. Si no creaste esta cuenta, puedes ignorar este mensaje.
                """.formatted(user.getFirstName(), link), link);
    }

    public void sendPasswordReset(User user, String token) {
        String link = properties.getPublicUrl() + "/restablecer?token=" + token;
        if (english(user)) {
            send(user.getEmail(), "Reset your EmpleoRD password", """
                    Hello %s,

                    We received a request to create or reset your password:

                    %s

                    The link expires in 2 hours. If this was not you, ignore this message.
                    """.formatted(user.getFirstName(), link), link);
            return;
        }
        send(user.getEmail(), "Restablece tu contraseña de EmpleoRD", """
                Hola %s,

                Recibimos una solicitud para crear o restablecer tu contraseña:

                %s

                El enlace vence en 2 horas. Si no fuiste tú, ignora este mensaje.
                """.formatted(user.getFirstName(), link), link);
    }

    public void sendPasswordChanged(User user) {
        if (english(user)) {
            send(user.getEmail(), "Your EmpleoRD password was updated",
                    "Hello " + user.getFirstName() + ",\n\nYour account password was updated. If this was not you, contact support immediately.", null);
            return;
        }
        send(user.getEmail(), "Tu contraseña de EmpleoRD fue actualizada",
                "Hola " + user.getFirstName() + ",\n\nLa contraseña de tu cuenta fue actualizada. Si no fuiste tú, escribe a soporte de inmediato.", null);
    }

    public void sendApplicationReceived(User user, String jobTitle) {
        if (english(user)) {
            send(user.getEmail(), "New application for " + jobTitle,
                    "Hello " + user.getFirstName() + ",\n\nYou received a new application for " + jobTitle + ". Sign in to EmpleoRD to review it.", null);
            return;
        }
        send(user.getEmail(), "Nueva postulación en " + jobTitle,
                "Hola " + user.getFirstName() + ",\n\nRecibiste una nueva postulación para " + jobTitle + ". Entra a EmpleoRD para revisarla.", null);
    }

    public void sendApplicationStatus(User user, String jobTitle, String companyName) {
        if (english(user)) {
            send(user.getEmail(), "Update on your application",
                    "Hello " + user.getFirstName() + ",\n\nYour application to " + jobTitle + " at " + companyName + " has a new status. Sign in to EmpleoRD to see the detail.", null);
            return;
        }
        send(user.getEmail(), "Actualización de tu postulación",
                "Hola " + user.getFirstName() + ",\n\nTu postulación a " + jobTitle + " en " + companyName + " tiene un nuevo estado. Entra a EmpleoRD para ver el detalle.", null);
    }

    public void sendJobExpiring(User user, String jobTitle) {
        if (english(user)) {
            send(user.getEmail(), "Your job post is about to expire",
                    "Hello " + user.getFirstName() + ",\n\nThe job \"" + jobTitle + "\" expires soon. Sign in to EmpleoRD if you want to renew it.", null);
            return;
        }
        send(user.getEmail(), "Tu oferta está por vencer",
                "Hola " + user.getFirstName() + ",\n\nLa oferta \"" + jobTitle + "\" vence pronto. Entra a EmpleoRD si quieres renovarla.", null);
    }

    public void sendJobAlert(User user, String summary, java.util.List<String> titles, String link) {
        String jobs = String.join("\n", titles);
        String url = properties.getPublicUrl() + link;
        if (english(user)) {
            send(user.getEmail(), "New jobs for your alert",
                    "Hello " + user.getFirstName() + ",\n\n" + summary + "\n\n" + jobs + "\n\n" + url, null);
            return;
        }
        send(user.getEmail(), "Nuevas ofertas para tu alerta",
                "Hola " + user.getFirstName() + ",\n\n" + summary + "\n\n" + jobs + "\n\n" + url, null);
    }

    public void sendSubscription(User user, String subject, String body) {
        String text = english(user)
                ? "Hello " + user.getFirstName() + ",\n\n" + body + "\n\n" + properties.getPublicUrl() + "/empresa/suscripcion"
                : "Hola " + user.getFirstName() + ",\n\n" + body + "\n\n" + properties.getPublicUrl() + "/empresa/suscripcion";
        send(user.getEmail(), subject, text, null);
    }

    public void sendPlain(String to, String subject, String text) {
        send(to, subject, text, null);
    }

    private boolean english(User user) {
        return user.getLocale() != null && user.getLocale().toLowerCase().startsWith("en");
    }

    private void send(String to, String subject, String text, String sensitiveLink) {
        String html = """
                <div style="font-family:Georgia,serif;background:#f6f3ee;padding:24px">
                  <div style="max-width:560px;margin:0 auto;background:#fff;border:1px solid #e6e0d6">
                    <div style="background:#0e2744;color:#fff;padding:18px 24px;font-family:Arial,sans-serif;letter-spacing:.04em">EMPLEORD</div>
                    <div style="padding:24px;color:#142033;line-height:1.55;white-space:pre-line">%s</div>
                  </div>
                </div>
                """.formatted(escape(text));
        if (!properties.getMail().isEnabled()) {
            if (properties.getMail().isLogLinks()) {
                log.info("Correo local. Para={} Asunto={} Enlace={}", to, subject, sensitiveLink);
            } else {
                log.info("Correo no enviado porque MAIL_ENABLED=false. Para={} Asunto={}", to, subject);
            }
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(properties.getMail().getFrom());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html);
            mailSender.send(message);
        } catch (Exception ex) {
            log.warn("No se pudo enviar el correo a {}. Asunto={}", to, subject);
        }
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
