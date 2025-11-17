package com.safi.task_backend.repository;

import com.safi.task_backend.model.Invoice;
import com.safi.task_backend.model.enums.InvoiceStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Optional<Invoice> findByClaimId(Long claimId);

    long countByStatus(InvoiceStatus status);
}
