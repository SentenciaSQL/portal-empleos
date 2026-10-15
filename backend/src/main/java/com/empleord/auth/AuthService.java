package com.empleord.auth;

import com.empleord.audit.AuditService;
import com.empleord.billing.PlanService;
import com.empleord.common.ApiException;
import com.empleord.common.Text;
import com.empleord.common.Tokens;
import com.empleord.config.AppProperties;
import com.empleord.domain.entity.CandidateProfile;
import com.empleord.domain.entity.Company;
import com.empleord.domain.entity.CompanyMember;
import com.empleord.domain.entity.EmployerProfile;
import com.empleord.domain.entity.RefreshToken;
import com.empleord.domain.entity.Role;
import com.empleord.domain.entity.User;
import com.empleord.domain.enums.CompanyMemberRole;
import com.empleord.domain.enums.CompanyStatus;
import com.empleord.domain.enums.NotificationType;
import com.empleord.domain.enums.UserRole;
import com.empleord.domain.enums.UserStatus;
import com.empleord.domain.enums.VerificationStatus;
import com.empleord.domain.repository.CandidateProfileRepository;
import com.empleord.domain.repository.CompanyMemberRepository;
import com.empleord.domain.repository.CompanyRepository;
import com.empleord.domain.repository.CountryRepository;
import com.empleord.domain.repository.EmployerProfileRepository;
import com.empleord.domain.repository.RefreshTokenRepository;
import com.empleord.domain.repository.RoleRepository;
import com.empleord.domain.repository.UserRepository;
import com.empleord.notification.EmailService;
import com.empleord.notification.NotificationService;
import com.empleord.security.JwtService;
import com.empleord.security.SecurityUtils;
import com.empleord.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

@Service
public class AuthService {

    private static final String DUMMY_HASH = "$2b$12$qQVaOqBQ1GNPgmSDOfXDZOGIeW4kFpTpC26.pmnMZcBjGeo5tHGBK";
    private static final String COOKIE = "refresh_token";

    private final UserRepository users;
    private final RoleRepository roles;
    private final CandidateProfileRepository profiles;
    private final EmployerProfileRepository employers;
    private final CompanyRepository companies;
    private final CompanyMemberRepository members;
    private final CountryRepository countries;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final EmailService email;
    private final NotificationService notifications;
    private final AuditService audit;
    private final PlanService plans;
    private final AppProperties properties;

    public AuthService(UserRepository users, RoleRepository roles, CandidateProfileRepository profiles, EmployerProfileRepository employers,
                       CompanyRepository companies, CompanyMemberRepository members, CountryRepository countries, RefreshTokenRepository refreshTokens,
                       PasswordEncoder encoder, JwtService jwt, EmailService email, NotificationService notifications, AuditService audit,
                       PlanService plans, AppProperties properties) {
        this.users = users;
        this.roles = roles;
        this.profiles = profiles;
        this.employers = employers;
        this.companies = companies;
        this.members = members;
        this.countries = countries;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.email = email;
        this.notifications = notifications;
        this.audit = audit;
        this.plans = plans;
        this.properties = properties;
    }

    @Transactional
    public void register(AuthDtos.RegisterRequest request, String ip) {
        if (request.accountType() != UserRole.CANDIDATE && request.accountType() != UserRole.EMPLOYER) {
            throw ApiException.badRequest("Selecciona una cuenta de candidato o de empresa.");
        }
        String emailAddress = request.email().trim().toLowerCase();
        if (users.findActiveByEmail(emailAddress).isPresent()) {
            throw ApiException.conflict("Ya existe una cuenta con ese correo.");
        }
        User user = newUser(emailAddress, request.password(), request.firstName(), request.lastName(), request.phone(), request.accountType());
        String token = assignVerification(user);
        users.save(user);
        if (request.accountType() == UserRole.CANDIDATE) {
            CandidateProfile profile = new CandidateProfile();
            profile.setUser(user);
            profile.setSalaryCurrency("DOP");
            profile.setOpenToWork(true);
            profiles.save(profile);
        } else {
            createCompanyForOwner(user, request.companyName());
        }
        email.sendVerification(user, token);
        notifications.notify(user, NotificationType.ACCOUNT, "Confirma tu correo", "Revisa tu bandeja para activar la cuenta.", "/verificar-correo");
        audit.record(user.getId(), "REGISTER", "User", user.getId().toString(), request.accountType().name(), ip);
    }

