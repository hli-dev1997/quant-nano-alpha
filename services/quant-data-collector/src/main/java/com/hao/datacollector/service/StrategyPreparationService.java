package com.hao.datacollector.service;

import java.time.LocalDate;
import java.util.List;

/**
 * 策略数据预处理服务接口
 *
 * 设计目的：
 * 1. 为策略引擎准备每日基础数据，预热历史价格到Redis。
 * 2. 与策略引擎解耦，由数据采集模块统一管理数据准备。
 *
 * 为什么需要该类：
 * - 策略引擎启动时需要历史数据预热（如九转需要20天收盘价）。
 * - 将数据准备逻辑集中到采集模块，避免策略引擎直接访问DB。
 *
 * 核心实现思路：
 * - 查询历史分时数据，提取收盘价（每日最后一条数据）。
 * - 按股票代码组织为HashMap结构，存入Redis供策略引擎消费。
 *
 * @author hli
 * @date 2026-01-02
 */
public interface StrategyPreparationService {

    /**
     * 预热九转序列策略所需的历史数据（全量股票）
     *
     * 实现逻辑：
     * 1. 校验tradeDate是否为有效交易日。
     * 2. 计算前20个交易日的日期区间。
     * 3. 批量查询所有股票的历史分时数据。
     * 4. 提取每日收盘价（当日最后一条记录的latestPrice）。
     * 5. 按股票代码组织为Map<windCode, List<Double>>结构。
     * 6. 以NINE_TURN前缀存入Redis Hash。
     *
     * @param tradeDate 当前交易日
     * @return 预热成功返回处理的股票数量
     * @throws IllegalArgumentException 非交易日或跨年时抛出
     */
    int prepareNineTurnData(LocalDate tradeDate);

    /**
     * 预热九转序列策略所需的历史数据（指定股票列表）
     *
     * 股票列表优先级：
     * 1. 使用传入的 stockCodes（如果非空）
     * 2. 回退到 StockCache.allWindCode（如果 stockCodes 为空或null）
     *
     * @param tradeDate  当前交易日
     * @param stockCodes 股票代码列表（null或空时使用全量）
     * @return 预热成功返回处理的股票数量
     * @throws IllegalArgumentException 非交易日或跨年时抛出
     */
    int prepareNineTurnData(LocalDate tradeDate, List<String> stockCodes);
}
