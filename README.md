# Invoicing & Expense Claims Platform

This repository contains a Spring Boot backend and an Angular standalone frontend for managing expense claims, generating invoices, and tracking stock generated from approved invoices. The goal of this document is to explain the overall project design and the flow of the main tasks so newcomers can get productive quickly.

## High-level architecture

| Layer | Technology | Responsibilities |
| --- | --- | --- |
| Frontend | Angular 17 standalone component + Signals | Single-page UI for entering claims, browsing invoices/stock, orchestrating workflow transitions, generating PDFs through pdfmake. |
| Backend | Spring Boot 3 + JPA/Hibernate + H2/MySQL (configurable) | REST APIs for CRUD operations, workflow enforcement, invoice creation/approval, and stock adjustments. |
| Persistence | Relational database | Persists claims, invoice line items, history, and aggregated stock summaries. |

Key backend packages:
- `controller`: HTTP endpoints for claims, invoices, stock, plus a `RestExceptionHandler` for consistent error responses.
- `service`: Business logic, including the `ClaimWorkflow` state machine, invoice generation, PDF payload creation, and stock aggregation.
- `model` + `repository`: JPA entities and Spring Data repositories for each aggregate root.
- `dto`: Request/response records that shape the API contract between the Angular app and the backend.

The Angular frontend lives in `frontend/src/app`. The `App` component owns the UI state via Angular Signals and Reactive Forms, while `ApiService` centralizes all HTTP calls back to the backend.

## Backend flow

### Expense claim lifecycle

1. **Creation & editing** — `ExpenseClaimController` exposes `POST /api/claims` and `PUT /api/claims/{id}`. Both delegate to `ExpenseClaimService`, which stamps reference numbers, calculates totals from `ClaimItem` rows, and writes `StatusHistory` snapshots for auditability. Only `DRAFT` claims are editable (`updateClaim`).
2. **Listing & retrieval** — `GET /api/claims` returns a paged list (via `PageResponse`) while `GET /api/claims/{id}` returns a single `ExpenseClaimResponse` including allowed transitions derived from the workflow.
3. **State transitions** — `POST /api/claims/{id}/transition` calls `ExpenseClaimService.transition`, which validates the requested move using `ClaimWorkflow`. Backward transitions restore prior snapshots and, if needed, delete invoices (reverting stock) via `InvoiceService.removeInvoice`. Forward moves to `INVOICED` auto-create or reuse an invoice and log a history entry.
4. **History** — `GET /api/claims/{id}/history` streams chronological `StatusHistoryResponse` entries so the UI can show an audit log.

### ClaimWorkflow

`ClaimWorkflow` defines the finite-state machine for claims:
- `DRAFT → SUBMITTED`
- `SUBMITTED → UNDER_REVIEW` or back to `DRAFT`
- `UNDER_REVIEW → APPROVED` or back to `SUBMITTED`
- `APPROVED → INVOICED` or back to `UNDER_REVIEW`
- `INVOICED → APPROVED` (only backwards)

This table drives the frontend's transition dropdown and backend validation.

### Invoice + stock management

- **Creation** — When a claim transitions to `INVOICED`, `InvoiceService.createFromClaim` copies claim items into `InvoiceItem` rows, computes tax totals, and persists an `Invoice` tied to the claim. Existing invoices are reused to avoid duplicates.
- **Approval** — `POST /api/invoices/{id}/approve` marks the invoice as `APPROVED`, timestamps it, and calls `StockService.applyInvoice` once per invoice to increment the `StockSummary` totals per item.
- **Reverting** — If a claim moves backwards from `INVOICED`, the invoice is removed and `StockService.revertInvoice` decrements the aggregated stock counts so data stays consistent.
- **Listing/PDF** — `InvoiceController` provides `GET /api/invoices` for pagination, `GET /api/invoices/{id}` for details, and `GET /api/invoices/{id}/pdf-data` so the Angular client can render branded PDFs entirely client-side.

- **Stock endpoint** — `StockController` exposes a read-only `GET /api/stock` that flattens `StockSummary` entities into DTOs consumed by the dashboard.

## Frontend flow

The Angular UI is intentionally compact so each task is visible on a single page (`frontend/src/app/app.html`). The `App` component (`app.ts`) handles all orchestration:

1. **Claim form** — A `FormGroup` collects claimant details and a dynamic `FormArray` of line items. On submit it calls `ApiService.createClaim`, resets the form, and reloads the paginated claims list.
2. **Claims table** — On init the component loads `PageResponse<ExpenseClaim>` via `ApiService.listClaims`. Selecting a row populates the detail pane, fetches status history, and seeds the transition form with the first allowed status.
3. **Transition form** — Submitting calls `ApiService.transitionClaim`, updates the selected claim, refreshes claims/invoices/stock, and reloads history so the UI stays in sync with backend workflow enforcement.
4. **Invoices panel** — Uses `ApiService.listInvoices` plus pagination controls. Each row offers Approve (calls `approveInvoice`) and PDF download (calls `getInvoicePdfData` then uses pdfmake to build a document with header/footer images and totals).
5. **Stock panel** — Pulls aggregated `StockSummary` entries once on load and again after invoice approvals or workflow regressions.

Signals (`signal()`) hold the latest page data, selected claim, submission flags, etc., providing fine-grained change detection without NgRx. All HTTP calls reside in `ApiService` to keep the component focused on UI behavior.

## Typical task flow

1. **Create a claim** in the "Create an Expense Claim" form and save it.
2. **Submit and review** by selecting the new claim, choosing the next status from the dropdown, and applying transitions until it reaches `APPROVED`.
3. **Generate an invoice** by transitioning to `INVOICED`; the backend emits an invoice tied to the claim automatically.
4. **Approve invoice** from the invoices table. Approval both stamps the invoice and increments stock quantities per item.
5. **Download invoice PDF** if needed, or revert the claim back to a previous status, which removes the invoice and rolls back stock totals.
6. **Monitor stock** in the "Current Stock" panel to confirm that approvals (or reversions) changed aggregate counts.

## Local development

1. **Backend**
   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```
   This starts the REST API on `http://localhost:8080`.

2. **Frontend**
   ```bash
   cd frontend
   npm install
   npm run start
   ```
   Navigate to `http://localhost:4200/` to interact with the UI. The app expects the backend to be available on port 8080 (configurable by editing `ApiService.baseUrl`).

With this overview you can trace every task from the form submission through backend workflow enforcement, invoice creation, and stock aggregation.
