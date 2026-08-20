package com.simplywealth.portfolio.dto;

/** Response shape for FR1 search results, sourced from Yahoo's search endpoint. */
public class AssetSearchResult {
    private String ticker;
    private String name;
    private String assetType; // "stock" | "etf" | "crypto"

    public AssetSearchResult(String ticker, String name, String assetType) {
        this.ticker = ticker;
        this.name = name;
        this.assetType = assetType;
    }

    public String getTicker() { return ticker; }
    public String getName() { return name; }
    public String getAssetType() { return assetType; }
}
