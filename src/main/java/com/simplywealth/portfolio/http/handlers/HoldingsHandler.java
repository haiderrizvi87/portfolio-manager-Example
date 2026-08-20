package com.simplywealth.portfolio.http.handlers;

import com.simplywealth.portfolio.dto.AggregatedHoldingDTO;
import com.simplywealth.portfolio.dto.RecordHoldingRequest;
import com.simplywealth.portfolio.http.HttpUtil;
import com.simplywealth.portfolio.http.JsonUtil;
import com.simplywealth.portfolio.model.Asset;
import com.simplywealth.portfolio.model.Holding;
import com.simplywealth.portfolio.service.AssetService;
import com.simplywealth.portfolio.service.HoldingService;
import com.simplywealth.portfolio.service.PortfolioService;
import com.simplywealth.portfolio.service.YahooFinanceService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Handles everything under /holdings and /holdings/:
 *   POST   /holdings                       -> FR3 (record investment)
 *   GET    /holdings                       -> FR4 (aggregated browse view, also reused for FR7's bar chart)
 *   DELETE /holdings/{id}                  -> FR5 (remove a single acquisition)
 *   GET    /holdings/{assetId}/performance -> FR6 (per-holding breakdown for one asset)
 * One class per resource, dispatching internally on method + path shape.
 */
public class HoldingsHandler implements HttpHandler {

    private final AssetService assetService;
    private final HoldingService holdingService;
    private final PortfolioService portfolioService;
    private final YahooFinanceService yahooFinanceService;

    public HoldingsHandler(AssetService assetService, HoldingService holdingService,
                            PortfolioService portfolioService, YahooFinanceService yahooFinanceService) {
        this.assetService = assetService;
        this.holdingService = holdingService;
        this.portfolioService = portfolioService;
        this.yahooFinanceService = yahooFinanceService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtil.handlePreflight(exchange)) {
            return;
        }

        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        // Work out WHICH resource this path refers to first, before checking the method.
        // This matters for correct HTTP status codes: a recognised path with the wrong
        // method is 405 (Method Not Allowed); an unrecognised path is 404 (Not Found).
        boolean isCollectionPath = path.equals("/holdings");          // the whole collection: /holdings
        boolean isPerformancePath = path.endsWith("/performance");    // /holdings/{assetId}/performance
        boolean isSingleHoldingPath = isSingleHoldingPath(path);      // /holdings/{id}

        if (isCollectionPath) {
            if (method.equalsIgnoreCase("POST")) {
                handleRecord(exchange);
            } else if (method.equalsIgnoreCase("GET")) {
                handleList(exchange);
            } else {
                HttpUtil.sendError(exchange, 405, "Method not allowed");
            }
        } else if (isPerformancePath) {
            if (method.equalsIgnoreCase("GET")) {
                handlePerformance(exchange);
            } else {
                HttpUtil.sendError(exchange, 405, "Method not allowed");
            }
        } else if (isSingleHoldingPath) {
            if (method.equalsIgnoreCase("DELETE")) {
                handleDelete(exchange);
            } else {
                HttpUtil.sendError(exchange, 405, "Method not allowed");
            }
        } else {
            HttpUtil.sendError(exchange, 404, "Not found");
        }
    }

    /** True only for exactly "/holdings/{id}" - one path segment after "/holdings", e.g. "/holdings/5". */
    private boolean isSingleHoldingPath(String path) {
        String[] segments = path.split("/");
        // "/holdings/5" splits to ["", "holdings", "5"] - exactly 3 elements
        return segments.length == 3 && segments[1].equals("holdings");
    }

    /** POST /holdings -> FR3. Body: { ticker, assetType, name, quantity, dateAcquired } */
    private void handleRecord(HttpExchange exchange) throws IOException {
        try {
            String body = HttpUtil.readBody(exchange);
            RecordHoldingRequest req = JsonUtil.GSON.fromJson(body, RecordHoldingRequest.class);

            if (req.getTicker() == null || req.getQuantity() == null || req.getDateAcquired() == null) {
                HttpUtil.sendError(exchange, 400, "ticker, quantity, and dateAcquired are required");
                return;
            }
            if (req.getAssetType() == null || req.getName() == null) {
                // Both come from the search result the user picked (FR1) - if either is missing,
                // a brand-new ticker would otherwise fail the database's NOT NULL constraint
                // (schema.sql) with a confusing 502 instead of a clear 400.
                HttpUtil.sendError(exchange, 400, "assetType and name are required");
                return;
            }

            LocalDate dateAcquired = LocalDate.parse(req.getDateAcquired());
            if (dateAcquired.isAfter(LocalDate.now())) {
                HttpUtil.sendError(exchange, 400, "dateAcquired cannot be in the future");
                return;
            }

            BigDecimal quantity = new BigDecimal(req.getQuantity());
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                // Without this check, a zero or negative quantity would pass through to
                // PortfolioService and could cause a divide-by-zero when calculating avgPricePaid.
                HttpUtil.sendError(exchange, 400, "quantity must be greater than zero");
                return;
            }

            // FR1/NFR2 - get-or-create the Asset row so the ticker isn't duplicated across holdings
            Asset asset = assetService.getOrCreateAsset(req.getTicker(), req.getAssetType(), req.getName());

            // FR3 - price on the chosen date (auto-resolves non-trading days for stocks/ETFs)
            BigDecimal price = yahooFinanceService.getPriceOnDate(asset.getTicker(), dateAcquired);

            Holding holding = holdingService.recordHolding(asset.getId(), quantity, price, dateAcquired);
            HttpUtil.sendJson(exchange, 201, holding);

        } catch (NumberFormatException e) {
            HttpUtil.sendError(exchange, 400, "Invalid quantity format");
        } catch (java.time.format.DateTimeParseException e) {
            HttpUtil.sendError(exchange, 400, "Invalid dateAcquired format - expected yyyy-MM-dd");
        } catch (IllegalArgumentException e) {
            HttpUtil.sendError(exchange, 400, e.getMessage());
        } catch (Exception e) {
            HttpUtil.sendError(exchange, 502, "Failed to record holding: " + e.getMessage());
        }
    }

    /** GET /holdings -> FR4 (aggregated per-asset view, spec Section 3.3). Also used by summary.js for FR7's bar chart. */
    private void handleList(HttpExchange exchange) throws IOException {
        try {
            List<AggregatedHoldingDTO> holdings = portfolioService.getAggregatedPortfolio();
            HttpUtil.sendJson(exchange, 200, holdings);
        } catch (Exception e) {
            HttpUtil.sendError(exchange, 502, "Failed to load portfolio: " + e.getMessage());
        }
    }

    /** GET /holdings/{assetId}/performance -> FR6 (per-holding breakdown, spec Section 3.2/3.4) */
    private void handlePerformance(HttpExchange exchange) throws IOException {
        String assetIdParam = HttpUtil.getPathSegment(exchange, 1); // /holdings/{assetId}/performance
        if (assetIdParam == null) {
            HttpUtil.sendError(exchange, 400, "Missing assetId");
            return;
        }

        try {
            Long assetId = Long.parseLong(assetIdParam);
            List<AggregatedHoldingDTO> breakdown = portfolioService.getPerHoldingBreakdown(assetId);
            HttpUtil.sendJson(exchange, 200, breakdown);
        } catch (NumberFormatException e) {
            HttpUtil.sendError(exchange, 400, "Invalid assetId");
        } catch (Exception e) {
            HttpUtil.sendError(exchange, 502, "Failed to load performance data: " + e.getMessage());
        }
    }

    /** DELETE /holdings/{id} -> FR5 */
    private void handleDelete(HttpExchange exchange) throws IOException {
        String idParam = HttpUtil.getPathSegment(exchange, 1); // /holdings/{id}
        if (idParam == null) {
            HttpUtil.sendError(exchange, 400, "Missing holding id");
            return;
        }

        try {
            Long id = Long.parseLong(idParam);
            HttpUtil.addCorsHeaders(exchange); // added this to ensure back end and front end ports confirmed deletion message.
            holdingService.deleteHolding(id);
            exchange.sendResponseHeaders(204, -1);
        } catch (NumberFormatException e) {
            HttpUtil.sendError(exchange, 400, "Invalid holding id");
        } catch (Exception e) {
            HttpUtil.sendError(exchange, 500, "Failed to delete holding: " + e.getMessage());
        }
    }
}
