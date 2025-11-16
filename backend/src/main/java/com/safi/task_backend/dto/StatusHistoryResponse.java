package com.safi.task_backend.dto;

import com.safi.task_backend.model.enums.ClaimStatus;
import java.time.LocalDateTime;

public record StatusHistoryResponse(
        Long id,
        ClaimStatus fromStatus,
        ClaimStatus toStatus,
        String comment,
        LocalDateTime createdAt) {}
