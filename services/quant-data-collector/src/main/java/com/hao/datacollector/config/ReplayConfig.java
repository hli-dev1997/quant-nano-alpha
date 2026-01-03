package com.hao.datacollector.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 行情回放服务配置
 * <p>
 * 用于配置历史行情回放的参数，支持回测和模拟盘场景
 *
 * @author hli
 * @date 2026-01-01
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "replay")
public class ReplayConfig {

    /**
     * 是否启用回放模式
     */
    private boolean enabled = false;

    /**
     * 回放起始日期，格式：yyyyMMdd
     */
    private String startDate;

    /**
     * 回放结束日期，格式：yyyyMMdd
     */
    private String endDate;

    /**
     * 回放速度倍数
     * 1 = 实时（1秒推送1秒的数据）
     * 10 = 10倍速（1秒推送10秒的数据）
     * 0 = 最快速度（无延迟持续推送）
     */
    private int speedMultiplier = 1;

    /**
     * 每次预加载的时间片长度（分钟）
     * 建议值：5-10分钟，平衡内存占用与数据库查询次数
     */
    private int preloadMinutes = 5;

    /**
     * 缓冲区最大数据条数
     * 当缓冲区超过此值时，预加载线程将暂停等待
     */
    private int bufferMaxSize = 100000;

    /**
     * 指定股票列表（可选）
     * 为空时回放全市场数据
     */
    private String stockCodes;
}
