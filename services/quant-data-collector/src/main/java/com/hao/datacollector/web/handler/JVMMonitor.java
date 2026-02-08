package com.hao.datacollector.web.handler;

import constants.NumberFormatConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;

/**
 * JVM 运行时资源监控
 *
 * <p>功能：
 * - 监控堆内存和非堆内存使用情况
 * - 监控线程栈使用情况
 * - 当内存或线程栈使用过高时打印警告日志
 *
 * <p>使用场景：
 * - 及时发现潜在 OOM 或栈溢出风险
 * - 可以扩展为发送邮件或消息告警
 */
@Component
public class JVMMonitor {

    private static final Logger logger = LoggerFactory.getLogger(JVMMonitor.class);

    /**
     * 堆内存使用阈值，超过 80% 打警告
     */
    private static final double HEAP_THRESHOLD = 0.8;

    /**
     * 非堆内存使用阈值，超过 80% 打警告
     */
    private static final double NON_HEAP_THRESHOLD = 0.8;

    /**
     * 线程栈阈值，超过 90% 打警告
     */
    private static final double THREAD_STACK_THRESHOLD = 0.9;

    private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

    /**
     * 检查 JVM 内存和线程栈使用情况
     */
    public void checkJVM() {
        // 堆内存
        MemoryUsage heap = memoryMXBean.getHeapMemoryUsage();
        long heapUsed = heap.getUsed();
        long heapMax = heap.getMax();
        double heapRate = (double) heapUsed / heapMax;
        if (heapRate >= HEAP_THRESHOLD) {
            logger.error("[JVM_MONITOR]堆内存使用高={}/{}MB({})%",
                    heapUsed / 1024 / 1024,
                    heapMax / 1024 / 1024,
                    String.format(NumberFormatConstants.DECIMAL_2, heapRate * 100));
        }

        // 非堆内存
        MemoryUsage nonHeap = memoryMXBean.getNonHeapMemoryUsage();
        long nonHeapUsed = nonHeap.getUsed();
        long nonHeapMax = nonHeap.getMax();
        double nonHeapRate = (double) nonHeapUsed / nonHeapMax;
        if (nonHeapRate >= NON_HEAP_THRESHOLD) {
            logger.warn("[JVM_MONITOR]非堆内存使用高:{}/{}_MB({})%",
                    nonHeapUsed / 1024 / 1024,
                    nonHeapMax / 1024 / 1024,
                    String.format(NumberFormatConstants.DECIMAL_2, nonHeapRate * 100));
        }

        // 线程栈数目
        int threadCount = threadMXBean.getThreadCount();
        int threadPeak = threadMXBean.getPeakThreadCount();
        double threadRate = (double) threadCount / threadPeak;
//        if (threadRate >= THREAD_STACK_THRESHOLD) {
//            logger.error("[JVM_MONITOR]_线程数量接近峰值:{}/{}({})%",
//                    threadCount,
//                    threadPeak,
//                    String.format("%.2f", threadRate * 100));
//        }
    }
}
