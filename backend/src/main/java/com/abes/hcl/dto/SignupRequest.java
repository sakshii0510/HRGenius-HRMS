package com.abes.hcl.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,
        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 254, message = "Email must be at most 254 characters")
        String email,
        @NotBlank(message = "Password is required")
        @Size(min = 12, max = 64, message = "Password must be between 12 and 64 characters")
        String password) {
}
