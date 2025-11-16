package com.safi.task_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safi.task_backend.dto.ClaimItemDto;
import com.safi.task_backend.dto.ClaimTransitionRequest;
import com.safi.task_backend.dto.ExpenseClaimRequest;
import com.safi.task_backend.dto.ExpenseClaimResponse;
import com.safi.task_backend.dto.PageResponse;
import com.safi.task_backend.dto.StatusHistoryResponse;
import com.safi.task_backend.model.ClaimItem;
import com.safi.task_backend.model.ExpenseClaim;
import com.safi.task_backend.model.Invoice;
import com.safi.task_backend.model.StatusHistory;
import com.safi.task_backend.model.enums.ClaimStatus;
import com.safi.task_backend.repository.ExpenseClaimRepository;
import com.safi.task_backend.repository.StatusHistoryRepository;
import com.safi.task_backend.service.mapper.ClaimSnapshot;
import com.safi.task_backend.service.mapper.ClaimSnapshotMapper;
import com.safi.task_backend.util.ReferenceGenerator;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class ExpenseClaimService {

    private final ExpenseClaimRepository claimRepository;
    private final StatusHistoryRepository historyRepository;
    private final ClaimWorkflow workflow;
    private final InvoiceService invoiceService;
    private final ObjectMapper objectMapper;

    public ExpenseClaimService(
            ExpenseClaimRepository claimRepository,
            StatusHistoryRepository historyRepository,
            ClaimWorkflow workflow,
            InvoiceService invoiceService,
            ObjectMapper objectMapper) {
        this.claimRepository = claimRepository;
        this.historyRepository = historyRepository;
        this.workflow = workflow;
        this.invoiceService = invoiceService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ExpenseClaimResponse createClaim(ExpenseClaimRequest request) {
        ExpenseClaim claim = new ExpenseClaim();
        claim.setReferenceNumber(ReferenceGenerator.claimReference());
        claim.setClaimantName(request.claimantName());
        claim.setDescription(request.description());
        claim.setCreatedAt(LocalDateTime.now());
        claim.setUpdatedAt(LocalDateTime.now());
        applyItems(claim, request.items());
        claimRepository.save(claim);
        recordHistory(claim, ClaimStatus.DRAFT, ClaimStatus.DRAFT, "Claim created");
        return mapToResponse(claim);
    }

    @Transactional
    public ExpenseClaimResponse updateClaim(Long id, ExpenseClaimRequest request) {
        ExpenseClaim claim = getClaimEntity(id);
        if (claim.getStatus() != ClaimStatus.DRAFT) {
            throw new IllegalStateException("Only draft claims can be edited");
        }
        claim.setClaimantName(request.claimantName());
        claim.setDescription(request.description());
        applyItems(claim, request.items());
        claim.setUpdatedAt(LocalDateTime.now());
        claimRepository.save(claim);
        recordHistory(claim, ClaimStatus.DRAFT, ClaimStatus.DRAFT, "Draft updated");
        return mapToResponse(claim);
    }

    public ExpenseClaimResponse getClaim(Long id) {
        return mapToResponse(getClaimEntity(id));
    }

    public PageResponse<ExpenseClaimResponse> listClaims(int page, int size) {
        Page<ExpenseClaim> claims = claimRepository.findAll(PageRequest.of(page, size));
        List<ExpenseClaimResponse> content = claims.stream().map(this::mapToResponse).toList();
        return new PageResponse<>(content, claims.getTotalElements(), claims.getTotalPages(), page, size);
    }

    @Transactional
    public ExpenseClaimResponse transition(Long id, ClaimTransitionRequest request) {
        ExpenseClaim claim = getClaimEntity(id);
        ClaimStatus current = claim.getStatus();
        ClaimStatus target = request.targetStatus();
        if (!workflow.isTransitionAllowed(current, target)) {
            throw new IllegalStateException("Transition not allowed");
        }
        if (workflow.isBackward(current, target)) {
            historyRepository
                    .findTopByClaimIdAndToStatusOrderByCreatedAtDesc(claim.getId(), target)
                    .ifPresent(history -> {
                        try {
                            ClaimSnapshot snapshot = objectMapper.readValue(history.getSnapshot(), ClaimSnapshot.class);
                            ClaimSnapshotMapper.applySnapshot(claim, snapshot);
                        } catch (JsonProcessingException e) {
                            throw new IllegalStateException("Could not restore snapshot", e);
                        }
                    });
            if (current == ClaimStatus.INVOICED && claim.getInvoice() != null) {
                Long invoiceId = claim.getInvoice().getId();
                claim.setInvoice(null);
                invoiceService.removeInvoice(invoiceId);
            }
        }
        claim.setStatus(target);
        claim.setUpdatedAt(LocalDateTime.now());
        claimRepository.save(claim);
        if (target == ClaimStatus.INVOICED) {
            Invoice invoice = invoiceService.createFromClaim(claim);
            claim.setInvoice(invoice);
            claimRepository.save(claim);
        }
        recordHistory(claim, current, target, request.comment());
        return mapToResponse(claim);
    }

    public List<StatusHistoryResponse> history(Long id) {
        return historyRepository.findByClaimIdOrderByCreatedAtAsc(id).stream()
                .map(entry -> new StatusHistoryResponse(
                        entry.getId(), entry.getFromStatus(), entry.getToStatus(), entry.getComment(), entry.getCreatedAt()))
                .toList();
    }

    private void recordHistory(ExpenseClaim claim, ClaimStatus from, ClaimStatus to, String comment) {
        StatusHistory history = new StatusHistory();
        history.setClaim(claim);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setComment(comment);
        history.setCreatedAt(LocalDateTime.now());
        try {
            history.setSnapshot(objectMapper.writeValueAsString(ClaimSnapshot.fromClaim(claim)));
        } catch (JsonProcessingException e) {
            history.setSnapshot("{}");
        }
        historyRepository.save(history);
    }

    private void applyItems(ExpenseClaim claim, List<ClaimItemDto> items) {
        claim.getItems().clear();
        if (items != null) {
            for (ClaimItemDto dto : items) {
                ClaimItem item = new ClaimItem();
                item.setItemName(dto.itemName());
                item.setQuantity(dto.quantity());
                item.setUnitPrice(dto.unitPrice());
                item.setClaim(claim);
                claim.getItems().add(item);
            }
        }
        BigDecimal total = claim.getItems().stream()
                .map(ClaimItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        claim.setTotalAmount(total);
    }

    private ExpenseClaimResponse mapToResponse(ExpenseClaim claim) {
        List<ClaimItemDto> items = claim.getItems().stream()
                .map(item -> new ClaimItemDto(item.getId(), item.getItemName(), item.getQuantity(), item.getUnitPrice()))
                .toList();
        Set<ClaimStatus> allowed = workflow.allowedTargets(claim.getStatus());
        Long invoiceId = claim.getInvoice() != null ? claim.getInvoice().getId() : null;
        return new ExpenseClaimResponse(
                claim.getId(),
                claim.getReferenceNumber(),
                claim.getClaimantName(),
                claim.getDescription(),
                claim.getStatus(),
                claim.getTotalAmount(),
                claim.getCreatedAt(),
                claim.getUpdatedAt(),
                items,
                allowed,
                invoiceId);
    }

    private ExpenseClaim getClaimEntity(Long id) {
        return claimRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Claim not found"));
    }
}
