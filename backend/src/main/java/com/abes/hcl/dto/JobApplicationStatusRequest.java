package com.abes.hcl.dto;

import com.abes.hcl.entity.ApplicationStatus;
import jakarta.validation.constraints.NotNull;

public record JobApplicationStatusRequest(@NotNull ApplicationStatus status) {
}
