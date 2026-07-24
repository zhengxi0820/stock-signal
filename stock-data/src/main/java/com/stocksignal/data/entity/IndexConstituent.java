package com.stocksignal.data.entity;

import java.time.LocalDate;

/**
 * 指数成分快照条目。对应 index_constituents 表。
 */
public class IndexConstituent {

    private String indexCode;
    private String market;
    private String code;
    private LocalDate snapshotDate;

    public String getIndexCode() {
        return indexCode;
    }

    public void setIndexCode(String indexCode) {
        this.indexCode = indexCode;
    }

    public String getMarket() {
        return market;
    }

    public void setMarket(String market) {
        this.market = market;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public void setSnapshotDate(LocalDate snapshotDate) {
        this.snapshotDate = snapshotDate;
    }
}
