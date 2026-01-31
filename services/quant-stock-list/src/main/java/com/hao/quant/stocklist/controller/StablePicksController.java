package com.hao.quant.stocklist.controller;

/**
 * 类说明 / Class Description:
 * 中文：稳定精选股票的对外查询接口控制器。
 * English: External API controller for stable picks query.
 *
 * 使用场景 / Use Cases:
 * 中文：面向前端或第三方服务提供精选股票查询能力。
 * English: Provides query capabilities for selected stocks to frontend or third-party services.
 *
 * 设计目的 / Design Purpose:
 * 中文：基于多级缓存（Caffeine L1 -> Redis L2 -> MySQL L3）提供高性能的股票列表查询。
 * English: Provides high-performance stock list query based on multi-level cache.
 */

import com.hao.quant.stocklist.controller.vo.StablePicksVO;
import com.hao.quant.stocklist.common.dto.PageResult;
import com.hao.quant.stocklist.common.dto.Result;
import com.hao.quant.stocklist.service.MultiLevelCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import util.JsonUtil;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 每日精选股票查询接口。
 * <p>
 * 基于多级缓存架构实现高性能查询：
 * L1 Caffeine（3秒） -> L2 Redis -> L3 MySQL
 * </p>
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/stable-picks")
@Tag(name = "每日精选股票", description = "股票列表查询，基于多级缓存实现")
public class StablePicksController {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private MultiLevelCacheService multiLevelCacheService;

    /**
     * 方法说明 / Method Description:
     * 中文：查询指定交易日的精选股票分页列表。
     * English: Query paged list of stable picks for a given trade date.
     * <p>
     * 查询流程：
     * 1. 调用 MultiLevelCacheService 查询多级缓存
     * 2. 解析 JSON 字符串为 VO 对象
     * 3. 分页处理并返回
     *
     * @param tradeDate    交易日期
     * @param strategyName 策略名称（可选，默认查询所有策略）
     * @param pageNum      页码（默认1）
     * @param pageSize     每页数量（默认20）
     * @return 分页结果
     */
    @GetMapping("/daily")
    @Operation(summary = "查询每日精选", description = "根据交易日期和策略名称查询股票列表")
    public Result<PageResult<StablePicksVO>> queryDailyPicks(
            @RequestParam @NotNull(message = "交易日期不能为空")
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate tradeDate,
            @RequestParam(required = false, defaultValue = "ALL") String strategyName,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {

        long startTime = System.currentTimeMillis();
        String tradeDateStr = tradeDate.format(DATE_FORMATTER);

        log.info("查询每日精选|Daily_picks_query,tradeDate={},strategy={},pageNum={},pageSize={}",
                tradeDateStr, strategyName, pageNum, pageSize);

        try {
            // 1. 从多级缓存查询
            List<String> signalJsonList = multiLevelCacheService.querySignals(strategyName, tradeDateStr);

            if (signalJsonList == null || signalJsonList.isEmpty()) {
                log.info("查询结果为空|Query_result_empty,tradeDate={},strategy={}",
                        tradeDateStr, strategyName);
                return Result.success(PageResult.empty());
            }

            // 2. 解析 JSON 为 VO
            List<StablePicksVO> allPicks = signalJsonList.stream()
                    .map(this::parseToVO)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());

            // 3. 手动分页
            int total = allPicks.size();
            int startIndex = (pageNum - 1) * pageSize;
            int endIndex = Math.min(startIndex + pageSize, total);

            List<StablePicksVO> pagedPicks;
            if (startIndex >= total) {
                pagedPicks = Collections.emptyList();
            } else {
                pagedPicks = allPicks.subList(startIndex, endIndex);
            }

            // 使用 builder 构建 PageResult
            PageResult<StablePicksVO> pageResult = PageResult.<StablePicksVO>builder()
                    .records(pagedPicks)
                    .total(total)
                    .pageNum(pageNum)
                    .pageSize(pageSize)
                    .build();

            long costTime = System.currentTimeMillis() - startTime;
            log.info("查询每日精选完成|Daily_picks_done,tradeDate={},total={},costMs={}",
                    tradeDateStr, total, costTime);

            return Result.success(pageResult);

        } catch (Exception e) {
            log.error("查询每日精选异常|Daily_picks_error,tradeDate={}", tradeDateStr, e);
            return Result.failure(500, "查询失败：" + e.getMessage());
        }
    }

    /**
     * 解析 JSON 字符串为 StablePicksVO
     *
     * @param json JSON 字符串
     * @return VO 对象，解析失败返回 null
     */
    private StablePicksVO parseToVO(String json) {
        try {
            return JsonUtil.toBean(json, StablePicksVO.class);
        } catch (Exception e) {
            log.warn("JSON解析失败|Json_parse_failed,json={}", json);
            return null;
        }
    }
}

