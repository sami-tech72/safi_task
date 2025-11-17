import { CommonModule, DatePipe, NgFor, NgIf } from '@angular/common';
import { Component, OnInit, WritableSignal, inject, signal } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import pdfMake from '../pdfmake/build/pdfmake';
import pdfFonts from '../pdfmake/build/vfs_fonts';

const headerUrl = 'https://via.placeholder.com/600x100.png?text=Letterhead';
const footerUrl = 'https://via.placeholder.com/600x80.png?text=Footer';
import {
  ApiService,
  ClaimItem,
  ClaimStatus,
  DashboardMetrics,
  ExpenseClaim,
  InvoiceModel,
  InvoicePdfData,
  PageResponse,
  StatusHistoryEntry,
  StockSummary
} from './services/api.service';

(pdfMake as any).vfs = pdfFonts.pdfMake.vfs;

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, NgIf, NgFor, ReactiveFormsModule, DatePipe],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(ApiService);

  readonly claimForm = this.fb.group({
    claimantName: ['', Validators.required],
    description: [''],
    items: this.fb.array([this.createItemGroup()])
  });

  readonly transitionForm = this.fb.group({
    targetStatus: ['', Validators.required],
    comment: ['', Validators.required]
  });

  readonly claimsPage: WritableSignal<PageResponse<ExpenseClaim> | null> = signal(null);
  readonly invoicesPage: WritableSignal<PageResponse<InvoiceModel> | null> = signal(null);
  readonly stockEntries: WritableSignal<StockSummary[]> = signal([]);
  readonly dashboardMetrics = signal<DashboardMetrics | null>(null);
  readonly selectedClaim = signal<ExpenseClaim | null>(null);
  readonly claimHistory = signal<StatusHistoryEntry[]>([]);
  readonly isSubmitting = signal(false);
  readonly transitionSubmitting = signal(false);

  private claimPageIndex = signal(0);
  private invoicePageIndex = signal(0);
  readonly pageSize = 5;

  ngOnInit(): void {
    this.loadClaims();
    this.loadInvoices();
    this.loadStock();
    this.loadMetrics();
  }

  claimCount(): number {
    const metrics = this.dashboardMetrics();
    if (metrics) {
      return metrics.totalClaims;
    }
    return this.claimsPage()?.totalElements ?? 0;
  }

  pendingClaimsCount(): number {
    const metrics = this.dashboardMetrics();
    if (metrics) {
      return metrics.pendingClaims;
    }
    const page = this.claimsPage();
    if (!page) {
      return 0;
    }
    return page.content.filter(claim => claim.status !== 'APPROVED' && claim.status !== 'INVOICED').length;
  }

  claimValueTotal(): number {
    const metrics = this.dashboardMetrics();
    if (metrics) {
      return metrics.totalClaimValue;
    }
    const page = this.claimsPage();
    return page ? page.content.reduce((sum, claim) => sum + claim.totalAmount, 0) : 0;
  }

  invoicesAwaitingApproval(): number {
    const metrics = this.dashboardMetrics();
    if (metrics) {
      return metrics.invoicesAwaitingApproval;
    }
    const invoices = this.invoicesPage();
    return invoices ? invoices.content.filter(invoice => invoice.status !== 'APPROVED').length : 0;
  }

  invoiceApprovalRate(): number {
    const metrics = this.dashboardMetrics();
    if (metrics) {
      return metrics.invoiceApprovalRate;
    }
    const invoices = this.invoicesPage();
    if (!invoices || invoices.content.length === 0) {
      return 0;
    }
    const approved = invoices.content.filter(invoice => invoice.status === 'APPROVED').length;
    return Math.round((approved / invoices.content.length) * 100);
  }

  stockTracked(): number {
    const metrics = this.dashboardMetrics();
    if (metrics) {
      return metrics.stockTracked;
    }
    return this.stockEntries().length;
  }

  statusTone(status: string): string {
    const normalized = (status ?? '').toUpperCase();
    if (normalized.includes('APPROVED') || normalized.includes('PAID')) {
      return 'status-chip--success';
    }
    if (normalized.includes('REJECT')) {
      return 'status-chip--danger';
    }
    if (normalized.includes('DRAFT')) {
      return 'status-chip--muted';
    }
    return 'status-chip--warning';
  }

  get itemsArray(): FormArray {
    return this.claimForm.get('items') as FormArray;
  }

  addItem(): void {
    this.itemsArray.push(this.createItemGroup());
  }

  removeItem(index: number): void {
    if (this.itemsArray.length > 1) {
      this.itemsArray.removeAt(index);
    }
  }

  private createItemGroup(): FormGroup {
    return this.fb.group({
      itemName: ['', Validators.required],
      quantity: [1, [Validators.required, Validators.min(1)]],
      unitPrice: [0, [Validators.required, Validators.min(0)]]
    });
  }

  submitClaim(): void {
    if (this.claimForm.invalid) {
      this.claimForm.markAllAsTouched();
      return;
    }
    this.isSubmitting.set(true);
    const payload = {
      claimantName: this.claimForm.value.claimantName ?? '',
      description: this.claimForm.value.description ?? '',
      items: this.mapFormItems()
    };
    this.api.createClaim(payload).subscribe({
      next: () => {
        this.isSubmitting.set(false);
        this.claimForm.reset({ claimantName: '', description: '' });
        this.itemsArray.clear();
        this.addItem();
        this.loadClaims();
        this.loadMetrics();
      },
      error: () => this.isSubmitting.set(false)
    });
  }

  private mapFormItems(): ClaimItem[] {
    return this.itemsArray.controls.map(control => ({
      itemName: control.value.itemName ?? '',
      quantity: Number(control.value.quantity ?? 0),
      unitPrice: Number(control.value.unitPrice ?? 0)
    }));
  }

  loadClaims(page = this.claimPageIndex()): void {
    this.api.listClaims(page, this.pageSize).subscribe(pageData => {
      this.claimsPage.set(pageData);
      this.claimPageIndex.set(pageData.page);
    });
  }

  loadInvoices(page = this.invoicePageIndex()): void {
    this.api.listInvoices(page, this.pageSize).subscribe(pageData => {
      this.invoicesPage.set(pageData);
      this.invoicePageIndex.set(pageData.page);
    });
  }

  loadStock(): void {
    this.api.getStock().subscribe(entries => this.stockEntries.set(entries));
  }

  loadMetrics(): void {
    this.api.getDashboardMetrics().subscribe(data => this.dashboardMetrics.set(data));
  }

  selectClaim(claim: ExpenseClaim): void {
    this.selectedClaim.set(claim);
    const options = this.transitionsFor(claim);
    const next = options[0] ?? claim.status;
    this.transitionForm.patchValue({ targetStatus: next, comment: '' });
    this.api.getHistory(claim.id).subscribe(history => this.claimHistory.set(history));
  }

  performTransition(): void {
    const claim = this.selectedClaim();
    if (!claim || this.transitionForm.invalid) {
      this.transitionForm.markAllAsTouched();
      return;
    }
    this.transitionSubmitting.set(true);
    const { targetStatus, comment } = this.transitionForm.value as { targetStatus: ClaimStatus; comment: string };
    this.api.transitionClaim(claim.id, { targetStatus, comment }).subscribe({
      next: updated => {
        this.transitionSubmitting.set(false);
        this.selectedClaim.set(updated);
        const next = this.transitionsFor(updated)[0] ?? updated.status;
        this.transitionForm.patchValue({ targetStatus: next, comment: '' });
        this.loadClaims(this.claimPageIndex());
        this.loadInvoices(this.invoicePageIndex());
        this.loadStock();
        this.loadMetrics();
        this.api.getHistory(updated.id).subscribe(history => this.claimHistory.set(history));
      },
      error: () => this.transitionSubmitting.set(false)
    });
  }

  approveInvoice(invoice: InvoiceModel): void {
    this.api.approveInvoice(invoice.id).subscribe(updated => {
      this.loadInvoices(this.invoicePageIndex());
      this.loadStock();
      this.loadMetrics();
      if (this.selectedClaim() && this.selectedClaim()!.invoiceId === updated.id) {
        this.selectedClaim.set({ ...this.selectedClaim()!, invoiceId: updated.id });
      }
    });
  }

  downloadPdf(invoice: InvoiceModel): void {
    this.api.getInvoicePdfData(invoice.id).subscribe(data => this.buildPdf(data));
  }

  private buildPdf(data: InvoicePdfData): void {
    const headerImage = headerUrl || (data.headerImage ? `data:image/png;base64,${data.headerImage}` : undefined);
    const footerImage = footerUrl || (data.footerImage ? `data:image/png;base64,${data.footerImage}` : undefined);
    const tableBody = [
      ['Item', 'Qty', 'Price', 'Line Total'],
      ...data.items.map(item => [
        item.itemName,
        item.quantity,
        this.currency(item.unitPrice),
        this.currency(item.lineTotal)
      ])
    ];
    const totals = [
      { label: 'Subtotal', value: this.currency(data.subtotal) },
      { label: 'Tax', value: this.currency(data.tax) },
      { label: 'Total', value: this.currency(data.total) }
    ];
    const content: any[] = [
      { text: `Invoice ${data.invoiceNumber}`, style: 'title' },
      {
        columns: [
          { text: `Date: ${data.invoiceDate}`, width: '50%' },
          { text: `Claim Reference: ${data.claimReference}`, alignment: 'right', width: '50%' }
        ],
        margin: [0, 10, 0, 10]
      },
      { text: `Payee: ${data.claimantName}`, margin: [0, 0, 0, 10] },
      {
        table: {
          headerRows: 1,
          widths: ['*', 60, 80, 100],
          body: tableBody
        }
      },
      {
        columns: totals.map(item => ({ text: `${item.label}: ${item.value}`, alignment: 'right' })),
        margin: [0, 15, 0, 0]
      }
    ];
    if (data.managerApproved) {
      content.push({ text: 'Approved by Manager', style: 'approved', margin: [0, 20, 0, 0] });
    }
    const docDefinition = {
      pageMargins: [40, 120, 40, 120],
      header: {
        image: headerImage,
        alignment: 'center',
        margin: [0, 40, 0, 0],
        fit: [400, 80]
      },
      footer: {
        columns: [
          {
            image: footerImage,
            alignment: 'center',
            fit: [400, 60]
          }
        ],
        margin: [0, 0, 0, 40]
      },
      content,
      styles: {
        title: { fontSize: 20, bold: true },
        approved: { color: '#2e7d32', bold: true }
      }
    };
    pdfMake.createPdf(docDefinition).download(`invoice-${data.invoiceNumber}.pdf`);
  }

  private currency(value: number): string {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(value ?? 0);
  }

  goToPreviousClaims(): void {
    const page = Math.max((this.claimsPage()?.page ?? 0) - 1, 0);
    this.loadClaims(page);
  }

  goToNextClaims(): void {
    const total = this.claimsPage()?.totalPages ?? 1;
    const next = Math.min((this.claimsPage()?.page ?? 0) + 1, total - 1);
    this.loadClaims(next);
  }

  goToPreviousInvoices(): void {
    const page = Math.max((this.invoicesPage()?.page ?? 0) - 1, 0);
    this.loadInvoices(page);
  }

  goToNextInvoices(): void {
    const total = this.invoicesPage()?.totalPages ?? 1;
    const next = Math.min((this.invoicesPage()?.page ?? 0) + 1, total - 1);
    this.loadInvoices(next);
  }

  transitionsFor(claim: ExpenseClaim): ClaimStatus[] {
    return claim.allowedTransitions.filter(status => status !== claim.status);
  }
}
