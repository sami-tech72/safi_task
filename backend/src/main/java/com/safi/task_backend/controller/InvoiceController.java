package com.safi.task_backend.controller;

import com.safi.task_backend.dto.InvoicePdfData;
import com.safi.task_backend.dto.InvoiceResponse;
import com.safi.task_backend.dto.PageResponse;
import com.safi.task_backend.service.InvoiceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<InvoiceResponse>> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(invoiceService.list(page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(invoiceService.getInvoice(id));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<InvoiceResponse> approve(@PathVariable Long id) {
        return ResponseEntity.ok(invoiceService.toResponse(invoiceService.approve(id)));
    }

    @GetMapping("/{id}/pdf-data")
    public ResponseEntity<InvoicePdfData> pdf(@PathVariable Long id) {
        return ResponseEntity.ok(invoiceService.getPdfData(id));
    }
}
