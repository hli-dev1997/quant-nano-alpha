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
 * 中文：提供基础的查询接口框架，待其他模块产出数据后完善实现。
 * English: Provides basic query interface framework; to be enhanced after other modules produce data.
 */

import com.hao.quant.stocklist.controller.vo.StablePicksVO;
import com.hao.quant.stocklist.common.dto.PageResult;
import com.hao.quant.stocklist.common.dto.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 每日精选股票查询接口。
 * <p>
 * 提供基础的股票列表查询能力，待其他模块产出数据后完善。
 * </p>
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/stable-picks")
@Tag(name = "每日精选股票", description = "股票列表查询（待其他模块产出数据后完善）")
public class StablePicksController {

    /**
     * 方法说明 / Method Description:
     * 中文：查询指定交易日的精选股票分页列表。
     * English: Query paged list of stable picks for a given trade date.
     *
     * @param tradeDate 交易日期
     * @param pageNum   页码（默认1）
     * @param pageSize  每页数量（默认20）
     * @return 分页结果
     */
    @GetMapping("/daily")
    @Operation(summary = "查询每日精选", description = "根据交易日期查询股票列表")
    public Result<PageResult<StablePicksVO>> queryDailyPicks(
            @RequestParam @NotNull(message = "交易日期不能为空")
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate tradeDate,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {

        log.info("查询每日精选|Daily_picks_query,tradeDate={},pageNum={},pageSize={}", tradeDate, pageNum, pageSize);

        // TODO: 待其他模块产出股票数据后实现
        return Result.success(PageResult.empty());
    }
}
