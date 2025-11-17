package com.safi.task_backend.dto;

import java.math.BigDecimal;

public record DashboardMetrics(
        long totalClaims,
        long pendingClaims,
        BigDecimal totalClaimValue,
        long invoicesAwaitingApproval,
        int invoiceApprovalRate,
        long stockTracked) {}
