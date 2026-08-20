package com.simplywealth.portfolio.dto;

/** Request body for POST /holdings (FR3). Quantity/date arrive as strings from JSON and are parsed by the handler. */
public class RecordHoldingRequest {
    private String ticker;
    private String assetType; // "stock" | "etf" | "crypto" - from the search result the user picked
    private String name;       // asset name, from the search result
    private String quantity;
    private String dateAcquired; // ISO format yyyy-MM-dd

    public String getTicker() { return ticker; }
    public String getAssetType() { return assetType; }
    public String getName() { return name; }
    public String getQuantity() { return quantity; }
    public String getDateAcquired() { return dateAcquired; }
}
