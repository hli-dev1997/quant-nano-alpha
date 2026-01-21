package com.hao.datacollector.service;

import java.util.Map;

/**
 * 指数昨收价缓存服务接口
 * <p>
 * 负责从数据库加载各指数上一交易日收盘价，并缓存到 Redis。
 * 供风控模块通过公共 Redis Key 读取。
 *
 * @author hli
 * @date 2026-01-18
 */
public interface IndexPreCloseCacheService {

    /**
     * 预热所有指数的昨收价缓存
     * <p>
     * 从数据库查询上一交易日各指数的收盘价，写入 Redis。
     * 自动检测回放模式：如果启用回放，使用回放开始日期作为目标日期。
     *
     * @return 成功缓存的指数数量
     */
    int warmUpCache();

    /**
     * 为指定交易日预热昨收价缓存
     * <p>
     * 查询 targetTradeDate 的上一个交易日的收盘价，写入 Redis。
     * 用于回放模式下按回放日期预热。
     *
     * @param targetTradeDate 目标交易日（回放开始日期）
     * @return 成功缓存的指数数量
     */
    int warmUpCacheForDate(java.time.LocalDate targetTradeDate);

    /**
     * 获取当前缓存的指数昨收价 Map
     * <p>
     * 仅供调试或监控使用。
     *
     * @return Map<windCode, preClosePrice>
     */
    Map<String, Double> getCachedPreClosePrices();

    /**
     * 刷新指定指数的缓存
     *
     * @param windCode 指数代码
     */
    void refreshCache(String windCode);

    /**
     * 清除所有昨收价缓存
     * <p>
     * 用于测试或日切时清理旧数据。
     */
    void clearCache();
}
