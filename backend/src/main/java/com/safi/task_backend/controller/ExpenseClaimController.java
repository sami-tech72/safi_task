package com.safi.task_backend.controller;

import com.safi.task_backend.dto.ClaimTransitionRequest;
import com.safi.task_backend.dto.ExpenseClaimRequest;
import com.safi.task_backend.dto.ExpenseClaimResponse;
import com.safi.task_backend.dto.PageResponse;
import com.safi.task_backend.dto.StatusHistoryResponse;
import com.safi.task_backend.service.ExpenseClaimService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/claims")
public class ExpenseClaimController {

    private final ExpenseClaimService claimService;

    public ExpenseClaimController(ExpenseClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping
    public ResponseEntity<ExpenseClaimResponse> create(@Valid @RequestBody ExpenseClaimRequest request) {
        return ResponseEntity.ok(claimService.createClaim(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ExpenseClaimResponse> update(
            @PathVariable Long id, @Valid @RequestBody ExpenseClaimRequest request) {
        return ResponseEntity.ok(claimService.updateClaim(id, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExpenseClaimResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(claimService.getClaim(id));
    }

    @GetMapping
    public ResponseEntity<PageResponse<ExpenseClaimResponse>> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(claimService.listClaims(page, size));
    }

    @PostMapping("/{id}/transition")
    public ResponseEntity<ExpenseClaimResponse> transition(
            @PathVariable Long id, @Valid @RequestBody ClaimTransitionRequest request) {
        return ResponseEntity.ok(claimService.transition(id, request));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<StatusHistoryResponse>> history(@PathVariable Long id) {
        return ResponseEntity.ok(claimService.history(id));
    }
}
