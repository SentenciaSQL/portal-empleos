package com.empleord.auth;

import com.empleord.domain.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotNull(message = "Selecciona el tipo de cuenta") UserRole accountType,
            @NotBlank(message = "El correo es obligatorio") @Email(message = "Ingresa un correo válido") @Size(max = 255) String email,
            @NotBlank(message = "La contraseña es obligatoria")
            @Pattern(regexp = "^(?=.*[A-Za-zÁÉÍÓÚáéíóúÑñ])(?=.*\\d).{8,72}$", message = "La contraseña debe tener entre 8 y 72 caracteres, con letras y números")
            String password,
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 80) String firstName,
            @NotBlank(message = "El apellido es obligatorio") @Size(max = 80) String lastName,
            @Size(max = 30) String phone,
            @Size(max = 160) String companyName
    ) {
    }

    public record LoginRequest(
            @NotBlank(message = "El correo es obligatorio") @Email(message = "Ingresa un correo válido") String email,
            @NotBlank(message = "La contraseña es obligatoria") String password
    ) {
    }

    public record TokenRequest(@NotBlank(message = "El enlace no es válido") String token) {
    }

    public record EmailRequest(
            @NotBlank(message = "El correo es obligatorio") @Email(message = "Ingresa un correo válido") String email
    ) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "El enlace no es válido") String token,
            @NotBlank(message = "La contraseña es obligatoria")
            @Pattern(regexp = "^(?=.*[A-Za-zÁÉÍÓÚáéíóúÑñ])(?=.*\\d).{8,72}$", message = "La contraseña debe tener entre 8 y 72 caracteres, con letras y números")
            String password
    ) {
    }

    public record AuthResponse(String accessToken, long expiresIn, UserResponse user) {
    }

    public record UserResponse(
            UUID id,
            String email,
            String firstName,
            String lastName,
            String role,
            boolean emailVerified,
            UUID companyId,
            String companyName,
            String memberRole
    ) {
    }
}
