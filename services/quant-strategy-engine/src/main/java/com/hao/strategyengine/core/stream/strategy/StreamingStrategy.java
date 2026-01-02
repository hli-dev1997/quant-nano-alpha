package com.hao.strategyengine.core.stream.strategy;

import com.hao.strategyengine.core.stream.domain.StockDomainContext;
import com.hao.strategyengine.core.stream.domain.Tick;

/**
 * 流式策略接口（Strategy模式）
 *
 * 设计目的：
 * 1. 定义流式策略的标准契约，所有实时计算策略必须实现此接口。
 * 2. 入参为Tick和Context，强制纯内存操作，禁止任何IO。
 * 3. 返回boolean表示是否命中策略信号。
 *
 * 与QuantStrategy的区别：
 * - QuantStrategy: 批量计算模式，可包含IO操作（DB/RPC）
 * - StreamingStrategy: 实时计算模式，严禁IO，必须O(1)或O(logN)复杂度
 *
 * 设计约束（必须遵守）：
 * 1. isMatch方法必须是纯函数，仅依赖入参，无副作用
 * 2. 禁止在isMatch中进行任何网络调用、数据库查询
 * 3. 执行时间必须控制在微秒级，不得阻塞Worker线程
 *
 * 使用场景：
 * - 九转序列、突破策略、均线交叉等实时信号检测
 * - 由StreamComputeEngine在每个Tick到达时调用
 *
 * @author hli
 * @date 2026-01-02
 */
public interface StreamingStrategy {

    /**
     * 获取策略唯一标识
     *
     * @return 策略ID，用于日志记录和Redis Key生成
     */
    String getId();

    /**
     * 判断是否命中策略信号
     *
     * 实现要求：
     * 1. 纯内存计算，禁止任何IO操作
     * 2. 时间复杂度必须为O(1)或O(logN)
     * 3. 允许修改context中的strategyProps（策略状态）
     *
     * @param tick    当前Tick数据
     * @param context 股票领域上下文（包含历史价格和策略状态）
     * @return true-命中信号，false-未命中
     */
    boolean isMatch(Tick tick, StockDomainContext context);
}
