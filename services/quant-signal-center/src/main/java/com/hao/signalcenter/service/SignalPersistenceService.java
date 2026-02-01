package com.hao.signalcenter.service;

import com.hao.signalcenter.mapper.StockSignalMapper;
import com.hao.signalcenter.model.StockSignal;
import dto.StrategySignalDTO;
import enums.strategy.SignalStatusEnum;
import enums.strategy.StrategyRiskLevelEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 信号持久化服务 (Signal Persistence Service)
 * <p>
 * 类职责：
 * 将策略信号追加写入 MySQL 数据库（流水模式）。
 * <p>
 * 使用场景：
 * Kafka 消费者收到信号后，经过风控判断，调用此服务落库。
 * <p>
 * 核心设计：
 * 1. 追加模式 (Insert Mode)：同一股票+策略在同一天可多次触发
 * 2. 唯一键 (wind_code, strategy_id, signal_time) 防止同一毫秒内并发重复
 * 3. 保留完整信号流水，支持历史回测和信号稳定性分析
 *
 * @author hli
 * @date 2026-01-30
 */
@Slf4j
@Service
public class SignalPersistenceService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private StockSignalMapper stockSignalMapper;

    @Autowired
    private RiskControlClient riskControlClient;

    /**
     * 保存策略信号
     * <p>
     * 根据风控分数和策略风险等级判断展示状态：
     * 1. 正常模式：分数 >= 60 通过，< 60 拦截
     * 2. 降级模式：低风险策略标记中性，其他拦截
     *
     * @param signalDTO  策略信号 DTO
     * @param riskScore  当前风控分数
     * @param isFallback 是否处于降级模式
     * @return 保存的信号实体
     */
    @Transactional(rollbackFor = Exception.class)
    public StockSignal saveSignal(StrategySignalDTO signalDTO, int riskScore, boolean isFallback) {
        // 判断展示状态
        SignalStatusEnum status = determineStatus(signalDTO.getRiskLevel(), riskScore, isFallback);

        // 构建实体
        StockSignal signal = buildEntity(signalDTO, status, riskScore);

        // 追加落库（流水模式）
        int affected = stockSignalMapper.insert(signal);

        log.info("信号落库完成|Signal_persisted,code={},strategyId={},status={},riskScore={},affected={}",
                signal.getWindCode(), signal.getStrategyId(),
                status.getName(), riskScore, affected);

        return signal;
    }

    /**
     * 判断信号展示状态
     *
     * @param riskLevelCode 策略风险等级编码
     * @param riskScore     风控分数
     * @param isFallback    是否降级模式
     * @return 展示状态枚举
     */
    private SignalStatusEnum determineStatus(String riskLevelCode, int riskScore, boolean isFallback) {
        if (isFallback) {
            // 降级模式：根据策略风险等级决定
            StrategyRiskLevelEnum riskLevel = StrategyRiskLevelEnum.fromCode(riskLevelCode);
            if (riskLevel.isAllowedOnFallback()) {
                return SignalStatusEnum.NEUTRAL;  // 低风险策略：标记中性
            } else {
                return SignalStatusEnum.BLOCKED;  // 中高风险策略：强制拦截
            }
        } else {
            // 正常模式：根据分数判断
            if (riskScore >= RiskControlClient.PASS_THRESHOLD) {
                return SignalStatusEnum.PASSED;
            } else {
                return SignalStatusEnum.BLOCKED;
            }
        }
    }

    /**
     * 构建信号实体
     *
     * @param dto       策略信号 DTO
     * @param status    展示状态
     * @param riskScore 风控分数快照
     * @return 信号实体
     */
    private StockSignal buildEntity(StrategySignalDTO dto, SignalStatusEnum status, int riskScore) {
        StockSignal signal = new StockSignal();

        signal.setWindCode(dto.getWindCode());
        signal.setStrategyId(dto.getStrategyId());
        signal.setSignalType(dto.getSignalType());
        signal.setTriggerPrice(dto.getTriggerPrice());
        
        // signalTime 必须非空（数据库 NOT NULL 约束）
        LocalDateTime signalTime = dto.getSignalTime();
        if (signalTime == null) {
            signalTime = LocalDateTime.now();
        }
        signal.setSignalTime(signalTime);

        // 解析交易日（从 signalTime 衍生）
        if (dto.getTradeDate() != null && !dto.getTradeDate().isBlank()) {
            signal.setTradeDate(LocalDate.parse(dto.getTradeDate(), DATE_FORMATTER));
        } else {
            signal.setTradeDate(signalTime.toLocalDate());
        }

        signal.setShowStatus(status.getCode());
        signal.setRiskSnapshot(riskScore);

        return signal;
    }
}
