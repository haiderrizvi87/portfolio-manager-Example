package com.simplywealth.portfolio.model;

/**
 * Mirrors the Asset table (spec Section 4, NFR2).
 * Holds facts that depend only on the ticker: type and name.
 */
public class Asset {
    private Long id;
    private String ticker;
    private String assetType; // "stock" | "etf" | "crypto"
    private String name;

    public Asset() {}

    public Asset(Long id, String ticker, String assetType, String name) {
        this.id = id;
        this.ticker = ticker;
        this.assetType = assetType;
        this.name = name;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }

    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
