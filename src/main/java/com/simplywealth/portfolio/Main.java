package com.simplywealth.portfolio;

import com.simplywealth.portfolio.dao.AssetDao;
import com.simplywealth.portfolio.dao.HoldingDao;
import com.simplywealth.portfolio.http.handlers.AssetsHandler;
import com.simplywealth.portfolio.http.handlers.HoldingsHandler;
import com.simplywealth.portfolio.service.AssetService;
import com.simplywealth.portfolio.service.HoldingService;
import com.simplywealth.portfolio.service.PortfolioService;
import com.simplywealth.portfolio.service.YahooFinanceService;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;

/**
 * Entry point (spec Section 7.3 - Foundation).
 * Wires up DAOs -> services -> handlers, and registers routes on a plain
 * JDK HttpServer (no framework, per NFR8). Only two handler classes: one per
 * resource (/assets, /holdings), each dispatching internally on method/path.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        // --- DAOs (raw JDBC, no ORM) ---
        AssetDao assetDao = new AssetDao();
        HoldingDao holdingDao = new HoldingDao();

        // --- Services ---
        YahooFinanceService yahooFinanceService = new YahooFinanceService();
        AssetService assetService = new AssetService(assetDao);
        HoldingService holdingService = new HoldingService(holdingDao);
        PortfolioService portfolioService = new PortfolioService(holdingService, assetService, yahooFinanceService);

        // --- HTTP server ---
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/assets/", new AssetsHandler(yahooFinanceService));
        server.createContext("/holdings", new HoldingsHandler(assetService, holdingService, portfolioService, yahooFinanceService));

        server.setExecutor(null); // default executor
        server.start();

        System.out.println("Portfolio Manager backend running on http://localhost:8080");
    }
}
