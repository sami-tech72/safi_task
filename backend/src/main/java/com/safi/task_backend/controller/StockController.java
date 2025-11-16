package com.safi.task_backend.controller;

import com.safi.task_backend.dto.StockSummaryResponse;
import com.safi.task_backend.repository.StockSummaryRepository;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final StockSummaryRepository stockSummaryRepository;

    public StockController(StockSummaryRepository stockSummaryRepository) {
        this.stockSummaryRepository = stockSummaryRepository;
    }

    @GetMapping
    public ResponseEntity<List<StockSummaryResponse>> list() {
        List<StockSummaryResponse> payload = stockSummaryRepository.findAll().stream()
                .map(entry -> new StockSummaryResponse(entry.getId(), entry.getItemName(), entry.getTotalQuantity()))
                .toList();
        return ResponseEntity.ok(payload);
    }
}
