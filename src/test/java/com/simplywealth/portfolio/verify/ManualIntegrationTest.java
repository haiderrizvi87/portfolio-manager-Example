package com.simplywealth.portfolio.verify;

import com.simplywealth.portfolio.dao.AssetDao;
import com.simplywealth.portfolio.dao.HoldingDao;
import com.simplywealth.portfolio.dto.AssetSearchResult;
import com.simplywealth.portfolio.dto.PricePoint;
import com.simplywealth.portfolio.http.handlers.AssetsHandler;
import com.simplywealth.portfolio.http.handlers.HoldingsHandler;
import com.simplywealth.portfolio.model.Asset;
import com.simplywealth.portfolio.model.Holding;
import com.simplywealth.portfolio.service.AssetService;
import com.simplywealth.portfolio.service.HoldingService;
import com.simplywealth.portfolio.service.PortfolioService;
import com.simplywealth.portfolio.service.YahooFinanceService;
import com.sun.net.httpserver.HttpServer;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runs the real routing/handler/service code against FAKE data (in-memory instead of
 * MySQL, canned prices instead of Yahoo), by subclassing AssetDao/HoldingDao/YahooFinanceService
 * and overriding their public methods. No JUnit, no framework - a plain main() method
 * students can read and run directly to see expected request/response behaviour.
 *
 * This is NOT a replacement for testing against the real database and real Yahoo -
 * it only proves the routing, dispatch, and calculation LOGIC is correct.
 *
 * ===== STUDY GUIDE: what each test demonstrates =====
 *
 * CRUD -> HTTP verb -> status code mapping used throughout this app:
 *   Create -> POST   -> 201 Created           (Test 2, 3: record a holding)
 *   Read   -> GET    -> 200 OK                (Test 1, 7, 8, 13: search / list / fetch)
 *   Update -> (not implemented in this app - see spec Section 5, "editing not in scope")
 *   Delete -> DELETE -> 204 No Content         (Test 9: no response body on success)
 *
 * Client error handling (the request itself was invalid - always 4xx, never 5xx):
 *   Test 4, 5, 6 - 400 Bad Request for invalid input (zero quantity, missing field,
 *   future date). Compare HoldingsHandler.handleRecord()'s multiple `if` checks BEFORE
 *   any database or Yahoo call is made - validate early, fail fast.
 *
 * REST routing semantics (Test 10, 11, 12) - the most subtle but important part to study:
 *   404 Not Found        -> the PATH itself doesn't correspond to any resource
 *   405 Method Not Allowed -> the path is a real resource, but this HTTP verb isn't
 *                              supported on it (e.g. DELETE on the whole /holdings collection)
 *   Read HoldingsHandler.handle() and AssetsHandler.handle(): notice they identify WHICH
 *   resource a path refers to FIRST, then check the method - this ordering is what makes
 *   404 vs 405 come out correct. Getting this ordering backwards is a common REST API bug.
 *
 * How fakes work without any test framework or dependency-injection setup:
 *   FakeAssetDao/FakeHoldingDao/FakeYahooFinanceService below all `extends` the real class
 *   and override its public methods with in-memory logic. This works because none of the
 *   real classes are declared `final`, and their methods aren't `final` either - so plain
 *   Java inheritance is enough to substitute fake behaviour for real I/O (database, network)
 *   without needing an interface, a mocking library, or any framework.
 */
public class ManualIntegrationTest {

    private static int passCount = 0;
    private static int failCount = 0;

