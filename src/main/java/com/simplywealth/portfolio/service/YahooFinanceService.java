package com.simplywealth.portfolio.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.simplywealth.portfolio.dto.AssetSearchResult;
import com.simplywealth.portfolio.dto.PricePoint;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * All interaction with Yahoo Finance's unofficial endpoints (NFR3), plus a simple cache (NFR4).
 * Search, current price, and historical price all live here in one class - a training-project-sized
 * app doesn't need this split across an external client + a separate caching service.
 * NOT covered by any uptime/rate-limit guarantee - verify these endpoints still work before relying on them.
 */
public class YahooFinanceService {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    // Simple in-memory caches (Section 7.6 / NFR4). Historical daily prices are immutable once
    // a trading day has closed, so they're cached indefinitely, keyed by "ticker:date".
    private final Map<String, BigDecimal> historicalCache = new HashMap<>();

    /** FR1 - search Yahoo for tickers/company names matching the query. */
    public List<AssetSearchResult> search(String query) throws Exception {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://query2.finance.yahoo.com/v1/finance/search?q=" + encoded
                + "&quotesCount=10&newsCount=0";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        List<AssetSearchResult> results = new ArrayList<AssetSearchResult>();

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!root.has("quotes")) {
            return results;
        }

        JsonArray quotes = root.getAsJsonArray("quotes");
        for (int i = 0; i < quotes.size(); i++) {
            JsonObject q = quotes.get(i).getAsJsonObject();
            if (!q.has("symbol")) {
                continue;
            }

            String ticker = q.get("symbol").getAsString();
            String name = ticker;
            if (q.has("shortname")) {
                name = q.get("shortname").getAsString();
            } else if (q.has("longname")) {
                name = q.get("longname").getAsString();
            }

            String quoteType = "EQUITY";
            if (q.has("quoteType")) {
                quoteType = q.get("quoteType").getAsString();
            }
            String assetType = mapAssetType(quoteType);

            // Only surface the three asset types this app supports (spec Section 4, out of scope: bonds/cash)
            if (assetType != null) {
                results.add(new AssetSearchResult(ticker, name, assetType));
            }
        }
        return results;
    }

    /** FR2/FR6 - historical daily close prices between two dates (inclusive). */
    public List<PricePoint> getHistoricalSeries(String ticker, LocalDate from, LocalDate to) throws Exception {
        long period1 = from.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        long period2 = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond();

        String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + ticker
                + "?period1=" + period1 + "&period2=" + period2 + "&interval=1d";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonObject chart = root.getAsJsonObject("chart");

        List<PricePoint> points = new ArrayList<PricePoint>();
        JsonArray results = chart.getAsJsonArray("result");
        if (results == null || results.size() == 0) {
            throw new RuntimeException("No historical data returned for ticker: " + ticker);
        }

        JsonObject result = results.get(0).getAsJsonObject();
        JsonArray timestamps = result.getAsJsonArray("timestamp");
        JsonObject indicators = result.getAsJsonObject("indicators");
        JsonArray quoteArr = indicators.getAsJsonArray("quote");
        JsonObject quote = quoteArr.get(0).getAsJsonObject();
        JsonArray closes = quote.getAsJsonArray("close");

        for (int i = 0; i < timestamps.size(); i++) {
            if (closes.get(i).isJsonNull()) {
                continue; // non-trading day gap
            }
            long epochSeconds = timestamps.get(i).getAsLong();
            LocalDate date = Instant.ofEpochSecond(epochSeconds).atZone(ZoneOffset.UTC).toLocalDate();
            BigDecimal close = BigDecimal.valueOf(closes.get(i).getAsDouble());
            points.add(new PricePoint(date, close));
        }
        return points;
    }

    /** "Current price" = the latest available daily close (spec: daily granularity throughout, FR8). */
    public BigDecimal getCurrentPrice(String ticker) throws Exception {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(10); // small buffer in case recent days are non-trading days
        List<PricePoint> series = getHistoricalSeries(ticker, from, to);
        if (series.size() == 0) {
            throw new RuntimeException("No recent price data available for ticker: " + ticker);
        }
        return series.get(series.size() - 1).getPrice();
    }

    /** Closing price on a specific date, or the nearest prior trading day if that date has no data (FR3). Cached indefinitely (NFR4). */
    public BigDecimal getPriceOnDate(String ticker, LocalDate date) throws Exception {
        String cacheKey = ticker + ":" + date;
        if (historicalCache.containsKey(cacheKey)) {
            return historicalCache.get(cacheKey);
        }

        LocalDate from = date.minusDays(10); // buffer to find the nearest prior trading day
        List<PricePoint> series = getHistoricalSeries(ticker, from, date);
        if (series.size() == 0) {
            throw new RuntimeException("No price data available on or before " + date + " for ticker: " + ticker);
        }
        BigDecimal price = series.get(series.size() - 1).getPrice();
        historicalCache.put(cacheKey, price);
        return price;
    }

    private String mapAssetType(String yahooQuoteType) {
        if (yahooQuoteType.equals("EQUITY")) {
            return "stock";
        } else if (yahooQuoteType.equals("ETF")) {
            return "etf";
        } else if (yahooQuoteType.equals("CRYPTOCURRENCY")) {
            return "crypto";
        }
        return null; // filters out mutual funds, indices, bonds, etc. (out of scope)
    }
}
