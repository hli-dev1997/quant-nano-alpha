package com.hao.datacollector.cache;

import com.hao.datacollector.dal.dao.BaseDataMapper;
import com.hao.datacollector.dto.param.base.StockInfoDailyDTO;
import com.hao.datacollector.dto.table.base.StockBaseDTO;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 股票相关缓存
 * <p>
 * 设计目的：
 * 1. 缓存股票代码与基础信息，减少数据库访问。
 * 2. 提供股票ID与WindCode的快速映射。
 * <p>
 * 为什么需要该类：
 * - 股票基础信息是高频依赖数据，需要集中缓存。
 * <p>
 * 核心实现思路：
 * - 启动时批量加载股票基础数据并构建映射关系。
 *
 * @author hli
 * @program: datacollector
 * @Date 2025-06-20 21:11:07
 * @description: 股票相关缓存
 */
@Slf4j
@Component("StockCache")
public class StockCache {

    @Autowired
    private BaseDataMapper baseDataMapper;

    /**
     * 全部A股代码
     */
    public static List<String> allWindCode;

    /**
     * 补充的A股代码
     */
    public static List<String> supplementWindCode;

    /**
     * 股票ID前缀 -> 完整wind_code 映射
     * 例如：000001 -> 000001.SZ
     */
    public static Map<String, String> stockIdToWindCodeMap = new HashMap<>();

    /**
     * 股票基本信息map
     * key:windCode,value:windName
     */
    public static Map<String, String> windCodeToNameMap = new HashMap<>();

    /**
     * 初始化股票基础缓存
     * <p>
     * 实现逻辑：
     * 1. 加载A股代码与基础信息。
     * 2. 构建风格代码与名称映射。
     * 3. 生成股票ID到WindCode映射。
     */
    @PostConstruct
    private void initStockCache() {
        // 实现思路：
        // 1. 批量读取股票基础数据。
        // 2. 构建多维映射结构。
        allWindCode = baseDataMapper.getAllAStockCode();
        // 初始化补充列表
        initSupplementWindCode();
        //添加补充股票代码
        allWindCode.addAll(supplementWindCode);
        log.info("股票代码缓存完成|Stock_code_cache_loaded,totalSize={}", allWindCode.size());
        List<StockBaseDTO> allWindBaseInfo = baseDataMapper.getAllStockBaseInfo();
        windCodeToNameMap = allWindBaseInfo.stream()
                .collect(Collectors.toMap(StockBaseDTO::getWindCode, StockBaseDTO::getWindName));
        log.info("股票基础信息缓存完成|Stock_base_cache_loaded,baseInfoSize={},nameMapSize={}",
                allWindBaseInfo.size(), windCodeToNameMap.size());
        for (String windCode : allWindCode) {
            // 例如 windCode 是 000001.SZ，截取前缀作为 key
            String prefix = windCode.split("\\.")[0];
            // 避免覆盖已有的
            stockIdToWindCodeMap.putIfAbsent(prefix, windCode);
        }
        log.info("股票ID映射缓存完成|Stock_id_mapping_cache_loaded,mapSize={}", stockIdToWindCodeMap.size());
    }

    /**
     * 初始化补充WindCode列表
     */
    private void initSupplementWindCode() {
        try {
            // 获取当前日期作为查询日期
            // TODO: 这里使用了硬编码日期，建议改为动态获取最新交易日或从配置读取
            String queryDate = "20251225";

            // 查询日频股票信息
            // offset=1, pageSize=100000
            List<StockInfoDailyDTO> dailyStockInfos = baseDataMapper.queryStockInfoDaily(queryDate, 1, 100000);

            if (dailyStockInfos != null && !dailyStockInfos.isEmpty()) {
                // 提取所有windCode
                List<String> dailyWindCodes = dailyStockInfos.stream()
                        .map(StockInfoDailyDTO::getWindCode)
                        .collect(Collectors.toList());

                // 找出allWindCode中没有的windCode
                supplementWindCode = dailyWindCodes.stream()
                        .filter(code -> !allWindCode.contains(code))
                        .collect(Collectors.toList());

                log.info("股票代码补充缓存完成|Stock_supplement_code_cache_loaded,supplementSize={}", supplementWindCode.size());
            } else {
                supplementWindCode = new ArrayList<>();
                log.info("股票代码补充缓存为空|Stock_supplement_code_cache_empty");
            }
        } catch (Exception e) {
            log.error("初始化股票代码补充缓存失败|Init_supplement_wind_code_failed", e);
            supplementWindCode = new ArrayList<>();
        }
    }

    /**
     * 根据股票ID获取完整wind_code
     * <p>
     * 实现逻辑：
     * 1. 通过缓存映射返回WindCode。
     *
     * @param stockId 股票ID
     * @return WindCode
     */
    public static String getWindCodeByStockId(String stockId) {
        // 实现思路：
        // 1. 直接查询缓存映射。
        return stockIdToWindCodeMap.get(stockId);
    }

    /**
     * 根据windCode获取股票名称
     * <p>
     * 实现逻辑：
     * 1. 通过缓存映射返回股票名称。
     *
     * @param windCode WindCode
     * @return 股票名称
     */
    public static String getWindNameByWindCode(String windCode) {
        // 实现思路：
        // 1. 直接查询缓存映射。
        return windCodeToNameMap.get(windCode);
    }
}
