package com.simplywealth.portfolio.service;

import com.simplywealth.portfolio.dto.AggregatedHoldingDTO;
import com.simplywealth.portfolio.model.Asset;
import com.simplywealth.portfolio.model.Holding;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implements the profit/loss calculation logic from spec Section 3.
 * Depends on HoldingService (stored data) and YahooFinanceService (live prices).
 */
public class PortfolioService {

    private final HoldingService holdingService;
    private final AssetService assetService;
    private final YahooFinanceService yahooFinanceService;

    public PortfolioService(HoldingService holdingService, AssetService assetService, YahooFinanceService yahooFinanceService) {
        this.holdingService = holdingService;
        this.assetService = assetService;
        this.yahooFinanceService = yahooFinanceService;
    }

    /**
     * FR4 default view / FR7 - aggregated per-asset P/L (spec Section 3.3).
     * Holdings of the same asset (e.g. two purchases of NVDA) are summed into one row.
     * Returns a plain list - reused directly by both the FR4 browse view and FR7's bar
     * chart, since both need the same aggregated data (spec Section 3.4).
     */
    public List<AggregatedHoldingDTO> getAggregatedPortfolio() throws Exception {
        List<Holding> allHoldings = holdingService.findAll();

        // Group holdings by assetId (mirrors the "GROUP BY a.id" from spec Section 3.3)
        Map<Long, List<Holding>> byAsset = new LinkedHashMap<>();
        for (Holding h : allHoldings) {
            Long assetId = h.getAssetId();
            if (!byAsset.containsKey(assetId)) {
                byAsset.put(assetId, new ArrayList<Holding>());
            }
            byAsset.get(assetId).add(h);
        }

        List<AggregatedHoldingDTO> result = new ArrayList<>();
        for (Map.Entry<Long, List<Holding>> entry : byAsset.entrySet()) {
            Long assetId = entry.getKey();
            List<Holding> holdings = entry.getValue();

            Optional<Asset> assetOptional = assetService.findById(assetId);
            if (!assetOptional.isPresent()) {
                throw new RuntimeException("Asset not found: " + assetId);
            }
            Asset asset = assetOptional.get();

            BigDecimal totalQuantity = BigDecimal.ZERO;
            BigDecimal totalInvested = BigDecimal.ZERO;
            for (Holding h : holdings) {
                totalQuantity = totalQuantity.add(h.getQuantity());
                totalInvested = totalInvested.add(h.getQuantity().multiply(h.getPriceAtAcquisition()));
            }

            BigDecimal avgPricePaid;
            if (totalQuantity.compareTo(BigDecimal.ZERO) == 0) {
                // Defensive guard: HoldingsHandler already rejects quantity <= 0 at the API
                // boundary, but a service class shouldn't rely solely on the caller for
                // correctness - this avoids an ArithmeticException if that check is ever
                // bypassed or changed later.
                avgPricePaid = BigDecimal.ZERO;
            } else {
                avgPricePaid = totalInvested.divide(totalQuantity, MathContext.DECIMAL64);
            }
            BigDecimal currentPrice = yahooFinanceService.getCurrentPrice(asset.getTicker());
            BigDecimal currentValue = totalQuantity.multiply(currentPrice);
            BigDecimal profitLoss = currentValue.subtract(totalInvested);
            BigDecimal profitLossPercent;
            if (totalInvested.compareTo(BigDecimal.ZERO) == 0) {
                profitLossPercent = BigDecimal.ZERO;
            } else {
                profitLossPercent = profitLoss.divide(totalInvested, MathContext.DECIMAL64).multiply(BigDecimal.valueOf(100));
            }

            result.add(new AggregatedHoldingDTO(
                    assetId, null, asset.getTicker(), asset.getAssetType(), asset.getName(),
                    round(totalQuantity), round(avgPricePaid), round(totalInvested),
                    round(currentPrice), round(currentValue),
                    round(profitLoss), round(profitLossPercent), null
            ));
        }
        return result;
    }

    /** FR6 detail drill-down - per-holding P/L (spec Section 3.2), one entry per individual acquisition. */
    public List<AggregatedHoldingDTO> getPerHoldingBreakdown(Long assetId) throws Exception {
        Optional<Asset> assetOptional = assetService.findById(assetId);
        if (!assetOptional.isPresent()) {
            throw new RuntimeException("Asset not found: " + assetId);
        }
        Asset asset = assetOptional.get();
        BigDecimal currentPrice = yahooFinanceService.getCurrentPrice(asset.getTicker());

        List<AggregatedHoldingDTO> result = new ArrayList<>();
        for (Holding h : holdingService.findByAssetId(assetId)) {
            BigDecimal currentValue = h.getQuantity().multiply(currentPrice);
            BigDecimal invested = h.getQuantity().multiply(h.getPriceAtAcquisition());
            BigDecimal profitLoss = currentValue.subtract(invested);
            BigDecimal profitLossPercent;
            if (invested.compareTo(BigDecimal.ZERO) == 0) {
                profitLossPercent = BigDecimal.ZERO;
            } else {
                profitLossPercent = profitLoss.divide(invested, MathContext.DECIMAL64).multiply(BigDecimal.valueOf(100));
            }

            result.add(new AggregatedHoldingDTO(
                    assetId, h.getId(), asset.getTicker(), asset.getAssetType(), asset.getName(),
                    round(h.getQuantity()), round(h.getPriceAtAcquisition()), round(invested),
                    round(currentPrice), round(currentValue),
                    round(profitLoss), round(profitLossPercent), h.getDateAcquired()
            ));
        }
        return result;
    }

    private BigDecimal round(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}
