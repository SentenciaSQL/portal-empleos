package com.empleord.security;

import com.empleord.common.ApiResponse;
import com.empleord.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final RateLimitFilter rateLimitFilter;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter, RateLimitFilter rateLimitFilter, ObjectMapper objectMapper, AppProperties properties) {
        this.jwtFilter = jwtFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.frameOptions(frame -> frame.deny()).contentTypeOptions(content -> {}))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) -> write(response, 401, "Debes iniciar sesión."))
                        .accessDeniedHandler((request, response, ex) -> write(response, 403, "No tienes permiso para esta acción.")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout", "/api/auth/verify-email", "/api/auth/resend-verification", "/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
                        .requestMatchers("/api/catalog/**", "/api/health", "/sitemap.xml", "/robots.txt", "/public/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/jobs", "/api/jobs/*", "/api/companies/*", "/api/media/**", "/api/files/resumes").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/jobs/*/reports", "/api/payments/paypal/webhook").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/jobs/*/applications").hasRole("CANDIDATE")
                        .requestMatchers("/api/candidate/**").hasRole("CANDIDATE")
                        .requestMatchers("/api/company/**").hasRole("EMPLOYER")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/notifications/**", "/api/auth/me", "/api/auth/locale").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.corsOriginList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept-Language"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private void write(jakarta.servlet.http.HttpServletResponse response, int status, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(message, List.of()));
    }
}
