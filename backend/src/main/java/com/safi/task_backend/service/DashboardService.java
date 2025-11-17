package com.safi.task_backend.service;

import com.safi.task_backend.dto.DashboardMetrics;
import com.safi.task_backend.model.enums.ClaimStatus;
import com.safi.task_backend.model.enums.InvoiceStatus;
import com.safi.task_backend.repository.ExpenseClaimRepository;
import com.safi.task_backend.repository.InvoiceRepository;
import com.safi.task_backend.repository.StockSummaryRepository;
import java.math.BigDecimal;
import java.util.EnumSet;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private static final EnumSet<ClaimStatus> PENDING_STATUSES =
            EnumSet.of(ClaimStatus.DRAFT, ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW);

    private final ExpenseClaimRepository claimRepository;
    private final InvoiceRepository invoiceRepository;
    private final StockSummaryRepository stockSummaryRepository;

    public DashboardService(
            ExpenseClaimRepository claimRepository,
            InvoiceRepository invoiceRepository,
            StockSummaryRepository stockSummaryRepository) {
        this.claimRepository = claimRepository;
        this.invoiceRepository = invoiceRepository;
        this.stockSummaryRepository = stockSummaryRepository;
    }

    public DashboardMetrics getMetrics() {
        long totalClaims = claimRepository.count();
        long pendingClaims = claimRepository.countByStatusIn(PENDING_STATUSES);
        BigDecimal totalClaimValue = claimRepository.sumTotalAmount();
        if (totalClaimValue == null) {
            totalClaimValue = BigDecimal.ZERO;
        }

        long invoicesAwaitingApproval = invoiceRepository.countByStatus(InvoiceStatus.DRAFT);
        long approvedInvoices = invoiceRepository.countByStatus(InvoiceStatus.APPROVED);
        long invoiceTotal = invoicesAwaitingApproval + approvedInvoices;
        int approvalRate = invoiceTotal == 0 ? 0 : (int) Math.round((approvedInvoices * 100.0) / invoiceTotal);

        long stockTracked = stockSummaryRepository.count();

        return new DashboardMetrics(
                totalClaims,
                pendingClaims,
                totalClaimValue,
                invoicesAwaitingApproval,
                approvalRate,
                stockTracked);
    }
}
