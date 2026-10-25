package com.abes.hcl.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record JobApplicationRequest(
        @Pattern(regexp = "^[+0-9() .-]{7,30}$", message = "Enter a valid phone number")
        String phone,
        @Size(max = 3000) String coverLetter) {
}
