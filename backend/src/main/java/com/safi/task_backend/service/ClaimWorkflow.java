package com.safi.task_backend.service;

import com.safi.task_backend.model.enums.ClaimStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ClaimWorkflow {

    private final Map<ClaimStatus, Set<ClaimStatus>> transitions = new EnumMap<>(ClaimStatus.class);

    public ClaimWorkflow() {
        transitions.put(ClaimStatus.DRAFT, EnumSet.of(ClaimStatus.DRAFT, ClaimStatus.SUBMITTED));
        transitions.put(ClaimStatus.SUBMITTED, EnumSet.of(ClaimStatus.DRAFT, ClaimStatus.UNDER_REVIEW));
        transitions.put(ClaimStatus.UNDER_REVIEW, EnumSet.of(ClaimStatus.SUBMITTED, ClaimStatus.APPROVED));
        transitions.put(ClaimStatus.APPROVED, EnumSet.of(ClaimStatus.UNDER_REVIEW, ClaimStatus.INVOICED));
        transitions.put(ClaimStatus.INVOICED, EnumSet.of(ClaimStatus.APPROVED));
    }

    public boolean isTransitionAllowed(ClaimStatus current, ClaimStatus target) {
        return transitions.getOrDefault(current, Set.of()).contains(target);
    }

    public Set<ClaimStatus> allowedTargets(ClaimStatus current) {
        return transitions.getOrDefault(current, Set.of());
    }

    public boolean isBackward(ClaimStatus current, ClaimStatus target) {
        if (current == target) {
            return false;
        }
        return switch (current) {
            case DRAFT -> false;
            case SUBMITTED -> target == ClaimStatus.DRAFT;
            case UNDER_REVIEW -> target == ClaimStatus.SUBMITTED;
            case APPROVED -> target == ClaimStatus.UNDER_REVIEW;
            case INVOICED -> target == ClaimStatus.APPROVED;
        };
    }
}
