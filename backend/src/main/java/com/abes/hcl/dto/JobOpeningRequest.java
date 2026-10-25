package com.abes.hcl.dto;

import com.abes.hcl.entity.JobStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record JobOpeningRequest(
        @NotBlank @Size(max = 150) String title,
        @NotBlank @Size(max = 100) String department,
        @NotBlank @Size(max = 100) String location,
        @NotBlank @Size(max = 40) String employmentType,
        @NotBlank @Size(max = 5000) String description,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal salaryMin,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal salaryMax,
        @NotNull JobStatus status) {
}
