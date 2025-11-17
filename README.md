# Invoicing & Expense Claims Platform

This repository contains a Spring Boot backend and an Angular standalone frontend for managing expense claims, generating invoices, and tracking stock generated from approved invoices. The goal of this document is to explain the overall project design and the flow of the main tasks so newcomers can get productive quickly.

## High-level architecture

| Layer | Technology | Responsibilities |
| --- | --- | --- |
| Frontend | Angular 17 standalone component + Signals | Single-page UI for entering claims, browsing invoices/stock, orchestrating workflow transitions, generating PDFs through pdfmake. |
| Backend | Spring Boot 3 + JPA/Hibernate + H2/MySQL (configurable) | REST APIs for CRUD operations, workflow enforcement, invoice creation/approval, and stock adjustments. |
| Persistence | Relational database | Persists claims, invoice line items, history, and aggregated stock summaries. |

## System design

```text
┌──────────────┐      HTTPS REST       ┌────────────────┐      Service layer      ┌────────────────────┐      ORM        ┌───────────────┐
│ Angular SPA  │ ───────────────────▶ │ Controllers     │ ─────────────────────▶ │ Expense/Invoice    │ ─────────────▶ │ Spring Data   │
│ (signals +   │ ◀─────────────────── │ (claims,        │ ◀──────────────────── │ services +         │ ◀──────────── │ repositories  │
│ reactive forms│     JSON responses   │ invoices, stock)│    DTO↔entity mapping │ StockService)      │   entity graph│               │
└──────────────┘                       └────────────────┘                         └────────────────────┘               └──────┬────────┘
                                                                                                                JDBC/Hibernate│
                                                                                                                             ▼
                                                                                                                       Relational DB
```

**Design goals**

1. **Tight workflow cohesion** – every status change travels through `ClaimWorkflow`, guaranteeing a single enforcement point for UI dropdowns and backend invariants.
2. **Task-friendly UI** – the Angular shell keeps creation, review, invoicing, and stock tracking visible simultaneously so operators never lose context mid-task.
3. **Reversible operations** – invoices and stock entries are derived artifacts that can be regenerated or rolled back automatically when workflow states regress.

### Backend structure

- **Controller layer** exposes REST endpoints and translates HTTP artifacts to DTOs.
- **Service layer** contains pure business logic. Each service owns one aggregate (Claims, Invoices, Stock) and orchestrates side effects such as history snapshots, invoice generation, and stock deltas.
- **Repository layer** uses Spring Data JPA interfaces; entities remain persistence-agnostic so the same services run against H2 (tests) or MySQL (prod) without code changes.

### Data model snapshot

| Table / Entity | Purpose | Key fields |
| --- | --- | --- |
| `ExpenseClaim` | Parent aggregate for each claim. | `id`, `claimant`, `status`, `totalAmount`, `items`, `history` |
| `ClaimItem` | Line items per claim. | `description`, `qty`, `unitPrice`, `taxRate` |
| `StatusHistory` | Audit log of transitions. | `fromStatus`, `toStatus`, `changedBy`, `changedAt`, `comment` |
| `Invoice` / `InvoiceItem` | Generated bill per approved claim. | `invoiceNumber`, `approvedAt`, `subtotal`, `tax`, `claimId` |
| `StockSummary` | Aggregated stock derived from invoices. | `itemCode`, `quantity`, `lastAdjustedAt`, `sourceInvoiceId` |

Relationships:

- `ExpenseClaim` 1→N `ClaimItem`
- `ExpenseClaim` 1→N `StatusHistory`
- `ExpenseClaim` 1→1 `Invoice` (optional)
- `Invoice` 1→N `InvoiceItem`
- `StockSummary` stores computed totals keyed by item, not a hard FK, making reconciliation idempotent.

### Deployment view

- **Local** – Angular dev server (`npm run start`) talks directly to Spring Boot (`./mvnw spring-boot:run`) over localhost. Hot reload on both sides speeds iteration.
- **Production** – Build artifacts (`ng build`, `mvn package`) are deployed behind a reverse proxy. The SPA is served as static assets, while the API is exposed under `/api/**`. Database connectivity is provided via managed MySQL.
- **Observability hooks** – Spring Boot actuators (if enabled) expose health metrics, while the frontend logs only actionable errors to keep browser consoles clean.


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

## REST API reference

All endpoints are served from the Spring Boot backend under the `/api` prefix (for example `http://localhost:8080/api`). Authentication/authorization is currently out of scope, so the APIs are open for local development.

### Claims endpoints

