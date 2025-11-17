import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export type ClaimStatus = 'DRAFT' | 'SUBMITTED' | 'UNDER_REVIEW' | 'APPROVED' | 'INVOICED';

export interface ClaimItem {
  id?: number;
  itemName: string;
  quantity: number;
  unitPrice: number;
}

export interface ExpenseClaim {
  id: number;
  referenceNumber: string;
  claimantName: string;
  description: string;
  status: ClaimStatus;
  totalAmount: number;
  createdAt: string;
  updatedAt: string;
  items: ClaimItem[];
  allowedTransitions: ClaimStatus[];
  invoiceId?: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface StatusHistoryEntry {
  id: number;
  fromStatus: ClaimStatus;
  toStatus: ClaimStatus;
  comment: string;
  createdAt: string;
}

export interface InvoiceRow {
  itemName: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface InvoiceModel {
  id: number;
  invoiceNumber: string;
  claimId: number;
  status: 'DRAFT' | 'APPROVED';
  createdAt: string;
  approvedAt?: string;
  subtotal: number;
  tax: number;
  total: number;
  stockApplied: boolean;
  items: InvoiceRow[];
}

export interface InvoicePdfData {
  invoiceNumber: string;
  invoiceDate: string;
  claimantName: string;
  claimReference: string;
  items: InvoiceRow[];
  subtotal: number;
  tax: number;
  total: number;
  managerApproved: boolean;
  headerImage: string;
  footerImage: string;
}

export interface StockSummary {
  id: number;
  itemName: string;
  totalQuantity: number;
}

export interface DashboardMetrics {
  totalClaims: number;
  pendingClaims: number;
  totalClaimValue: number;
  invoicesAwaitingApproval: number;
  invoiceApprovalRate: number;
  stockTracked: number;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = 'http://localhost:8080/api';

  createClaim(payload: { claimantName: string; description: string; items: ClaimItem[] }): Observable<ExpenseClaim> {
    return this.http.post<ExpenseClaim>(`${this.baseUrl}/claims`, payload);
  }

  listClaims(page: number, size: number): Observable<PageResponse<ExpenseClaim>> {
    return this.http.get<PageResponse<ExpenseClaim>>(`${this.baseUrl}/claims`, {
      params: { page, size }
    });
  }

  transitionClaim(
    claimId: number,
    payload: { targetStatus: ClaimStatus; comment: string }
  ): Observable<ExpenseClaim> {
    return this.http.post<ExpenseClaim>(`${this.baseUrl}/claims/${claimId}/transition`, payload);
  }

  getHistory(claimId: number): Observable<StatusHistoryEntry[]> {
    return this.http.get<StatusHistoryEntry[]>(`${this.baseUrl}/claims/${claimId}/history`);
  }

  listInvoices(page: number, size: number): Observable<PageResponse<InvoiceModel>> {
    return this.http.get<PageResponse<InvoiceModel>>(`${this.baseUrl}/invoices`, {
      params: { page, size }
    });
  }

  approveInvoice(invoiceId: number): Observable<InvoiceModel> {
    return this.http.post<InvoiceModel>(`${this.baseUrl}/invoices/${invoiceId}/approve`, {});
  }

  getInvoicePdfData(invoiceId: number): Observable<InvoicePdfData> {
    return this.http.get<InvoicePdfData>(`${this.baseUrl}/invoices/${invoiceId}/pdf-data`);
  }

  getStock(): Observable<StockSummary[]> {
    return this.http.get<StockSummary[]>(`${this.baseUrl}/stock`);
  }

  getDashboardMetrics(): Observable<DashboardMetrics> {
    return this.http.get<DashboardMetrics>(`${this.baseUrl}/dashboard/metrics`);
  }
}