    public static void main(String[] args) throws Exception {
        FakeAssetDao assetDao = new FakeAssetDao();
        FakeHoldingDao holdingDao = new FakeHoldingDao();
        FakeYahooFinanceService yahoo = new FakeYahooFinanceService();

        AssetService assetService = new AssetService(assetDao);
        HoldingService holdingService = new HoldingService(holdingDao);
        PortfolioService portfolioService = new PortfolioService(holdingService, assetService, yahoo);

        HttpServer server = HttpServer.create(new InetSocketAddress(8099), 0);
        server.createContext("/assets/", new AssetsHandler(yahoo));
        server.createContext("/holdings", new HoldingsHandler(assetService, holdingService, portfolioService, yahoo));
        server.setExecutor(null);
        server.start();

        HttpClient client = HttpClient.newHttpClient();
        String base = "http://localhost:8099";

        try {
            // --- Test 1: GET /assets/search -> 200, returns fake NVDA result ---
            HttpResponse<String> r1 = get(client, base + "/assets/search?q=nvda");
            check("Search returns 200", r1.statusCode() == 200);
            check("Search body contains NVDA", r1.body().contains("NVDA"));

            // --- Test 2: POST /holdings (Create) - record 10 NVDA @ acquisition date ---
            String body1 = "{\"ticker\":\"NVDA\",\"assetType\":\"stock\",\"name\":\"Nvidia\",\"quantity\":\"10\",\"dateAcquired\":\"2026-06-01\"}";
            HttpResponse<String> r2 = post(client, base + "/holdings", body1);
            check("Record holding returns 201", r2.statusCode() == 201);

            // --- Test 3: POST /holdings again - second NVDA purchase (tests Asset reuse, not duplicated) ---
            String body2 = "{\"ticker\":\"NVDA\",\"assetType\":\"stock\",\"name\":\"Nvidia\",\"quantity\":\"5\",\"dateAcquired\":\"2026-08-01\"}";
            HttpResponse<String> r3 = post(client, base + "/holdings", body2);
            check("Second holding returns 201", r3.statusCode() == 201);
            check("Only one Asset row created for NVDA (get-or-create worked)", assetDao.countTicker("NVDA") == 1);

            // --- Test 4: Validation - quantity <= 0 must be rejected (400) ---
            String badBody = "{\"ticker\":\"NVDA\",\"assetType\":\"stock\",\"name\":\"Nvidia\",\"quantity\":\"0\",\"dateAcquired\":\"2026-06-01\"}";
            HttpResponse<String> r4 = post(client, base + "/holdings", badBody);
            check("Zero quantity rejected with 400", r4.statusCode() == 400);

            // --- Test 5: Validation - missing assetType must be rejected (400, not a raw DB error) ---
            String badBody2 = "{\"ticker\":\"AAPL\",\"quantity\":\"1\",\"dateAcquired\":\"2026-06-01\"}";
            HttpResponse<String> r5 = post(client, base + "/holdings", badBody2);
            check("Missing assetType rejected with 400", r5.statusCode() == 400);

            // --- Test 6: Validation - future date rejected (400) ---
            String futureBody = "{\"ticker\":\"NVDA\",\"assetType\":\"stock\",\"name\":\"Nvidia\",\"quantity\":\"1\",\"dateAcquired\":\"2099-01-01\"}";
            HttpResponse<String> r6 = post(client, base + "/holdings", futureBody);
            check("Future date rejected with 400", r6.statusCode() == 400);

            // --- Test 7: GET /holdings (Read/aggregated) - verify the Section 3.3 worked example numbers ---
            // 10 @ £120 + 5 @ £145, fake current price £160 -> total qty 15, invested 1925, value 2400, profit +475 (+24.7%)
            HttpResponse<String> r7 = get(client, base + "/holdings");
            check("List holdings returns 200", r7.statusCode() == 200);
            check("Aggregated totalQuantity is 15", r7.body().contains("\"totalQuantity\": 15"));
            check("Aggregated totalInvested is 1925", r7.body().contains("\"totalInvested\": 1925"));
            check("Aggregated currentValue is 2400", r7.body().contains("\"currentValue\": 2400"));
            check("Aggregated profitLoss is 475", r7.body().contains("\"profitLoss\": 475"));

            // --- Test 8: GET /holdings/{assetId}/performance (per-holding breakdown) ---
            Long nvdaAssetId = assetDao.findByTicker("NVDA").get().getId();
            HttpResponse<String> r8 = get(client, base + "/holdings/" + nvdaAssetId + "/performance");
            check("Performance breakdown returns 200", r8.statusCode() == 200);
            check("Performance breakdown has 2 entries (two separate acquisitions)",
                    countOccurrences(r8.body(), "\"holdingId\"") == 2);

            // --- Test 9: DELETE /holdings/{id} (Delete) ---
            Long firstHoldingId = holdingDao.findAll().get(0).getId();
            HttpResponse<String> r9 = delete(client, base + "/holdings/" + firstHoldingId);
            check("Delete returns 204", r9.statusCode() == 204);
            check("Holding actually removed from storage", holdingDao.findAll().size() == 1);

            // --- Test 10: 404 vs 405 semantics ---
            HttpResponse<String> r10 = get(client, base + "/holdings/junk/whatever/nonsense");
            check("Unrecognised /holdings/ sub-path returns 404, not 405", r10.statusCode() == 404);

            HttpResponse<String> r11 = delete(client, base + "/holdings"); // DELETE on the collection itself is wrong verb
            check("DELETE on /holdings (collection) returns 405, not 404", r11.statusCode() == 405);

            HttpResponse<String> r12 = post(client, base + "/assets/search", "{}");
            check("POST on /assets/search returns 405 (GET-only resource)", r12.statusCode() == 405);

            // --- Test 11: GET /assets/{ticker}/history ---
            HttpResponse<String> r13 = get(client, base + "/assets/NVDA/history?range=1m");
            check("History returns 200", r13.statusCode() == 200);
            check("History body contains price data", r13.body().contains("\"price\""));

        } finally {
            server.stop(0);
        }

        System.out.println("");
        System.out.println("===== RESULTS: " + passCount + " passed, " + failCount + " failed =====");
        if (failCount > 0) {
            System.exit(1);
        }
    }

