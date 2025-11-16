package com.safi.task_backend.service.mapper;

import com.safi.task_backend.model.ClaimItem;
import com.safi.task_backend.model.ExpenseClaim;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class ClaimSnapshotMapper {

    private ClaimSnapshotMapper() {}

    public static void applySnapshot(ExpenseClaim claim, ClaimSnapshot snapshot) {
        claim.setClaimantName(snapshot.claimantName());
        claim.setDescription(snapshot.description());
        List<ClaimItem> items = new ArrayList<>();
        if (snapshot.items() != null) {
            for (ClaimSnapshot.ItemSnapshot itemSnapshot : snapshot.items()) {
                ClaimItem item = new ClaimItem();
                item.setItemName(itemSnapshot.itemName());
                item.setQuantity(itemSnapshot.quantity());
                item.setUnitPrice(itemSnapshot.unitPrice());
                item.setClaim(claim);
                items.add(item);
            }
        }
        claim.getItems().clear();
        claim.getItems().addAll(items);
        BigDecimal total = claim.getItems().stream()
                .map(ClaimItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        claim.setTotalAmount(total);
    }
}
