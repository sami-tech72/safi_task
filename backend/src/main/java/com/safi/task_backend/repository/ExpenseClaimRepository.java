package com.safi.task_backend.repository;

import com.safi.task_backend.model.ExpenseClaim;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseClaimRepository extends JpaRepository<ExpenseClaim, Long> {
}
