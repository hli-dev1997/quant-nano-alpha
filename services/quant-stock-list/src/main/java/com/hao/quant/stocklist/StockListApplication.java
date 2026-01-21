package com.hao.quant.stocklist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 类说明 / Class Description:
 * 中文：股票精选列表服务启动入口。
 * English: Startup entry for stock picks list service.
 *
 * 使用场景 / Use Cases:
 * 中文：提供基础的股票列表查询能力，待其他模块产出数据后完善。
 * English: Provides basic stock list query capability; to be enhanced after other modules produce data.
 */
@SpringBootApplication
public class StockListApplication {

    /**
     * 启动 SpringBoot 应用。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(StockListApplication.class, args);
    }
}