| Method | Path | Purpose | Request / Response basics |
| --- | --- | --- | --- |
| `POST` | `/claims` | Create a draft expense claim. | Body: `ExpenseClaimRequest` with claimant + items. Response: persisted `ExpenseClaimResponse`. |
| `PUT` | `/claims/{id}` | Update an existing draft claim. | Body: `ExpenseClaimRequest`. Only allowed while status is `DRAFT`. |
| `GET` | `/claims` | List paginated claims ordered by last update. | Query params: `page`, `size`. Response: `PageResponse<ExpenseClaimResponse>`. |
| `GET` | `/claims/{id}` | Fetch a single claim with allowed transitions. | Response includes `allowedTransitions` derived from the workflow. |
| `GET` | `/claims/{id}/history` | Retrieve chronological status changes. | Response: array of `StatusHistoryResponse`. |
| `POST` | `/claims/{id}/transition` | Move a claim to another workflow state. | Body: `{ "targetStatus": "APPROVED", "comment": "optional" }`. Response: updated `ExpenseClaimResponse`. |

### Invoice endpoints

| Method | Path | Purpose | Request / Response basics |
| --- | --- | --- | --- |
| `GET` | `/invoices` | Paginated invoice listing for the dashboard. | Query params mirror claims pagination. Response: `PageResponse<InvoiceResponse>`. |
| `GET` | `/invoices/{id}` | Retrieve invoice header + line items. | Response: `InvoiceResponse` with monetary totals. |
| `POST` | `/invoices/{id}/approve` | Mark an invoice as approved and update stock. | No body. Response: approved `InvoiceResponse`. |
| `GET` | `/invoices/{id}/pdf-data` | JSON payload used by the Angular pdfmake renderer. | Response: `{ header, footer, lineItems, totals }` structure consumed by the frontend. |

### Stock endpoints

| Method | Path | Purpose | Request / Response basics |
| --- | --- | --- | --- |
| `GET` | `/stock` | Read-only snapshot of aggregated quantities per item. | Response: array of `StockSummaryResponse` objects with `itemCode`, `quantity`, and metadata. |

These APIs intentionally keep business rules server-side—the Angular app simply orchestrates calls and renders results.

## Step-by-step API walkthrough (with `curl`)

Use the following numbered flow to exercise every major endpoint from a terminal. Replace placeholder UUIDs/IDs as needed. Each command assumes the backend is listening at `http://localhost:8080`.

1. **Create a draft claim**
   ```bash
   curl -X POST http://localhost:8080/api/claims \
     -H 'Content-Type: application/json' \
     -d '{
           "claimant": "Alex Ops",
           "items": [
             {"description": "Travel", "qty": 2, "unitPrice": 120, "taxRate": 0.1},
             {"description": "Meals", "qty": 5, "unitPrice": 25, "taxRate": 0.05}
           ]
         }'
   ```
   Response: persisted `ExpenseClaimResponse` that includes the generated `id`, computed totals, and `allowedTransitions`.

2. **List claims to confirm creation**
   ```bash
   curl 'http://localhost:8080/api/claims?page=0&size=10'
   ```
   Response: `PageResponse` containing the claim plus pagination metadata.

3. **Update the draft (optional)**
   ```bash
   curl -X PUT http://localhost:8080/api/claims/{claimId} \
     -H 'Content-Type: application/json' \
     -d '{ "claimant": "Alex Ops", "items": [ ...updated lines... ] }'
   ```
   Only works while the claim is still `DRAFT`.

4. **Advance workflow to SUBMITTED / UNDER_REVIEW / APPROVED**
   ```bash
   curl -X POST http://localhost:8080/api/claims/{claimId}/transition \
     -H 'Content-Type: application/json' \
     -d '{ "targetStatus": "SUBMITTED", "comment": "Ready for review" }'
   ```
   Repeat with `targetStatus` values of `UNDER_REVIEW` and `APPROVED`. Each response returns the updated claim.

5. **Generate an invoice by transitioning to INVOICED**
   ```bash
   curl -X POST http://localhost:8080/api/claims/{claimId}/transition \
     -H 'Content-Type: application/json' \
     -d '{ "targetStatus": "INVOICED" }'
   ```
   The backend creates (or refreshes) the related invoice and returns the refreshed claim.

6. **Retrieve invoices (paged)**
   ```bash
   curl 'http://localhost:8080/api/invoices?page=0&size=10'
   ```
   Response: `PageResponse<InvoiceResponse>` including each invoice status and monetary totals.

7. **Approve an invoice and adjust stock**
   ```bash
   curl -X POST http://localhost:8080/api/invoices/{invoiceId}/approve
   ```
   Response: approved `InvoiceResponse`; stock totals are incremented server-side.

8. **Fetch invoice PDF data for the Angular client**
   ```bash
   curl http://localhost:8080/api/invoices/{invoiceId}/pdf-data
   ```
   Response: JSON containing `header`, `footer`, and `lineItems` ready for pdfmake rendering.

9. **Inspect stock levels**
   ```bash
   curl http://localhost:8080/api/stock
   ```
   Response: array of `StockSummaryResponse` rows with `itemCode`, `quantity`, and provenance details.

