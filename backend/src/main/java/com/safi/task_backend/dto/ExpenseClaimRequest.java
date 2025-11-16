package com.safi.task_backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ExpenseClaimRequest(
        @NotBlank(message = "Claimant name is required") String claimantName,
        @Size(max = 2000, message = "Description is too long") String description,
        @Valid List<ClaimItemDto> items) {}
