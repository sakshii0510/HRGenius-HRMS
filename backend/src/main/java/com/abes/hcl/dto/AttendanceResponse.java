package com.abes.hcl.dto;

import com.abes.hcl.entity.AttendanceStatus;

import java.time.LocalDate;
import java.time.LocalTime;

public record AttendanceResponse(
        Long id,
        Long employeeId,
        String employeeName,
        LocalDate attendanceDate,
        LocalTime checkIn,
        LocalTime checkOut,
        AttendanceStatus status) {
}
