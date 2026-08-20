package com.simplywealth.portfolio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A single (date, price) pair used for historical charts (FR2, FR6). */
public class PricePoint {
    private LocalDate date;
    private BigDecimal price;

    public PricePoint(LocalDate date, BigDecimal price) {
        this.date = date;
        this.price = price;
    }

    public LocalDate getDate() { return date; }
    public BigDecimal getPrice() { return price; }
}
