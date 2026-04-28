package com.shb.dashboard.model;

import java.math.BigDecimal;

public final class SalesRecord {

    private String     region;
    private String     product;
    private BigDecimal amount;
    private String     saleDate; // ISO-8601: yyyy-MM-dd

    /** Default constructor required for Jackson deserialization from the request body. */
    public SalesRecord() {}

    /** Creates a fully-populated SalesRecord without using individual setters. */
    public SalesRecord(String region, String product, BigDecimal amount, String saleDate) {
        this.region   = region;
        this.product  = product;
        this.amount   = amount;
        this.saleDate = saleDate;
    }

    /** Returns the geographic sales region (e.g., "North", "East"). */
    public String     getRegion()   { return region; }

    /** Returns the product category (e.g., "Bonds", "Equities"). */
    public String     getProduct()  { return product; }

    /** Returns the sale amount in the base currency. */
    public BigDecimal getAmount()   { return amount; }

    /** Returns the sale date as an ISO-8601 string (yyyy-MM-dd). */
    public String     getSaleDate() { return saleDate; }

    /** Sets the geographic sales region. */
    public void setRegion(String region)      { this.region   = region; }

    /** Sets the product category. */
    public void setProduct(String product)    { this.product  = product; }

    /** Sets the sale amount. */
    public void setAmount(BigDecimal amount)  { this.amount   = amount; }

    /** Sets the sale date as an ISO-8601 string (yyyy-MM-dd). */
    public void setSaleDate(String saleDate)  { this.saleDate = saleDate; }
}
