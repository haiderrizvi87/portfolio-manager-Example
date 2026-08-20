package com.simplywealth.portfolio.service;

import com.simplywealth.portfolio.model.Asset;
import com.simplywealth.portfolio.dao.AssetDao;

import java.util.Optional;

/** Handles "get or create" an Asset row when a holding is recorded against a ticker (spec Section 4, NFR2). */
public class AssetService {

    private final AssetDao assetDao;

    public AssetService(AssetDao assetDao) {
        this.assetDao = assetDao;
    }

    /**
     * Returns the existing Asset row for this ticker, or creates one if this is the
     * first time it's been recorded (spec Section 4, NFR2 - Asset is written once per new ticker).
     */
    public Asset getOrCreateAsset(String ticker, String assetType, String name) throws Exception {
        Optional<Asset> existing = assetDao.findByTicker(ticker);
        if (existing.isPresent()) {
            return existing.get();
        }
        Asset asset = new Asset(null, ticker, assetType, name);
        return assetDao.insert(asset);
    }

    public Optional<Asset> findById(Long id) throws Exception {
        return assetDao.findById(id);
    }
}
