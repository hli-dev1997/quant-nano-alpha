package com.hao.datacollector.web.controller;

import com.hao.datacollector.service.StrategyPreparationService;
import com.hao.datacollector.web.vo.result.ResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import util.DateUtil;

import java.time.LocalDate;

/**
 * 策略数据预处理控制器
 *
 * 设计目的：
 * 1. 为策略引擎提供数据预热API接口。
 * 2. 将历史收盘价数据组织后存入Redis，供策略引擎启动时消费。
 *
 * 为什么需要该类：
 * - 策略引擎需要历史数据预热（如九转需要20天收盘价）。
 * - 通过独立接口解耦数据准备与策略计算。
 * - 支持定时任务或手动触发数据预热。
 *
 * 核心实现思路：
 * - 校验交易日有效性。
 * - 调用Service层完成数据查询、处理、存储。
 * - 返回预热结果摘要。
 *
 * @author hli
 * @date 2026-01-02
 */
@Tag(name = "策略数据预处理", description = "为策略引擎准备每日的历史基础数据")
@Slf4j
@RestController
@RequestMapping("/strategy_preparation")
@RequiredArgsConstructor
public class StrategyPreparationController {

    private final StrategyPreparationService strategyPreparationService;

    /**
     * 预热九转序列策略数据
     *
     * 实现逻辑：
     * 1. 解析tradeDate参数，默认为今天。
     * 2. 校验是否为有效交易日（在CurrentYearTradeDateList中）。
     * 3. 查询前20个交易日的收盘价数据。
     * 4. 按股票代码组织为Hash结构存入Redis。
     *
     * Redis Key设计：
     * - Key: NINE_TURN:PREHEAT:{yyyyMMdd}
     * - Field: windCode
     * - Value: List<Double> (JSON数组，0=昨日收盘价, 1=前日...)
     *
     * @param tradeDate 交易日期，格式yyyyMMdd或yyyy-MM-dd，不传默认今天
     * @return 预热结果
     */
    @Operation(summary = "预热九转序列数据", 
            description = "计算指定日期前20个交易日的收盘价，存入Redis供策略引擎使用")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "预热成功"),
            @ApiResponse(responseCode = "400", description = "非交易日或参数错误"),
            @ApiResponse(responseCode = "500", description = "服务异常")
    })
    @GetMapping("/nine_turn")
    public ResultVO<String> prepareNineTurnData(
            @Parameter(description = "交易日期，格式yyyyMMdd或yyyy-MM-dd，不传默认今天")
            @RequestParam(required = false) String tradeDate) {

        ResultVO<String> result = new ResultVO<>();

        try {
            // 实现思路：
            // 1. 使用DateUtil统一解析日期参数
            LocalDate date = DateUtil.parseTradeDate(tradeDate);

            log.info("九转预热开始|Nine_turn_preheat_start,tradeDate={}", date);

            // 实现思路：
            // 2. 调用Service执行预热
            int stockCount = strategyPreparationService.prepareNineTurnData(date);

            // 实现思路：
            // 3. 构建成功响应
            result.setCode(200);
            result.setMessage("OK");
            result.setData(String.format("九转预热成功|预热股票数: %d, 交易日: %s", stockCount, date));

            log.info("九转预热成功|Nine_turn_preheat_success,tradeDate={},stockCount={}", date, stockCount);

        } catch (IllegalArgumentException e) {
            // 中文：参数校验失败（非交易日、跨年等）
            // English: Parameter validation failed (non-trading day, cross-year, etc.)
            result.setCode(400);
            result.setMessage(e.getMessage());
            result.setData(null);
            log.warn("九转预热参数错误|Nine_turn_preheat_param_error,tradeDate={},error={}",
                    tradeDate, e.getMessage());

        } catch (Exception e) {
            // 中文：服务异常
            // English: Service exception
            result.setCode(500);
            result.setMessage("服务异常: " + e.getMessage());
            result.setData(null);
            log.error("九转预热异常|Nine_turn_preheat_exception,tradeDate={}", tradeDate, e);
        }

        return result;
    }
}
