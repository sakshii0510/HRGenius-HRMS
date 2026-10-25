package com.abes.hcl.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkspaceStatusRequest(@NotBlank @Size(max = 32) String status) {
}