    private static HttpResponse<String> get(HttpClient client, String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(HttpClient client, String url, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> delete(HttpClient client, String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).DELETE().build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while (true) {
            index = text.indexOf(needle, index);
            if (index == -1) {
                break;
            }
            count = count + 1;
            index = index + needle.length();
        }
        return count;
    }

    private static void check(String description, boolean condition) {
        if (condition) {
            System.out.println("PASS - " + description);
            passCount = passCount + 1;
        } else {
            System.out.println("FAIL - " + description);
            failCount = failCount + 1;
        }
    }

    // ===== Fakes: in-memory substitutes for the DAOs and Yahoo, via subclassing =====

    static class FakeAssetDao extends AssetDao {
        private final Map<Long, Asset> byId = new HashMap<Long, Asset>();
        private final Map<String, Asset> byTicker = new HashMap<String, Asset>();
        private long nextId = 1;

        @Override
        public Optional<Asset> findByTicker(String ticker) {
            if (byTicker.containsKey(ticker)) {
                return Optional.of(byTicker.get(ticker));
            }
            return Optional.empty();
        }

        @Override
        public Optional<Asset> findById(Long id) {
            if (byId.containsKey(id)) {
                return Optional.of(byId.get(id));
            }
            return Optional.empty();
        }

        @Override
        public Asset insert(Asset asset) {
            asset.setId(nextId);
            nextId = nextId + 1;
            byId.put(asset.getId(), asset);
            byTicker.put(asset.getTicker(), asset);
            return asset;
        }

        int countTicker(String ticker) {
            if (byTicker.containsKey(ticker)) {
                return 1;
            }
            return 0;
        }
    }

    static class FakeHoldingDao extends HoldingDao {
        private final List<Holding> holdings = new ArrayList<Holding>();
        private long nextId = 1;

        @Override
        public Holding insert(Holding holding) {
            holding.setId(nextId);
            nextId = nextId + 1;
            holdings.add(holding);
            return holding;
        }

        @Override
        public List<Holding> findAll() {
            return new ArrayList<Holding>(holdings);
        }

        @Override
        public List<Holding> findByAssetId(Long assetId) {
            List<Holding> result = new ArrayList<Holding>();
            for (int i = 0; i < holdings.size(); i = i + 1) {
                if (holdings.get(i).getAssetId().equals(assetId)) {
                    result.add(holdings.get(i));
                }
            }
            return result;
        }

        @Override
        public void deleteById(Long id) {
            for (int i = 0; i < holdings.size(); i = i + 1) {
                if (holdings.get(i).getId().equals(id)) {
                    holdings.remove(i);
                    return;
                }
            }
        }
    }

    static class FakeYahooFinanceService extends YahooFinanceService {
        @Override
        public List<AssetSearchResult> search(String query) {
            List<AssetSearchResult> results = new ArrayList<AssetSearchResult>();
            results.add(new AssetSearchResult("NVDA", "Nvidia Corporation", "stock"));
            return results;
        }

        @Override
        public BigDecimal getCurrentPrice(String ticker) {
            return BigDecimal.valueOf(160); // matches spec Section 3.3 worked example
        }

        @Override
        public BigDecimal getPriceOnDate(String ticker, LocalDate date) {
            // matches spec Section 3.3 worked example: 10 @ £120 (June), 5 @ £145 (August)
            if (date.equals(LocalDate.of(2026, 6, 1))) {
                return BigDecimal.valueOf(120);
            }
            if (date.equals(LocalDate.of(2026, 8, 1))) {
                return BigDecimal.valueOf(145);
            }
            return BigDecimal.valueOf(100);
        }

        @Override
        public List<PricePoint> getHistoricalSeries(String ticker, LocalDate from, LocalDate to) {
            List<PricePoint> points = new ArrayList<PricePoint>();
            points.add(new PricePoint(from, BigDecimal.valueOf(150)));
            points.add(new PricePoint(to, BigDecimal.valueOf(160)));
            return points;
        }
    }
}
