package com.abes.hcl.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CandidateRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @Pattern(regexp = "^[+0-9() .-]{7,30}$", message = "Enter a valid phone number")
        String phone) {
}
