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
        send(user.getEmail(), "Confirma tu cuenta en EmpleoRD", """
                Hola %s,

                Gracias por registrarte en EmpleoRD. Confirma tu correo para activar la cuenta:

                %s

                El enlace vence en 48 horas. Si no creaste esta cuenta, puedes ignorar este mensaje.
                """.formatted(user.getFirstName(), link), link);
    }

    public void sendPasswordReset(User user, String token) {
        String link = properties.getPublicUrl() + "/restablecer?token=" + token;
        send(user.getEmail(), "Restablece tu contraseña de EmpleoRD", """
                Hola %s,

                Recibimos una solicitud para crear o restablecer tu contraseña:

                %s

                El enlace vence en 2 horas. Si no fuiste tú, ignora este mensaje.
                """.formatted(user.getFirstName(), link), link);
    }

    public void sendPlain(String to, String subject, String text) {
        send(to, subject, text, null);
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
