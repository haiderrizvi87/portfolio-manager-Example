# Portfolio Manager — Requirements Specification

## 1. Overview

A single-user (no authentication) portfolio tracking application. Users search for and select a financial asset — **stock, ETF, or cryptocurrency** — record an investment (quantity + acquisition date), and view profit/loss and historical performance. Price data is sourced from Yahoo Finance, using **daily granularity** throughout (no intraday/live pricing). Bonds and cash are explicitly out of scope (see Section 5).

---

## 2. Functional Requirements

### FR1 — Search / Select Asset
- User can search for an asset by ticker or name, across three supported types: **stock, ETF, cryptocurrency**.
- Search queries Yahoo Finance (search/autocomplete endpoint) and returns matching results, tagged with their asset type (Yahoo's search results include a type/quote-type field for this).
- Crypto tickers follow Yahoo's pairing format (e.g. `BTC-USD`, `ETH-USD`).
- A small preset list of popular tickers per asset type may be shown by default before the user types anything.
- Invalid/non-existent tickers must be rejected with a clear error before proceeding.
- The user does not need to explicitly declare the asset type before searching — it is inferred from the selected search result and stored against the holding.

### FR2 — View Asset Details (pre-recording)
- After selecting an asset, display:
  - Ticker
  - Name
  - Asset type (stock / ETF / crypto)
  - Most recent available daily closing price
- Display historical price chart with selectable ranges: **1 Week / 1 Month / 6 Months / 1 Year / 5 Years**, sourced from Yahoo's historical daily endpoint.
- Note: crypto trades 24/7 (no "market closed" days), while stocks and ETFs only have data for trading days. Chart and date-picker logic must account for this difference (see FR3).

### FR3 — Record Investment
- User can record an investment in a selected asset by providing:
  - **Quantity** (number of shares/units/coins)
  - **Acquisition date** (user-selectable — does not have to be "today"; supports backdating)
- On submission:
  - System fetches the historical closing price for the ticker on the chosen date from Yahoo.
  - For **stocks/ETFs**: if the chosen date falls on a non-trading day (weekend/holiday), system automatically uses the **nearest prior trading day's** closing price.
  - For **crypto**: prices exist for every calendar day (including weekends), so no fallback logic is needed — the chosen date is used directly.
  - The record is stored with: ticker, asset type, quantity, acquisition date (actual date used), and price on that date.
- Future dates must be rejected.
- No limit on the number of assets a user can record (unlimited portfolio size).

### FR4 — Browse Portfolio
- Display a list of all currently recorded holdings, each showing:
  - Ticker
  - Asset type (stock / ETF / crypto)
  - Quantity
  - Acquisition date
  - Price at acquisition
  - Current (latest daily close) price
  - Current value (`quantity × current price`)
  - Profit/Loss (absolute and %) since acquisition
- Calculation logic is defined in Section 3; default display is the aggregated per-asset view (Section 3.3).

### FR5 — Remove Holding
- User can delete a recorded holding entirely (removes the `Holding` row).
- Deleting a holding does **not** delete the underlying `Asset` record if other holdings still reference it (e.g. the user has recorded NVDA twice, at two dates — deleting one leaves the other and the shared `Asset` row intact).
- If a deleted holding was the last one referencing its `Asset`, the `Asset` row may be left in place (harmless, enables faster re-adding later) or cleaned up — either is acceptable for v1.
- No soft-delete required for v1.

### FR6 — Per-Stock Performance View
- For a single selected holding, display a line chart plotting value/price over time, from the acquisition date to the present, using Yahoo's daily historical data.
- Show current profit/loss (absolute and %) prominently alongside the chart, using the calculation defined in Section 3.4.

### FR7 — Combined Portfolio Performance View
- Display profit/loss **per stock, side-by-side**, using a **bar chart** (not a time-series line chart, and not a pie chart — see rationale in Section 6, Design Notes).
- Optionally, a secondary pie chart may show proportion of **capital invested** per stock (not profit/loss, since pie charts cannot represent negative values).
- No combined "total portfolio value over time" line is required for v1, due to holdings having different acquisition dates (see Section 6, Design Notes).
- Uses the aggregated per-asset calculation defined in Section 3.3.

### FR8 — Data Refresh
- All price data (current and historical) is daily-close granularity only.
- No live/intraday price refresh is required or expected.
- "Current price" = most recent available daily close from Yahoo.

---

## 3. Profit/Loss Calculation Logic

Profit/loss is **never stored** in the database — it is calculated at request time by combining stored acquisition data with a live current price fetched from Yahoo. This section defines exactly how.

### 3.1 Inputs

- From the database (`Holding` row, joined to `Asset`): `quantity`, `price_at_acquisition`
- From Yahoo (fetched live, not stored): `current_price` — the most recent available daily close for that ticker

### 3.2 Per-Holding Calculation (FR4, FR6)

Used when showing a single recorded acquisition (e.g. one line in the detailed holdings view, or the per-asset performance chart in FR6):

```
profit_loss         = (current_price - price_at_acquisition) × quantity
profit_loss_percent = ((current_price - price_at_acquisition) / price_at_acquisition) × 100
current_value        = current_price × quantity
```

**Worked example:** 10 shares of NVDA acquired at £120, current price £160 →
`profit_loss = (160 - 120) × 10 = £400` · `profit_loss_percent = 33.3%` · `current_value = £1,600`

### 3.3 Aggregated Per-Asset Calculation (FR4 default view, FR7)

If the same asset has been recorded more than once (e.g. NVDA bought on two separate dates), holdings for that asset are summed **before** applying the current price, so the user sees one combined position per asset rather than duplicate rows:

```
total_quantity      = SUM(quantity) across all holdings for that asset
total_invested       = SUM(quantity × price_at_acquisition) across all holdings for that asset
avg_price_paid       = total_invested / total_quantity
current_value         = total_quantity × current_price
profit_loss           = current_value - total_invested
profit_loss_percent   = (profit_loss / total_invested) × 100
```

**Worked example:** 10 NVDA @ £120 + 5 NVDA @ £145, current price £160 →
`total_quantity = 15` · `total_invested = £1,925` · `avg_price_paid = £128.33` · `current_value = £2,400` · `profit_loss = +£475 (+24.7%)`

The `total_quantity` and `total_invested` portion can be computed directly in SQL, since it only involves stored data:

```sql
SELECT
  a.id AS asset_id,
  a.ticker,
  a.name,
  SUM(h.quantity) AS total_quantity,
  SUM(h.quantity * h.price_at_acquisition) AS total_invested,
  SUM(h.quantity * h.price_at_acquisition) / SUM(h.quantity) AS avg_price_paid
FROM Holding h
JOIN Asset a ON h.asset_id = a.id
GROUP BY a.id;
```

The application layer then fetches `current_price` per `ticker` from Yahoo (via the cache, NFR4) and computes `current_value`, `profit_loss`, and `profit_loss_percent` on top of the SQL result — this final step cannot be done in SQL alone, since the database has no knowledge of live market prices.

### 3.4 Which View Uses Which Calculation

| View | Calculation used |
|---|---|
| FR4 default "Browse Portfolio" | Aggregated per-asset (3.3) |
| FR4 detail / drill-down into one asset's individual purchases | Per-holding (3.2), one row per acquisition |
| FR6 Per-asset performance chart | Per-holding (3.2) if one purchase, or aggregated (3.3) if multiple purchases of the same asset exist |
| FR7 Combined portfolio bar chart | Aggregated per-asset (3.3) — one bar per asset, not per holding |

---

## 4. Non-Functional Requirements

### NFR1 — Authentication
- None. Single implicit user. No login/session management required.

### NFR2 — Data Storage
- Relational database (SQL) for portfolio holdings, **normalized to at least 3NF**.
- A single flat table is insufficient: if a user records the same ticker more than once (e.g. adds NVDA on two different dates), storing `ticker`/`asset_type` on every holding row would repeat data that actually depends on the asset, not on the individual holding — a normalization violation. The schema below splits this into two tables:

  ```
  Asset
  - id (PK)
  - ticker              (unique)
  - asset_type          ("stock" | "etf" | "crypto")
  - name                (company/asset name, from Yahoo search result)

  Holding
  - id (PK)
  - asset_id (FK → Asset.id)
  - quantity
  - price_at_acquisition
  - date_acquired
  ```

- `Asset` holds facts that depend only on the ticker (type, name) and is written once per new ticker encountered (via search/select). `Holding` holds facts that depend on the specific act of recording an investment (quantity, price paid, date), and references `Asset` by foreign key rather than duplicating ticker/type data.
- This also means a ticker only ever needs to be validated against Yahoo once (on first use) rather than on every holding involving it, though re-validation on each add is also acceptable if simpler to implement.
- No requirement to store historical price series in the database — historical data is fetched live from Yahoo on demand each time it's needed (see NFR4 on caching).

### NFR3 — External Data Source
- Yahoo Finance (via public/unofficial endpoints, or the `yfinance` Python library, or the course-provided sample cached API) is the sole source of price data.
- Yahoo endpoints used are unofficial/undocumented — no uptime or rate-limit guarantees exist. This is an accepted constraint for this project, not something to be engineered around beyond basic error handling (NFR6).

### NFR4 — Caching
- A simple cache (in-memory, keyed by ticker + date) should be used to avoid redundant repeated calls to Yahoo for the same ticker/date combination within a session.
- Historical daily prices are immutable once a trading day has closed and may be cached indefinitely.
- No TTL/expiry logic is required, since there is no "live" price concept in this design (see FR8).

### NFR5 — Split Adjustment Verification
- Before relying on any profit/loss calculation, the chosen Yahoo data source must be verified to return **split-adjusted** closing prices. This must be explicitly checked and documented, not assumed.

### NFR6 — Error Handling
- The system must handle, at minimum:
  - Invalid/non-existent ticker input
  - Yahoo API unavailable or request failure (show a clear message; do not crash)
  - No historical data available for a requested date/ticker
  - Non-trading-day acquisition dates (auto-resolve per FR3)

### NFR7 — Scalability
- Designed for single-user, low-volume demo use only. No requirement for multi-user concurrency, horizontal scaling, or production-grade caching (e.g. Redis) in this version.

### NFR8 — Technology Constraints
- **No frameworks permitted** (explicit project constraint) — no React, Angular, Vue, etc. on the frontend; no Spring Boot or similar on the backend.
- **Frontend:** plain HTML, CSS, and JavaScript.
- **Backend:** plain Java, using the JDK's built-in `com.sun.net.httpserver.HttpServer` (no external web framework).
- **Database:** MySQL Community Server (free, official distribution), accessed via the `mysql-connector-j` JDBC driver and raw SQL (`PreparedStatement`) — no ORM (e.g. no Hibernate).
- See Section 7 for the full component breakdown under these constraints.

---

## 5. Explicitly Out of Scope (v1)

- Multi-currency support / currency conversion
- Dividend tracking
- **Bonds** — no reliable Yahoo-sourced daily pricing exists for most bonds (OTC market, not exchange-traded); would require a different data provider and a materially different pricing model (yield/maturity-based rather than simple daily close)
- **Cash** — has no price history to track; a static balance is a different feature (interest accrual) not covered by this spec
- User accounts / authentication / multi-user support
- Live/intraday price updates
- Editing a recorded holding after creation (delete-and-re-add only)
- Risk/diversification/concentration metrics
- Persistent (database-backed) or long-TTL caching beyond in-memory session cache

---

## 6. Design Notes / Rationale (for reference during build)

- **No scheduler/background job is required.** All performance data is calculated on-demand by fetching Yahoo's historical daily series from the acquisition date to today, rather than periodically recording snapshots.
- **Combined multi-stock time-series charts were deliberately rejected** in favor of a per-stock bar chart for portfolio-wide P/L, because holdings recorded on different dates cannot be meaningfully aligned on a single shared time axis without misleading gaps or false trend lines.
- **Pie charts are only used for positive-only quantities** (e.g. capital invested), never for profit/loss, since losses cannot be represented as a pie slice.
- **Stocks, ETFs, and crypto were included because all three are natively supported by Yahoo Finance with a consistent ticker + daily-close-price model.** Bonds and cash were excluded because they don't fit this model at all (see Section 5) — including them would require a fundamentally different data source and pricing approach, not just an extra `asset_type` value.
- **Crypto's 24/7 trading calendar is the one real behavioral difference** from stocks/ETFs in this design — it affects date validation on acquisition (FR3) but nothing else; the rest of the pipeline (search, chart, P/L calculation) is identical across all three asset types.

---

## 7. Implementation Components (No-Framework Build)

The team is building this together rather than splitting into strict individual ownership, so this section lays out every component as a shared reference — what exists, what it's responsible for, and what it depends on. Build the items marked **[Foundation]** first, together, before writing feature code, since almost everything else depends on them.

### 7.1 Frontend — Pages

Plain HTML/CSS/JS, no framework. Maps directly to the functional requirements:

| # | Page | Covers |
|---|---|---|
| 1 | Search / Home | FR1 — search bar, preset list, results |
| 2 | Asset Detail | FR2, FR3 — current price, historical chart with range toggle, "Record Investment" form |
| 3 | Portfolio Dashboard | FR4 — aggregated holdings list, profit/loss per asset |
| 4 | Asset Performance | FR6 — single-asset line chart over time |
| 5 | Portfolio Performance | FR7 — combined bar chart (and optional pie chart) |

Pages 3 and 4 may be merged (dashboard row expands into a chart) to reduce to 4 pages if preferred — not required.

### 7.2 Backend — Services (business logic, plain Java classes)

| Service | Responsibility | Depends on |
|---|---|---|
| `AssetService` | "Get or create" an `Asset` row (FR1) | `AssetDao` |
| `HoldingService` | Record / list / delete holdings (FR3, FR5) | `HoldingDao` |
| `YahooFinanceService` | All Yahoo calls — search, current price, historical price — plus the cache (NFR4), in one class | Yahoo API |
| `PortfolioService` | P/L calculation logic (Section 3) — per-holding and aggregated | `HoldingService`, `AssetService`, `YahooFinanceService` |

**Simplification note:** an earlier draft split Yahoo access across a separate HTTP client class, a caching service, and a `PriceProvider` interface (to allow swapping in a test stub). For a project this size that was unnecessary indirection — `PortfolioService` now depends directly on the concrete `YahooFinanceService`. Testability wasn't lost: since neither the class nor its methods are `final`, a test can simply `extends YahooFinanceService` and override its methods with fake data (see `src/test/java/.../ManualIntegrationTest.java` in the generated build for a working example) — plain Java inheritance does the same job the interface would have, without the extra file.

### 7.3 Backend — HTTP Server **[Foundation]**

Use `com.sun.net.httpserver.HttpServer` (built into the JDK, no dependency needed):

```java
HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
server.createContext("/assets/", new AssetsHandler());
server.createContext("/holdings", new HoldingsHandler());
server.start();
```

Since there's no framework doing routing/JSON handling for you, build once, share across the team:
- A small routing/dispatch helper (or one `HttpHandler` per resource, as below)
- A shared JSON read/write helper (e.g. a thin wrapper around a JSON library, or hand-rolled if libraries are also restricted — confirm with instructor)
- A shared DB connection helper

### 7.4 Backend — Handlers (one per resource, not one per endpoint)

| Handler | Routes it owns | Maps to |
|---|---|---|
| `AssetsHandler` | `GET /assets/search?q=`, `GET /assets/{ticker}/history?range=` | FR1, FR2 |
| `HoldingsHandler` | `POST /holdings`, `GET /holdings`, `DELETE /holdings/{id}`, `GET /holdings/{assetId}/performance` | FR3, FR4, FR5, FR6, FR7 |

**Simplification note:** an earlier draft used one class per endpoint (7 handler classes) plus two extra classes purely for routing between them. For a project this size, one class per REST *resource* — dispatching internally on method + path — is simpler to navigate and matches how a single Servlet or Controller conventionally owns a resource in larger frameworks. `/portfolio/summary` (FR7) was also dropped as a separate route: it returned identical data to `GET /holdings` (both are the aggregated view from Section 3.3), so the frontend's bar chart reuses `/holdings` directly instead of hitting a duplicate endpoint.

**Getting HTTP status codes right matters here.** Each handler must work out *which resource* a path refers to before checking *which method* was used:
- Unrecognised path → `404 Not Found`
- Recognised path, wrong method (e.g. `DELETE` on the whole `/holdings` collection) → `405 Method Not Allowed`

Getting this ordering backwards (checking method first) causes wrong-but-plausible-looking status codes — a common real-world REST bug worth testing explicitly.

Handlers should stay thin: parse the request, call the relevant service, format the JSON response. No business logic (calculations, validation rules) belongs in a handler — that lives in the services (7.2). Input validation (e.g. rejecting a zero/negative quantity, a future date, or missing required fields) *does* belong in the handler, since it's about rejecting a bad request before any service or database call is made — return `400 Bad Request` early rather than letting bad input surface later as a confusing `500`.

### 7.5 Data Objects

Kept separate — **entities** mirror the DB tables; **DTOs** shape data for API responses (including computed fields the DB doesn't hold, like current price and P/L):

```
Entities (mirror Asset / Holding tables — Section 4, NFR2):
- Asset
- Holding

Request DTOs:
- RecordHoldingRequest      (ticker, assetType, name, quantity, dateAcquired)

Response DTOs:
- AssetSearchResult          (ticker, name, assetType)
- PricePoint                  (date, price) — for chart data
- AggregatedHoldingDTO        (assetId, holdingId, ticker, totalQuantity, avgPricePaid, currentPrice, profitLoss, profitLossPercent)
```

`AggregatedHoldingDTO` is reused for both the aggregated view (Section 3.3, `holdingId` is null since one row can represent multiple underlying holdings) and the per-holding breakdown (Section 3.2, `holdingId` is populated so the frontend can `DELETE` a specific acquisition). A separate `PortfolioSummaryDTO` wrapper was dropped along with the `/portfolio/summary` route (see 7.4) — `GET /holdings` now returns the list directly.

### 7.6 MySQL Setup **[Foundation]**

- **MySQL Community Server** — free, official distribution. Recommended: each team member runs a local instance for development, to avoid contention on a single shared DB during early build.
- **JDBC driver:** `mysql-connector-j` — required to connect from Java; this is a driver, not a framework, so it's compatible with the no-frameworks constraint.
- **No ORM** (no Hibernate) — raw SQL via `PreparedStatement`, consistent with NFR8 and the queries already defined in Section 3.
- Schema (`Asset`, `Holding`) as defined in Section 4, NFR2 — agree this **before** any service code is written, since both `AssetService` and `HoldingService` read/write it.

### 7.7 Suggested Build Order

Since the team is coding together rather than splitting by ownership, this order minimizes rework and blocking:

1. **[Foundation]** Agree `Asset`/`Holding` schema, agree every endpoint's exact JSON request/response shape, stand up the bare `HttpServer` + DB connection helper + JSON helper.
2. `YahooFinanceService` — no other component depends on anything except this and the DAOs existing.
3. `AssetService`, `HoldingService` — depend only on the schema from step 1 and their respective DAO.
4. `PortfolioService` — can be built and tested against a fake `YahooFinanceService` subclass (override its methods with canned data) in parallel with step 2, then switch to the real one once ready — see `ManualIntegrationTest` for a working example of this pattern.
5. Handlers (7.4) — thin wrappers around the now-complete services. Only two classes needed (`AssetsHandler`, `HoldingsHandler`).
6. Frontend pages (7.1) — can be built against mock/hardcoded JSON matching the agreed contract from step 1, then pointed at the real backend once handlers exist.
7. Integration pass — swap all fakes/mocks for real implementations, test end-to-end against the actual MySQL data and live Yahoo calls.
