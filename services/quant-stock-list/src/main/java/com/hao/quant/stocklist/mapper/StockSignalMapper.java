package com.hao.quant.stocklist.mapper;

import com.hao.quant.stocklist.model.StockSignal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 股票信号 Mapper（只读查询）
 * <p>
 * 类职责：
 * 直连 Signal Center 数据库，查询通过风控的信号列表。
 * <p>
 * 使用场景：
 * 仅用于 L3 缓存穿透时的兜底查询。
 * <p>
 * 设计目的：
 * 与 Signal Center 解耦，Stock-List 模块独立查询数据库。
 *
 * @author hli
 * @date 2026-02-01
 */
@Mapper
public interface StockSignalMapper {

    /**
     * 查询指定策略和交易日的已通过信号
     * <p>
     * SQL 位于：resources/mapper/StockSignalMapper.xml
     *
     * @param strategyId 策略ID（如 MA_BULLISH、NINE_TURN_RED）
     * @param tradeDate  交易日字符串（yyyy-MM-dd）
     * @return 信号列表
     */
    List<StockSignal> selectPassedSignals(@Param("strategyId") String strategyId,
                                          @Param("tradeDate") String tradeDate);
}
