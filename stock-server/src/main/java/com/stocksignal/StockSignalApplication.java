package com.stocksignal;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.stocksignal.data.mapper")
public class StockSignalApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockSignalApplication.class, args);
    }
}
