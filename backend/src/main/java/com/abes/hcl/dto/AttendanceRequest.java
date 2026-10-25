package com.abes.hcl.dto;

import com.abes.hcl.entity.AttendanceStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.time.LocalTime;

public record AttendanceRequest(
        @NotNull @Positive Long employeeId,
        @NotNull @PastOrPresent LocalDate attendanceDate,
        LocalTime checkIn,
        LocalTime checkOut,
        @NotNull AttendanceStatus status) {
}
