package com.empleord.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    private Jwt jwt = new Jwt();
    private String corsOrigins = "http://localhost:4200";
    private Storage storage = new Storage();
    private Mail mail = new Mail();
    private String publicUrl = "http://localhost:4200";
    private String apiPublicUrl = "http://localhost:8080";
    private boolean cookieSecure;
    private int jobDefaultDays = 30;
    private boolean production;

    public List<String> corsOriginList() {
        return Arrays.stream(corsOrigins.split(",")).map(String::trim).filter(value -> !value.isEmpty()).toList();
    }

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private int accessMinutes = 15;
        private int refreshDays = 7;
    }

    @Getter
    @Setter
    public static class Storage {
        private String location = "./data/uploads";
        private String signingSecret;
    }

    @Getter
    @Setter
    public static class Mail {
        private String from = "noreply@empleord.do";
        private boolean enabled;
        private boolean logLinks;
    }
}
