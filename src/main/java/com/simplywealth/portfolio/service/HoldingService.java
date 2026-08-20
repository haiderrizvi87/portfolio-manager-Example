package com.simplywealth.portfolio.service;

import com.simplywealth.portfolio.model.Holding;
import com.simplywealth.portfolio.dao.HoldingDao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** FR3, FR5 - record, list, and delete holdings. */
public class HoldingService {

    private final HoldingDao holdingDao;

    public HoldingService(HoldingDao holdingDao) {
        this.holdingDao = holdingDao;
    }

    public Holding recordHolding(Long assetId, BigDecimal quantity, BigDecimal priceAtAcquisition, LocalDate dateAcquired) throws Exception {
        if (dateAcquired.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Acquisition date cannot be in the future");
        }
        Holding holding = new Holding(null, assetId, quantity, priceAtAcquisition, dateAcquired);
        return holdingDao.insert(holding);
    }

    public List<Holding> findAll() throws Exception {
        return holdingDao.findAll();
    }

    public List<Holding> findByAssetId(Long assetId) throws Exception {
        return holdingDao.findByAssetId(assetId);
    }

    public void deleteHolding(Long id) throws Exception {
        holdingDao.deleteById(id);
    }
}
