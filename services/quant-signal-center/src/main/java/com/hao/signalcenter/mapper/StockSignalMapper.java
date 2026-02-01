package com.hao.signalcenter.mapper;

import com.hao.signalcenter.model.StockSignal;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 股票信号 Mapper (Stock Signal Mapper)
 * <p>
 * 数据库操作接口，提供：
 * 1. 追加插入（Insert Mode，同一天可多次触发）
 * 2. 按策略和交易日查询通过的信号（全量流水）
 * 3. 按策略和交易日查询最新信号（每只股票取最新一条）
 *
 * @author hli
 * @date 2026-01-30
 */
@Mapper
public interface StockSignalMapper {

    /**
     * 插入信号（追加模式）
     * <p>
     * 同一股票+策略在同一天可以多次触发，每次触发都是一条新记录。
     * 唯一键：(wind_code, strategy_id, signal_time)
     * 用于防止同一毫秒内的并发重复，不会覆盖不同时间点的信号。
     *
     * @param signal 信号实体
     * @return 影响行数
     */
    int insert(StockSignal signal);

    /**
     * 查询指定策略和交易日通过的信号列表（全量流水）
     * <p>
     * 返回所有触发记录，用于历史回测和信号稳定性分析。
     *
     * @param strategyId 策略ID
     * @param tradeDate  交易日
     * @return 通过的信号列表（按时间倒序）
     */
    List<StockSignal> selectPassedSignals(@Param("strategyId") String strategyId,
                                          @Param("tradeDate") LocalDate tradeDate);

    /**
     * 查询指定交易日所有通过的信号列表（全量流水）
     *
     * @param tradeDate 交易日
     * @return 通过的信号列表（按时间倒序）
     */
    List<StockSignal> selectAllPassedSignalsByDate(@Param("tradeDate") LocalDate tradeDate);

    /**
     * 查询指定策略和交易日的最新信号（每只股票只取最新一条）
     * <p>
     * 使用 ROW_NUMBER() 窗口函数，按股票分组后取最新记录。
     * 适用于展示"当前选股列表"场景。
     *
     * @param strategyId 策略ID
     * @param tradeDate  交易日
     * @return 最新信号列表（每只股票一条）
     */
    List<StockSignal> selectLatestSignals(@Param("strategyId") String strategyId,
                                          @Param("tradeDate") LocalDate tradeDate);
}

