package com.safi.task_backend.repository;

import com.safi.task_backend.model.StockSummary;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockSummaryRepository extends JpaRepository<StockSummary, Long> {
    Optional<StockSummary> findByItemNameIgnoreCase(String itemName);
}
