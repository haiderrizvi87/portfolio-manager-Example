package com.simplywealth.portfolio.http.handlers;

import com.simplywealth.portfolio.dto.AssetSearchResult;
import com.simplywealth.portfolio.dto.PricePoint;
import com.simplywealth.portfolio.http.HttpUtil;
import com.simplywealth.portfolio.service.YahooFinanceService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * Handles everything under /assets/:
 *   GET /assets/search?q=            -> FR1 (search)
 *   GET /assets/{ticker}/history?range= -> FR2 (historical chart)
 * One class per resource, dispatching internally on the exact path shape -
 * simpler than a separate class per endpoint for a project this size.
 * Neither search nor history touches the database, so this depends directly
 * on YahooFinanceService rather than going via AssetService.
 */
public class AssetsHandler implements HttpHandler {

    private final YahooFinanceService yahooFinanceService;

    public AssetsHandler(YahooFinanceService yahooFinanceService) {
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
        // method is 405 (Method Not Allowed); an unrecognised path is 404 (Not Found) -
        // conflating the two gives misleading error responses to API clients.
        boolean isSearchPath = path.equals("/assets/search");
        boolean isHistoryPath = path.endsWith("/history");

        if (!isSearchPath && !isHistoryPath) {
            HttpUtil.sendError(exchange, 404, "Not found");
            return;
        }

        if (!method.equalsIgnoreCase("GET")) {
            HttpUtil.sendError(exchange, 405, "Method not allowed");
            return;
        }

        if (isSearchPath) {
            handleSearch(exchange);
        } else {
            handleHistory(exchange);
        }
    }

    /** GET /assets/search?q= -> FR1 */
    private void handleSearch(HttpExchange exchange) throws IOException {
        String query = HttpUtil.getQueryParam(exchange, "q");
        if (query == null || query.isBlank()) {
            HttpUtil.sendError(exchange, 400, "Missing required query param: q");
            return;
        }

        try {
            List<AssetSearchResult> results = yahooFinanceService.search(query);
            HttpUtil.sendJson(exchange, 200, results);
        } catch (Exception e) {
            HttpUtil.sendError(exchange, 502, "Failed to search Yahoo Finance: " + e.getMessage());
        }
    }

    /** GET /assets/{ticker}/history?range= -> FR2. range one of: 1w, 1m, 6m, 1y, 5y */
    private void handleHistory(HttpExchange exchange) throws IOException {
        // path is /assets/{ticker}/history -> segment 1 is the ticker
        String ticker = HttpUtil.getPathSegment(exchange, 1);
        String range = HttpUtil.getQueryParam(exchange, "range");
        if (ticker == null || range == null) {
            HttpUtil.sendError(exchange, 400, "Missing ticker or range");
            return;
        }

        LocalDate to = LocalDate.now();
        LocalDate from = null;
        if (range.equals("1w")) {
            from = to.minusWeeks(1);
        } else if (range.equals("1m")) {
            from = to.minusMonths(1);
        } else if (range.equals("6m")) {
            from = to.minusMonths(6);
        } else if (range.equals("1y")) {
            from = to.minusYears(1);
        } else if (range.equals("5y")) {
            from = to.minusYears(5);
        }
        if (from == null) {
            HttpUtil.sendError(exchange, 400, "Invalid range. Use one of: 1w, 1m, 6m, 1y, 5y");
            return;
        }

        try {
            List<PricePoint> series = yahooFinanceService.getHistoricalSeries(ticker, from, to);
            HttpUtil.sendJson(exchange, 200, series);
        } catch (Exception e) {
            HttpUtil.sendError(exchange, 502, "Failed to fetch historical data: " + e.getMessage());
        }
    }
}
