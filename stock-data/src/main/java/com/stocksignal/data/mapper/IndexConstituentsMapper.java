package com.stocksignal.data.mapper;

import com.stocksignal.data.entity.IndexConstituent;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * index_constituents 表访问接口。
 */
public interface IndexConstituentsMapper {

    /** 查询某指数最新快照日期的全部成分。 */
    List<IndexConstituent> findLatestSnapshot(@Param("indexCode") String indexCode);
}