    @Transactional
    public User createRecruiter(String emailAddress, String firstName, String lastName) {
        String normalized = emailAddress.trim().toLowerCase();
        if (users.findActiveByEmail(normalized).isPresent()) {
            throw ApiException.conflict("Ya existe una cuenta con ese correo.");
        }
        User user = newUser(normalized, Tokens.random() + "a1", firstName, lastName, null, UserRole.EMPLOYER);
        String token = assignReset(user);
        users.save(user);
        EmployerProfile profile = new EmployerProfile();
        profile.setUser(user);
        employers.save(profile);
        email.sendPasswordReset(user, token);
        return user;
    }

    @Transactional
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request, HttpServletResponse response, String ip) {
        String emailAddress = request.email().trim().toLowerCase();
        User user = users.findActiveByEmail(emailAddress).orElse(null);
        if (user == null) {
            encoder.matches(request.password(), DUMMY_HASH);
            throw ApiException.unauthorized("Correo o contraseña incorrectos.");
        }
        if (!encoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Correo o contraseña incorrectos.");
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw ApiException.forbidden("Tu cuenta está suspendida.");
        }
        if (!user.isEmailVerified() || user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.forbidden("Debes verificar tu correo antes de entrar.");
        }
        audit.record(user.getId(), "LOGIN", "User", user.getId().toString(), null, ip);
        return issue(user, response);
    }

    @Transactional
    public AuthDtos.AuthResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        String raw = readCookie(request);
        if (raw == null) {
            throw ApiException.unauthorized("La sesión expiró.");
        }
        RefreshToken stored = refreshTokens.findByTokenHash(Tokens.sha256(raw))
                .orElseThrow(() -> ApiException.unauthorized("La sesión expiró."));
        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.unauthorized("La sesión expiró.");
        }
        User user = stored.getUser();
        if (user.getDeletedAt() != null || user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.unauthorized("La sesión expiró.");
        }
        stored.setRevoked(true);
        return issue(user, response);
    }

    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String raw = readCookie(request);
        if (raw != null) {
            refreshTokens.findByTokenHash(Tokens.sha256(raw)).ifPresent(token -> token.setRevoked(true));
        }
        writeCookie(response, "", 0);
    }

    @Transactional
    public void verifyEmail(String token) {
        User user = users.findByEmailVerificationTokenHashAndDeletedAtIsNull(Tokens.sha256(token))
                .orElseThrow(() -> ApiException.badRequest("El enlace de verificación no es válido."));
        if (user.getEmailVerificationExpiresAt() == null || user.getEmailVerificationExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("El enlace de verificación venció. Solicita otro.");
        }
        user.setEmailVerified(true);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerificationTokenHash(null);
        user.setEmailVerificationExpiresAt(null);
    }

    @Transactional
    public void resendVerification(String emailAddress) {
        users.findActiveByEmail(emailAddress.trim().toLowerCase()).ifPresent(user -> {
            if (!user.isEmailVerified()) {
                String token = assignVerification(user);
                email.sendVerification(user, token);
            }
        });
    }

    @Transactional
    public void forgotPassword(String emailAddress) {
        users.findActiveByEmail(emailAddress.trim().toLowerCase()).ifPresent(user -> {
            if (user.getStatus() != UserStatus.DELETED) {
                String token = assignReset(user);
                email.sendPasswordReset(user, token);
            }
        });
    }

    @Transactional
    public void resetPassword(String token, String password) {
        User user = users.findByPasswordResetTokenHashAndDeletedAtIsNull(Tokens.sha256(token))
                .orElseThrow(() -> ApiException.badRequest("El enlace para restablecer la contraseña no es válido."));
        if (user.getPasswordResetExpiresAt() == null || user.getPasswordResetExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("El enlace venció. Solicita otro.");
        }
        user.setPasswordHash(encoder.encode(password));
        user.setPasswordResetTokenHash(null);
        user.setPasswordResetExpiresAt(null);
        user.setEmailVerified(true);
        if (user.getStatus() == UserStatus.PENDING) {
            user.setStatus(UserStatus.ACTIVE);
        }
        refreshTokens.revokeActiveForUser(user.getId());
        email.sendPlain(user.getEmail(), "Tu contraseña de EmpleoRD fue actualizada",
                "Hola " + user.getFirstName() + ",\n\nLa contraseña de tu cuenta fue actualizada. Si no fuiste tú, escribe a soporte de inmediato.");
    }

    @Transactional(readOnly = true)
    public AuthDtos.UserResponse me() {
        UserPrincipal principal = SecurityUtils.requireUser();
        User user = users.findActiveById(principal.getId()).orElseThrow(() -> ApiException.unauthorized("Debes iniciar sesión."));
        return toUser(user);
    }

    public AuthDtos.UserResponse toUser(User user) {
        CompanyMember member = members.findByUserId(user.getId()).orElse(null);
        return new AuthDtos.UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole().getName(),
                user.isEmailVerified(),
                member == null ? null : member.getCompany().getId(),
                member == null ? null : member.getCompany().getName(),
                member == null ? null : member.getMemberRole().name());
    }

    private AuthDtos.AuthResponse issue(User user, HttpServletResponse response) {
        String raw = Tokens.random();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(Tokens.sha256(raw));
        token.setExpiresAt(Instant.now().plus(properties.getJwt().getRefreshDays(), ChronoUnit.DAYS));
        refreshTokens.save(token);
        writeCookie(response, raw, properties.getJwt().getRefreshDays() * 24L * 60L * 60L);
        UserPrincipal principal = UserPrincipal.from(user);
        return new AuthDtos.AuthResponse(jwt.createAccessToken(principal), jwt.accessSeconds(), toUser(user));
    }

    private User newUser(String emailAddress, String password, String firstName, String lastName, String phone, UserRole roleName) {
        Role role = roles.findByName(roleName.name()).orElseThrow(() -> ApiException.badRequest("El rol no existe."));
        User user = new User();
        user.setRole(role);
        user.setEmail(emailAddress);
        user.setPasswordHash(encoder.encode(password));
        user.setFirstName(Text.required(firstName, "El nombre"));
        user.setLastName(Text.required(lastName, "El apellido"));
        user.setPhone(Text.clean(phone));
        user.setStatus(UserStatus.PENDING);
        user.setEmailVerified(false);
        return user;
    }

    private void createCompanyForOwner(User user, String companyName) {
        String name = Text.required(companyName, "El nombre de la empresa");
        EmployerProfile employer = new EmployerProfile();
        employer.setUser(user);
        employer.setPhone(user.getPhone());
        employers.save(employer);
        Company company = new Company();
        company.setName(name);
        company.setSlug(uniqueSlug(name));
        company.setCountry(countries.findById("DO").orElseThrow());
        company.setVerificationStatus(VerificationStatus.PENDING);
        company.setStatus(CompanyStatus.ACTIVE);
        companies.save(company);
        CompanyMember member = new CompanyMember();
        member.setCompany(company);
        member.setUser(user);
        member.setMemberRole(CompanyMemberRole.OWNER);
        members.save(member);
        plans.assignFree(company);
    }

    private String uniqueSlug(String name) {
        String base = Text.slug(name);
        String slug = base;
        int suffix = 2;
        while (companies.existsBySlug(slug)) {
            slug = base + "-" + suffix++;
        }
        return slug;
    }

    private String assignVerification(User user) {
        String token = Tokens.random();
        user.setEmailVerificationTokenHash(Tokens.sha256(token));
        user.setEmailVerificationExpiresAt(Instant.now().plus(48, ChronoUnit.HOURS));
        return token;
    }

    private String assignReset(User user) {
        String token = Tokens.random();
        user.setPasswordResetTokenHash(Tokens.sha256(token));
        user.setPasswordResetExpiresAt(Instant.now().plus(2, ChronoUnit.HOURS));
        return token;
    }

    private void writeCookie(HttpServletResponse response, String value, long maxAge) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE, value)
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String readCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> COOKIE.equals(cookie.getName()))
                .map(jakarta.servlet.http.Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
