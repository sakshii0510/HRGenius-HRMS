package com.abes.hcl.dto;

import com.abes.hcl.entity.ApplicationStatus;

import java.time.LocalDateTime;

public record JobApplicationResponse(
        Long id,
        Long jobOpeningId,
        String jobTitle,
        Long candidateId,
        String candidateName,
        String candidateEmail,
        String candidatePhone,
        ApplicationStatus status,
        String coverLetter,
        LocalDateTime appliedAt) {
}
