package com.hao.signalcenter.mapper;

import com.hao.signalcenter.model.StockSignal;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 股票信号 Mapper (Stock Signal Mapper)
 * <p>
 * 数据库操作接口，提供：
 * 1. 幂等 Upsert（INSERT ON DUPLICATE KEY UPDATE）
 * 2. 按策略和交易日查询通过的信号
 * 3. 按股票代码查询信号历史
 *
 * @author hli
 * @date 2026-01-30
 */
@Mapper
public interface StockSignalMapper {

    /**
     * 幂等插入/更新信号
     * <p>
     * 使用 INSERT ON DUPLICATE KEY UPDATE 语法：
     * - 若记录不存在，执行插入
     * - 若记录已存在（唯一键冲突），执行更新
     * <p>
     * 唯一键：(wind_code, strategy_name, trade_date)
     *
     * @param signal 信号实体
     * @return 影响行数
     */
    int upsert(StockSignal signal);

    /**
     * 查询指定策略和交易日通过的信号列表
     * <p>
     * 用于刷新 Redis 缓存和 API 查询。
     *
     * @param strategyName 策略名称
     * @param tradeDate    交易日
     * @return 通过的信号列表
     */
    List<StockSignal> selectPassedSignals(@Param("strategyName") String strategyName,
                                          @Param("tradeDate") LocalDate tradeDate);

    /**
     * 查询指定交易日所有通过的信号列表
     *
     * @param tradeDate 交易日
     * @return 通过的信号列表
     */
    List<StockSignal> selectAllPassedSignalsByDate(@Param("tradeDate") LocalDate tradeDate);
}
