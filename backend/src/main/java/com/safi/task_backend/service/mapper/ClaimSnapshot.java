package com.safi.task_backend.service.mapper;

import com.safi.task_backend.model.ClaimItem;
import com.safi.task_backend.model.ExpenseClaim;
import java.math.BigDecimal;
import java.util.List;

public record ClaimSnapshot(String claimantName, String description, List<ItemSnapshot> items) {

    public static ClaimSnapshot fromClaim(ExpenseClaim claim) {
        return new ClaimSnapshot(
                claim.getClaimantName(),
                claim.getDescription(),
                claim.getItems().stream()
                        .map(item -> new ItemSnapshot(item.getItemName(), item.getQuantity(), item.getUnitPrice()))
                        .toList());
    }

    public record ItemSnapshot(String itemName, Integer quantity, BigDecimal unitPrice) {}
}
