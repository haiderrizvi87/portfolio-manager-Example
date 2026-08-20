package com.simplywealth.portfolio.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Mirrors the Holding table (spec Section 4, NFR2).
 * Holds facts that depend on a specific acquisition event: quantity, price paid, date.
 */
public class Holding {
    private Long id;
    private Long assetId;
    private BigDecimal quantity;
    private BigDecimal priceAtAcquisition;
    private LocalDate dateAcquired;

    public Holding() {}

    public Holding(Long id, Long assetId, BigDecimal quantity, BigDecimal priceAtAcquisition, LocalDate dateAcquired) {
        this.id = id;
        this.assetId = assetId;
        this.quantity = quantity;
        this.priceAtAcquisition = priceAtAcquisition;
        this.dateAcquired = dateAcquired;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getPriceAtAcquisition() { return priceAtAcquisition; }
    public void setPriceAtAcquisition(BigDecimal priceAtAcquisition) { this.priceAtAcquisition = priceAtAcquisition; }

    public LocalDate getDateAcquired() { return dateAcquired; }
    public void setDateAcquired(LocalDate dateAcquired) { this.dateAcquired = dateAcquired; }
}
