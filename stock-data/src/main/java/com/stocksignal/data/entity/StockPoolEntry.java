package com.stocksignal.data.entity;

/**
 * 股票池条目。对应 stock_pool 表，主键 (pool_name, market, code)。
 */
public class StockPoolEntry {

    private String poolName;
    private String market;
    private String code;

    public StockPoolEntry() {
    }

    public StockPoolEntry(String poolName, String market, String code) {
        this.poolName = poolName;
        this.market = market;
        this.code = code;
    }

    public String getPoolName() {
        return poolName;
    }

    public void setPoolName(String poolName) {
        this.poolName = poolName;
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
}
