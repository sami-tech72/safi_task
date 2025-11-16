package com.safi.task_backend.repository;

import com.safi.task_backend.model.StatusHistory;
import com.safi.task_backend.model.enums.ClaimStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {
    List<StatusHistory> findByClaimIdOrderByCreatedAtAsc(Long claimId);

    Optional<StatusHistory> findTopByClaimIdAndToStatusOrderByCreatedAtDesc(Long claimId, ClaimStatus status);
}
