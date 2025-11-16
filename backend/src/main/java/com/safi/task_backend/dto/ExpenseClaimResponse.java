package com.safi.task_backend.dto;

import com.safi.task_backend.model.enums.ClaimStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record ExpenseClaimResponse(
        Long id,
        String referenceNumber,
        String claimantName,
        String description,
        ClaimStatus status,
        BigDecimal totalAmount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ClaimItemDto> items,
        Set<ClaimStatus> allowedTransitions,
        Long invoiceId) {}
