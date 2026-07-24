package com.stocksignal.data.mapper;

import com.stocksignal.data.entity.StockPoolEntry;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * stock_pool 表访问接口。
 */
public interface StockPoolMapper {

    List<StockPoolEntry> findByPool(@Param("poolName") String poolName);

    int insertIgnore(StockPoolEntry entry);

    int delete(@Param("poolName") String poolName,
               @Param("market") String market,
               @Param("code") String code);
}
