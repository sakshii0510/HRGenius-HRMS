package com.abes.hcl.dto;

import com.abes.hcl.entity.JobStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record JobOpeningResponse(
        Long id,
        String title,
        String department,
        String location,
        String employmentType,
        String description,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        JobStatus status,
        LocalDate postedDate) {
}
