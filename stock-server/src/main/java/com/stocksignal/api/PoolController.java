package com.stocksignal.api;

import com.stocksignal.data.entity.StockPoolEntry;
import com.stocksignal.data.mapper.StockPoolMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 股票池管理（库表 stock_pool，与 YAML 池取并集后参与扫描）。
 * 注意：公网暴露前必须经 M6 认证关卡，本接口无鉴权仅限本机使用。
 */
@Tag(name = "pools", description = "股票池管理")
@RestController
@RequestMapping("/api/pools/{poolName}/entries")
public class PoolController {

    private final StockPoolMapper stockPoolMapper;

    public PoolController(StockPoolMapper stockPoolMapper) {
        this.stockPoolMapper = stockPoolMapper;
    }

    @Operation(summary = "列出池内股票")
    @GetMapping
    public List<StockPoolEntry> list(
            @Parameter(description = "池名", example = "default") @PathVariable String poolName) {
        return stockPoolMapper.findByPool(poolName);
    }

    @Operation(summary = "加入股票", description = "幂等：重复加入不产生重复记录")
    @PostMapping
    public Map<String, Object> add(
            @PathVariable String poolName,
            @Parameter(description = "市场", example = "SH") @RequestParam String market,
            @Parameter(description = "代码", example = "600519") @RequestParam String code) {
        int inserted = stockPoolMapper.insertIgnore(new StockPoolEntry(poolName, market, code));
        return Map.of("inserted", inserted);
    }

    @Operation(summary = "移出股票")
    @DeleteMapping
    public Map<String, Object> remove(
            @PathVariable String poolName,
            @RequestParam String market,
            @RequestParam String code) {
        int deleted = stockPoolMapper.delete(poolName, market, code);
        return Map.of("deleted", deleted);
    }
}
