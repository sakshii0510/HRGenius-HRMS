package com.abes.hcl.dto;

import com.abes.hcl.entity.WorkspaceModule;

import java.time.Instant;
import java.time.LocalDate;

public record WorkspaceResponse(
        Long id,
        WorkspaceModule module,
        String title,
        String description,
        String status,
        Long employeeId,
        String employeeName,
        LocalDate dueDate,
        Integer score,
        boolean visibleToEmployees,
        String fileName,
        String contentType,
        long fileSize,
        Instant createdAt,
        Instant updatedAt) {
}
