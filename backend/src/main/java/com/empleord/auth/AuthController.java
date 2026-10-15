package com.empleord.auth;

import com.empleord.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody AuthDtos.RegisterRequest request, HttpServletRequest http) {
        auth.register(request, http.getRemoteAddr());
        return ApiResponse.message("Cuenta creada. Revisa tu correo para confirmarla.");
    }

    @PostMapping("/login")
    public ApiResponse<AuthDtos.AuthResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        return ApiResponse.ok(auth.login(request, response, http.getRemoteAddr()));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthDtos.AuthResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        return ApiResponse.ok(auth.refresh(request, response));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        auth.logout(request, response);
        return ApiResponse.message("Sesión cerrada.");
    }

    @PostMapping("/verify-email")
    public ApiResponse<Void> verify(@Valid @RequestBody AuthDtos.TokenRequest request) {
        auth.verifyEmail(request.token());
        return ApiResponse.message("Correo confirmado. Ya puedes entrar.");
    }

    @PostMapping("/resend-verification")
    public ApiResponse<Void> resend(@Valid @RequestBody AuthDtos.EmailRequest request) {
        auth.resendVerification(request.email());
        return ApiResponse.message("Si la cuenta existe y aún no está confirmada, enviamos un nuevo enlace.");
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgot(@Valid @RequestBody AuthDtos.EmailRequest request) {
        auth.forgotPassword(request.email());
        return ApiResponse.message("Si la cuenta existe, enviamos instrucciones para restablecer la contraseña.");
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> reset(@Valid @RequestBody AuthDtos.ResetPasswordRequest request) {
        auth.resetPassword(request.token(), request.password());
        return ApiResponse.message("Contraseña actualizada. Ya puedes entrar.");
    }

    @GetMapping("/me")
    public ApiResponse<AuthDtos.UserResponse> me() {
        return ApiResponse.ok(auth.me());
    }

    @PatchMapping("/locale")
    public ApiResponse<AuthDtos.UserResponse> locale(@Valid @RequestBody AuthDtos.LocaleRequest request) {
        return ApiResponse.ok(auth.updateLocale(request.locale()));
    }
}
