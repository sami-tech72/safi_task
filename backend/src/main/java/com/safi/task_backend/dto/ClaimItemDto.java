package com.safi.task_backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ClaimItemDto(
        Long id,
        @NotBlank(message = "Item name is required") String itemName,
        @NotNull(message = "Quantity is required") @Min(1) Integer quantity,
        @NotNull(message = "Unit price is required") @DecimalMin("0.0") BigDecimal unitPrice) {}
