package com.abes.hcl.dto;

import com.abes.hcl.entity.LeaveStatus;
import com.abes.hcl.entity.LeaveType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record LeaveApplicationResponse(
        Long id,
        Long employeeId,
        String employeeName,
        LeaveType leaveType,
        LocalDate startDate,
        LocalDate endDate,
        String reason,
        LeaveStatus status,
        LocalDateTime appliedAt,
        LocalDateTime reviewedAt,
        String reviewComment) {
}
