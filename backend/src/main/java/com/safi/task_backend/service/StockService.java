package com.safi.task_backend.service;

import com.safi.task_backend.model.InvoiceItem;
import com.safi.task_backend.model.StockSummary;
import com.safi.task_backend.repository.StockSummaryRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StockService {

    private final StockSummaryRepository stockSummaryRepository;

    public StockService(StockSummaryRepository stockSummaryRepository) {
        this.stockSummaryRepository = stockSummaryRepository;
    }

    @Transactional
    public void applyInvoice(List<InvoiceItem> items) {
        for (InvoiceItem item : items) {
            stockSummaryRepository
                    .findByItemNameIgnoreCase(item.getItemName())
                    .ifPresentOrElse(
                            summary -> {
                                summary.setTotalQuantity(summary.getTotalQuantity() + item.getQuantity());
                                stockSummaryRepository.save(summary);
                            },
                            () -> {
                                StockSummary summary = new StockSummary();
                                summary.setItemName(item.getItemName());
                                summary.setTotalQuantity(item.getQuantity());
                                stockSummaryRepository.save(summary);
                            });
        }
    }

    @Transactional
    public void revertInvoice(List<InvoiceItem> items) {
        for (InvoiceItem item : items) {
            stockSummaryRepository
                    .findByItemNameIgnoreCase(item.getItemName())
                    .ifPresent(summary -> {
                        summary.setTotalQuantity(summary.getTotalQuantity() - item.getQuantity());
                        stockSummaryRepository.save(summary);
                    });
        }
    }
}
