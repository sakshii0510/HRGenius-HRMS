package com.abes.hcl.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record WorkspaceRequest(
        @NotBlank @Size(max = 150) String title,
        @Size(max = 5000) String description,
        @NotBlank @Size(max = 32) String status,
        Long employeeId,
        LocalDate dueDate,
        @Min(0) @Max(100) Integer score,
        boolean visibleToEmployees) {
}
