package com.abes.hcl.dto;

import com.abes.hcl.entity.LeaveStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LeaveReviewRequest(
        @NotNull LeaveStatus status,
        @Size(max = 500) String reviewComment) {
}
