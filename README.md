# Portfolio Manager - Working Skeleton

Backend: plain Java (JDK `HttpServer`, no framework) · Build: Gradle (Groovy DSL) · DB: MySQL (raw JDBC) · JSON: Gson
Frontend: plain HTML/CSS/JS (no UI framework - no React/Vue/Angular), charts via Chart.js (loaded locally, see `frontend/js/vendor/`)

This matches the requirements spec (`portfolio_manager_spec.md`) - see that document for the full
functional/non-functional requirements and design rationale behind every decision here.

Built and structured to be **studied**, not just run - see "How to study this codebase" below.

## ⚠️ Before you rely on this

This was built without access to a live MySQL server or Yahoo's real endpoints. What HAS been
verified:
- The full project **compiles cleanly** against the real Gson library (compiled from source and
  checked, not just a stub).
- A full CRUD cycle, input validation, and the exact profit/loss calculation from spec Section 3.3
  were run **end-to-end** with a real `HttpServer`, real HTTP requests, and real JSON serialization
  - just with fake in-memory data standing in for MySQL and Yahoo. See `runVerification` below.

What has NOT been verified, because it can't be from outside a real environment:
1. The actual MySQL connection and schema (get this running first)
2. Whether Yahoo's real endpoints still behave as expected (they're unofficial - NFR3 in the spec)

## Setup

### 1. MySQL
```
mysql -u root -p < src/main/resources/schema.sql
```
Then edit `src/main/java/com/simplywealth/portfolio/config/DatabaseConfig.java` with your actual
username/password if they differ from the defaults (`root` / `password`).

### 2. Backend (IntelliJ)
- Open the `portfolio-manager` folder as a Gradle project (File > Open) - IntelliJ will resolve
  `mysql-connector-j` and `gson` from Maven Central automatically via `build.gradle`.
- Run `Main.java` (or `./gradlew run` once IntelliJ generates the wrapper).
- Server starts on `http://localhost:8080`.

### 3. Verify the logic without MySQL or Yahoo (recommended first step)
```
./gradlew runVerification
```
Runs `src/test/java/.../ManualIntegrationTest.java` - a plain `main()` method (no JUnit, no
framework) that starts a real `HttpServer` wired to fake in-memory data, fires real HTTP requests
at it, and checks the responses. This proves the routing and calculation logic works *before* you
spend time debugging a MySQL connection string.

### 4. Frontend
The `frontend/` folder is plain static HTML/CSS/JS - no build step. Options:
- Open `frontend/index.html` directly in a browser, or
- Serve it with any static server, e.g. `python3 -m http.server 5500` from inside `frontend/`, or
  IntelliJ's built-in "open in browser" on the HTML file.

`frontend/js/api.js` has `API_BASE = "http://localhost:8080"` - change this if your backend runs
elsewhere. CORS is already handled on the backend (`HttpUtil.addCorsHeaders`).

## Project structure (18 main files - deliberately minimal)

```
src/main/java/com/hsbc/portfolio/
  Main.java                    entry point: wires everything together, starts the server
  config/DatabaseConfig.java   JDBC connection details
  model/                       Asset, Holding - mirror the two DB tables exactly
  dao/                         AssetDao, HoldingDao - raw JDBC, one class per table
  service/
    AssetService.java          "get or create" an Asset row
    HoldingService.java        record / list / delete holdings
    PortfolioService.java      the profit/loss MATH (spec Section 3) - no I/O of its own
    YahooFinanceService.java   all Yahoo Finance HTTP calls + simple cache, in one place
  dto/                         shapes of data sent over the API (separate from the DB model)
  http/
    HttpUtil.java, JsonUtil.java     shared request/response/JSON helpers
    handlers/AssetsHandler.java      everything under /assets/
    handlers/HoldingsHandler.java    everything under /holdings and /holdings/

src/test/java/com/hsbc/portfolio/verify/
  ManualIntegrationTest.java   runnable, readable end-to-end check (see Setup step 3)
```

Only **two** handler classes, each owning one REST resource and dispatching internally on
method + path - not one class per endpoint. This mirrors how a single Servlet or Controller
class conventionally owns one resource in larger frameworks; here it's done with a plain
`if/else` chain so the mechanics are visible rather than hidden behind annotations.

## How to study this codebase

**To understand a request end-to-end**, pick one and trace it top to bottom:
- *Simplest*: `GET /holdings` - `Main.java` (routing) -> `HoldingsHandler.handleList()` ->
  `PortfolioService.getAggregatedPortfolio()` -> `HoldingDao.findAll()` (SQL) -> back up through
  the DTO -> `HttpUtil.sendJson()` (JSON out).
- *Most complete*: `POST /holdings` - same path, but also touches `AssetService` (get-or-create),
  `YahooFinanceService` (price lookup), and several `if` validation checks before anything is
  written to the database. This is the best single method to read if you only read one.

**To understand REST status codes properly**, read `HoldingsHandler.handle()` and
`AssetsHandler.handle()` - both work out *which resource* a path refers to before checking
*which method* was used. That ordering is what makes `404 Not Found` (wrong path) and
`405 Method Not Allowed` (right path, wrong verb) come out correct. Then run
`ManualIntegrationTest` and look at Tests 10-12, which check exactly this.

**To understand CRUD without any ORM**, compare `AssetDao.java` and `HoldingDao.java` side by
side - every method is `PreparedStatement` in, `ResultSet` out, by hand. No annotations, no
magic. This is what Hibernate/JPA do for you automatically in a framework - seeing it written
out once is the point of building it this way.

**To understand testing without a mocking framework**, read the bottom of
`ManualIntegrationTest.java` - `FakeAssetDao`, `FakeHoldingDao`, and `FakeYahooFinanceService`
all just `extends` the real class and override its methods. No interface, no Mockito, no
framework - plain Java inheritance is enough, because none of the real classes or their methods
are marked `final`.

## Code style

Matches the "no lambdas, simple syntax" rule confirmed for this cohort (consistent with the
Transaction Manager project convention):
- **No lambda expressions or arrow functions anywhere** - Java and frontend JS both use plain
  `if/else`, traditional `for` loops, and named classes/functions throughout. `Main.java`'s
  routing uses named handler classes (`AssetsHandler`, `HoldingsHandler`), not inline lambdas.
- **No switch expressions** (`switch (x) { case y -> ... }`) and **no ternaries** - both replaced
  with plain `if/else` for consistency. A traditional `switch (x) { case y: return z; }` statement
  is still used where natural (`YahooFinanceService.mapAssetType`), since that's classic syntax,
  not an expression.
- **DAO, not Repository** naming - `AssetDao`/`HoldingDao` in the `dao` package, matching the
  Transaction Manager project's `AccountDao`/`TransactionDao` convention.
- `Optional` is used for "might not exist" lookups (`findById`, `findByTicker`), checked with
  `.isPresent()`/`.get()` rather than `.orElseThrow(() -> ...)`, to avoid lambdas.

## Known gaps / things to finish as a team

- **Split-adjustment (NFR5) not yet verified** - confirm Yahoo's `v8/finance/chart` endpoint
  returns split-adjusted closes before trusting any P/L numbers in a demo.
- **`getCurrentPrice()` isn't cached** (unlike `getPriceOnDate()`, which is) - every portfolio
  view re-fetches live from Yahoo. Fine at demo scale; worth discussing as a stretch goal.
- **No update/edit endpoint** - deliberately out of scope (spec Section 5): delete and re-add only.
- **`ManualIntegrationTest` checks logic, not your real MySQL/Yahoo setup** - run it first to
  confirm the code works, then still test manually against your real database and network calls
  before a demo.
