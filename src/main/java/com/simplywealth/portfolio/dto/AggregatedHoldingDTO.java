package com.simplywealth.portfolio.dto;
import java.time.LocalDate;
import java.math.BigDecimal;

/**
 * Aggregated per-asset view (spec Section 3.3) - the default "Browse Portfolio" (FR4) row.
 * If an asset has multiple holdings (e.g. bought on two dates), they are summed into one row here.
 */
public class AggregatedHoldingDTO {
    private Long assetId;
    // Only populated for the per-holding breakdown (Section 3.2 / FR6), null in the
    // aggregated view (Section 3.3) since one aggregated row can represent multiple
    // underlying Holding rows. Needed so the frontend can call DELETE /holdings/{id}
    // on a specific acquisition, not just the asset as a whole.
    private Long holdingId;
    private String ticker;
    private String assetType;
    private String name;
    private BigDecimal totalQuantity;
    private BigDecimal avgPricePaid;
    private BigDecimal totalInvested;
    private BigDecimal currentPrice;
    private BigDecimal currentValue;
    private BigDecimal profitLoss;
    private BigDecimal profitLossPercent;
    private LocalDate dateAcquired;

    public AggregatedHoldingDTO(Long assetId, Long holdingId, String ticker, String assetType, String name,
                                BigDecimal totalQuantity, BigDecimal avgPricePaid, BigDecimal totalInvested,
                                BigDecimal currentPrice, BigDecimal currentValue,
                                BigDecimal profitLoss, BigDecimal profitLossPercent, LocalDate dateAcquired) {
        this.assetId = assetId;
        this.holdingId = holdingId;
        this.ticker = ticker;
        this.assetType = assetType;
        this.name = name;
        this.totalQuantity = totalQuantity;
        this.avgPricePaid = avgPricePaid;
        this.totalInvested = totalInvested;
        this.currentPrice = currentPrice;
        this.currentValue = currentValue;
        this.profitLoss = profitLoss;
        this.profitLossPercent = profitLossPercent;
        this.dateAcquired = dateAcquired;
    }

    public Long getAssetId() { return assetId; }
    public Long getHoldingId() { return holdingId; }
    public String getTicker() { return ticker; }
    public String getAssetType() { return assetType; }
    public String getName() { return name; }
    public BigDecimal getTotalQuantity() { return totalQuantity; }
    public BigDecimal getAvgPricePaid() { return avgPricePaid; }
    public BigDecimal getTotalInvested() { return totalInvested; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public BigDecimal getCurrentValue() { return currentValue; }
    public BigDecimal getProfitLoss() { return profitLoss; }
    public BigDecimal getProfitLossPercent() { return profitLossPercent; }
    public LocalDate getDateAcquired() { return dateAcquired; }
}
