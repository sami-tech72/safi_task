package com.safi.task_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InvoicePdfData(
        String invoiceNumber,
        LocalDate invoiceDate,
        String claimantName,
        String claimReference,
        List<InvoiceLine> items,
        BigDecimal subtotal,
        BigDecimal tax,
        BigDecimal total,
        boolean managerApproved,
        String headerImage,
        String footerImage) {

    public record InvoiceLine(String itemName, Integer quantity, BigDecimal unitPrice, BigDecimal lineTotal) {}
}