10. **Audit the claim history (optional)**
    ```bash
    curl http://localhost:8080/api/claims/{claimId}/history
    ```
    Response: chronological list of every transition, user, and comment.

11. **Regress workflow (optional)**
    ```bash
    curl -X POST http://localhost:8080/api/claims/{claimId}/transition \
      -H 'Content-Type: application/json' \
      -d '{ "targetStatus": "APPROVED", "comment": "Need to adjust stock" }'
    ```
    Moving backwards from `INVOICED` automatically deletes the invoice and reverts stock via `StockService`.

Following these steps sequentially mirrors exactly what the Angular frontend automates, making it easy to debug the workflow without leaving the terminal.

## Typical task flow

1. **Create a claim** in the "Create an Expense Claim" form and save it.
2. **Submit and review** by selecting the new claim, choosing the next status from the dropdown, and applying transitions until it reaches `APPROVED`.
3. **Generate an invoice** by transitioning to `INVOICED`; the backend emits an invoice tied to the claim automatically.
4. **Approve invoice** from the invoices table. Approval both stamps the invoice and increments stock quantities per item.
5. **Download invoice PDF** if needed, or revert the claim back to a previous status, which removes the invoice and rolls back stock totals.
6. **Monitor stock** in the "Current Stock" panel to confirm that approvals (or reversions) changed aggregate counts.

## Prerequisites

- Node.js 20+ (for the Angular dev server)
- npm 10+
- JDK 17+
- Maven Wrapper (already included in this repository)

Verify the toolchain:

```bash
node -v
npm -v
java -version
./backend/mvnw -v
```

## Local development (step-by-step)

1. **Clone and inspect the repo**
   ```bash
   git clone <this repo>
   cd safi_task
   ls
   ```
   Confirm that the `backend` (Spring Boot) and `frontend` (Angular) folders are present.

2. **Configure environment variables (optional)**
   - Backend: copy `backend/src/main/resources/application.yml` to `application-local.yml` if you want to override DB credentials or server ports.
   - Frontend: the default API base URL is `http://localhost:8080/api`. Update `frontend/src/app/api.service.ts` if your backend runs elsewhere.

3. **Start the backend API**
   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```
   - Wait until the log prints `Started Application in ... seconds`.
   - The API listens on `http://localhost:8080` with in-memory H2 storage by default.

4. **Start the Angular dev server** (new terminal)
   ```bash
   cd frontend
   npm install
   npm run start
   ```
   - Open `http://localhost:4200/` in the browser.
   - The dev server proxies API calls directly to the Spring Boot backend.

5. **Run backend tests (optional)**
   ```bash
   cd backend
   ./mvnw test
   ```
   This validates the workflow rules, services, and repository mappings.

6. **Run frontend tests (optional)**
   ```bash
   cd frontend
   npm run test
   ```
   Angular's Karma/Jasmine suite ensures the main components and the API service behave as expected.

## End-to-end usage walkthrough

Follow these concrete steps to exercise the entire workflow once both servers are running:

1. **Create a draft claim**
   - In the "Create an Expense Claim" form, fill claimant info and add one or more line items.
   - Click **Save Draft** to persist the claim (`POST /api/claims`).

2. **Submit and review**
   - Select the new claim in the claims table; the detail pane reveals allowed transitions.
   - Choose `SUBMITTED`, click **Apply Transition**, then repeat for `UNDER_REVIEW`.

3. **Approve and invoice**
   - Transition the claim to `APPROVED`, then to `INVOICED`.
   - The backend automatically generates or updates the invoice, which appears in the invoices table.

4. **Approve invoice & adjust stock**
   - Click **Approve** on the invoice row to call `POST /api/invoices/{id}/approve`.
   - Switch to the "Current Stock" panel to see quantities incremented per item.

5. **Regress workflow (optional)**
   - Use the transition form to move the claim back from `INVOICED` to `APPROVED`.
   - Observe that the invoice is deleted and stock counts decrease automatically.

6. **Download PDFs (optional)**
   - Click **PDF** next to an invoice to trigger `GET /api/invoices/{id}/pdf-data`.
   - The Angular client renders the PDF in-browser with pdfmake.

## Troubleshooting tips

| Symptom | Resolution |
| --- | --- |
| `ECONNREFUSED` errors in the browser console | Confirm the backend is running on port 8080 and that `ApiService.baseUrl` matches. |
| `Address already in use` when starting Spring Boot | Another process is on 8080; set `server.port` in `backend/src/main/resources/application.yml` and update the frontend base URL accordingly. |
| H2 data disappears between restarts | Switch to MySQL/PostgreSQL by editing Spring profiles and providing JDBC credentials. |
| npm install fails | Ensure you are using Node.js 20+ and delete `frontend/node_modules` before retrying. |

With this step-by-step guide you can trace every task from the form submission through backend workflow enforcement, invoice creation, and stock aggregation.
