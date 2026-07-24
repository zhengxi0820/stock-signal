package com.stocksignal.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 加载 config/strategies.yaml：股票池 + 策略配置。
 * 默认读 classpath 内置配置，可用 --stock.strategies-config=file:... 覆盖为外部文件。
 */
@Component
public class StrategyConfigLoader {

    private final Resource configResource;
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    public StrategyConfigLoader(ResourceLoader resourceLoader,
                                @Value("${stock.strategies-config:classpath:config/strategies.yaml}") String location) {
        this.configResource = resourceLoader.getResource(location);
    }

    @SuppressWarnings("unchecked")
    public Config load() {
        try {
            Map<String, Object> root = yaml.readValue(configResource.getInputStream(), Map.class);
            Map<String, List<String>> pools = (Map<String, List<String>>) root.getOrDefault("pools", Map.of());
            List<StrategyDefinition> strategies = ((List<Map<String, Object>>) root.getOrDefault("strategies", List.of()))
                    .stream()
                    .map(m -> new StrategyDefinition(
                            (String) m.get("name"),
                            (String) m.get("type"),
                            (String) m.get("pool"),
                            (Map<String, Object>) m.get("params"),
                            (List<String>) m.get("filters")))
                    .toList();
            return new Config(pools, strategies);
        } catch (IOException e) {
            throw new IllegalStateException("策略配置加载失败: " + configResource, e);
        }
    }

    public record Config(Map<String, List<String>> pools, List<StrategyDefinition> strategies) {
    }
}
