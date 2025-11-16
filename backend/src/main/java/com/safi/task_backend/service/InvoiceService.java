package com.safi.task_backend.service;

import com.safi.task_backend.dto.InvoicePdfData;
import com.safi.task_backend.dto.InvoiceResponse;
import com.safi.task_backend.dto.PageResponse;
import com.safi.task_backend.model.ExpenseClaim;
import com.safi.task_backend.model.Invoice;
import com.safi.task_backend.model.InvoiceItem;
import com.safi.task_backend.model.enums.InvoiceStatus;
import com.safi.task_backend.repository.InvoiceRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class InvoiceService {

    private static final String INLINE_PIXEL =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGMAAQAABQABDQottAAAAABJRU5ErkJggg==";

    private final InvoiceRepository invoiceRepository;
    private final StockService stockService;

    private final BigDecimal taxRate;
    private final String headerImage;
    private final String footerImage;

    public InvoiceService(
            InvoiceRepository invoiceRepository,
            StockService stockService,
            @Value("${invoice.pdf.tax-rate:0.1}") BigDecimal taxRate) {
        this.invoiceRepository = invoiceRepository;
        this.stockService = stockService;
        this.taxRate = taxRate;
        this.headerImage = INLINE_PIXEL;
        this.footerImage = INLINE_PIXEL;
    }

    @Transactional
    public Invoice createFromClaim(ExpenseClaim claim) {
        return invoiceRepository
                .findByClaimId(claim.getId())
                .orElseGet(() -> buildInvoice(claim));
    }

    private Invoice buildInvoice(ExpenseClaim claim) {
        Invoice invoice = new Invoice();
        invoice.setClaim(claim);
        invoice.setInvoiceNumber(com.safi.task_backend.util.ReferenceGenerator.invoiceReference());
        invoice.setCreatedAt(LocalDateTime.now());
        invoice.setUpdatedAt(LocalDateTime.now());
        List<InvoiceItem> items = claim.getItems().stream().map(item -> {
            InvoiceItem invoiceItem = new InvoiceItem();
            invoiceItem.setItemName(item.getItemName());
            invoiceItem.setQuantity(item.getQuantity());
            invoiceItem.setUnitPrice(item.getUnitPrice());
            invoiceItem.setLineTotal(item.getLineTotal());
            invoiceItem.setInvoice(invoice);
            return invoiceItem;
        }).collect(Collectors.toList());
        invoice.getItems().addAll(items);
        BigDecimal subtotal = items.stream()
                .map(InvoiceItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tax = subtotal.multiply(taxRate);
        invoice.setSubtotal(subtotal);
        invoice.setTax(tax);
        invoice.setTotal(subtotal.add(tax));
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public Invoice approve(Long invoiceId) {
        Invoice invoice = invoiceRepository
                .findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));
        invoice.setStatus(InvoiceStatus.APPROVED);
        invoice.setApprovedAt(LocalDateTime.now());
        invoice.setUpdatedAt(LocalDateTime.now());
        if (!invoice.isStockApplied()) {
            stockService.applyInvoice(invoice.getItems());
            invoice.setStockApplied(true);
        }
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public void removeInvoice(Long invoiceId) {
        invoiceRepository.findById(invoiceId).ifPresent(invoice -> {
            if (invoice.isStockApplied()) {
                stockService.revertInvoice(invoice.getItems());
            }
            invoiceRepository.delete(invoice);
        });
    }

    public PageResponse<InvoiceResponse> list(int page, int size) {
        Page<Invoice> invoices = invoiceRepository.findAll(PageRequest.of(page, size));
        List<InvoiceResponse> content = invoices.stream().map(this::toResponse).toList();
        return new PageResponse<>(content, invoices.getTotalElements(), invoices.getTotalPages(), page, size);
    }

    public InvoiceResponse getInvoice(Long id) {
        return toResponse(
                invoiceRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Invoice not found")));
    }

    public InvoiceResponse toResponse(Invoice invoice) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getClaim() != null ? invoice.getClaim().getId() : null,
                invoice.getStatus(),
                invoice.getCreatedAt(),
                invoice.getApprovedAt(),
                invoice.getSubtotal(),
                invoice.getTax(),
                invoice.getTotal(),
                invoice.isStockApplied(),
                invoice.getItems().stream()
                        .map(item -> new InvoiceResponse.InvoiceRow(
                                item.getItemName(), item.getQuantity(), item.getUnitPrice(), item.getLineTotal()))
                        .toList());
    }

    public InvoicePdfData getPdfData(Long invoiceId) {
        Invoice invoice = invoiceRepository
                .findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));
        List<InvoicePdfData.InvoiceLine> lines = invoice.getItems().stream()
                .map(item -> new InvoicePdfData.InvoiceLine(
                        item.getItemName(), item.getQuantity(), item.getUnitPrice(), item.getLineTotal()))
                .toList();
        return new InvoicePdfData(
                invoice.getInvoiceNumber(),
                invoice.getCreatedAt().toLocalDate(),
                invoice.getClaim() != null ? invoice.getClaim().getClaimantName() : "",
                invoice.getClaim() != null ? invoice.getClaim().getReferenceNumber() : "",
                lines,
                invoice.getSubtotal(),
                invoice.getTax(),
                invoice.getTotal(),
                invoice.getStatus() == InvoiceStatus.APPROVED,
                headerImage,
                footerImage);
    }
}
