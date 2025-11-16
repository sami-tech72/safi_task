package com.safi.task_backend.dto;

import com.safi.task_backend.model.enums.InvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record InvoiceResponse(
        Long id,
        String invoiceNumber,
        Long claimId,
        InvoiceStatus status,
        LocalDateTime createdAt,
        LocalDateTime approvedAt,
        BigDecimal subtotal,
        BigDecimal tax,
        BigDecimal total,
        boolean stockApplied,
        List<InvoiceRow> items) {

    public record InvoiceRow(String itemName, Integer quantity, BigDecimal unitPrice, BigDecimal lineTotal) {}
}
