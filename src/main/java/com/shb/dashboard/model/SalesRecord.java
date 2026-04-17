package com.shb.dashboard.model;

import java.math.BigDecimal;

public final class SalesRecord {

    private String     region;
    private String     product;
    private BigDecimal amount;
    private String     saleDate; // ISO-8601: yyyy-MM-dd

    public SalesRecord() {}

    public SalesRecord(String region, String product, BigDecimal amount, String saleDate) {
        this.region   = region;
        this.product  = product;
        this.amount   = amount;
        this.saleDate = saleDate;
    }

    public String     getRegion()   { return region; }
    public String     getProduct()  { return product; }
    public BigDecimal getAmount()   { return amount; }
    public String     getSaleDate() { return saleDate; }

    public void setRegion(String region)      { this.region   = region; }
    public void setProduct(String product)    { this.product  = product; }
    public void setAmount(BigDecimal amount)  { this.amount   = amount; }
    public void setSaleDate(String saleDate)  { this.saleDate = saleDate; }
}
