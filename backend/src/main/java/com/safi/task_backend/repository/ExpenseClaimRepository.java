package com.safi.task_backend.repository;

import com.safi.task_backend.model.ExpenseClaim;
import com.safi.task_backend.model.enums.ClaimStatus;
import java.math.BigDecimal;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ExpenseClaimRepository extends JpaRepository<ExpenseClaim, Long> {
    long countByStatusIn(Collection<ClaimStatus> statuses);

    @Query("select coalesce(sum(c.totalAmount), 0) from ExpenseClaim c")
    BigDecimal sumTotalAmount();
}
