package com.safi.task_backend.dto;

import com.safi.task_backend.model.enums.ClaimStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ClaimTransitionRequest(
        @NotNull(message = "Target status is required") ClaimStatus targetStatus,
        @NotBlank(message = "A comment is required for a transition") String comment) {}
