package com.hao.datacollector.replay;

import com.hao.datacollector.dto.quotation.HistoryTrendDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * 时间片缓存
 * <p>
 * 按秒级时间戳组织行情数据，支持高效的按时间检索和移除。
 * 使用读写锁保证多线程安全：预加载线程写入，调度器线程读取。
 *
 * @author hli
 * @date 2026-01-01
 */
@Slf4j
@Component
public class TimeSliceBuffer {

    /**
     * Key: 时间戳（秒）
     * Value: 该秒所有股票的行情列表
     */
    private final TreeMap<Long, List<HistoryTrendDTO>> timeSlices = new TreeMap<>();

    /**
     * 读写锁：支持多读单写
     */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 当前缓冲区数据总条数（原子计数）
     */
    private final AtomicInteger totalSize = new AtomicInteger(0);

    /**
     * 北京时区偏移量
     */
    private static final ZoneOffset BEIJING_ZONE = ZoneOffset.of("+8");

    /**
     * 批量加入数据，按时间分组
     *
     * @param data 行情数据列表
     */
    public void addBatch(List<HistoryTrendDTO> data) {
        if (data == null || data.isEmpty()) {
            return;
        }

        // Step 1: 按 tradeDate 的秒级时间戳分组
        Map<Long, List<HistoryTrendDTO>> grouped = data.stream()
                .filter(dto -> dto.getTradeDate() != null)
                .collect(Collectors.groupingBy(
                        dto -> dto.getTradeDate().toEpochSecond(BEIJING_ZONE)
                ));

        lock.writeLock().lock();
        try {
            int addedCount = 0;
            // Step 2: 将分组后的数据合并到 TreeMap 中
            for (Map.Entry<Long, List<HistoryTrendDTO>> entry : grouped.entrySet()) {
                Long timestamp = entry.getKey();
                List<HistoryTrendDTO> list = entry.getValue();
                timeSlices.computeIfAbsent(timestamp, k -> new ArrayList<>()).addAll(list);
                addedCount += list.size();
            }
            // Step 3: 更新总计数
            totalSize.addAndGet(addedCount);
            log.debug("时间片缓存新增|TimeSlice_buffer_add,sliceCount={},recordCount={},totalSize={}",
                    grouped.size(), addedCount, totalSize.get());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取并移除指定时间戳的数据
     *
     * @param timestamp 秒级时间戳
     * @return 该时间戳的所有行情数据，无数据时返回 null
     */
    public List<HistoryTrendDTO> pollSlice(long timestamp) {
        lock.writeLock().lock();
        try {
            // Step 1: 移除并返回指定时间戳的数据
            List<HistoryTrendDTO> removed = timeSlices.remove(timestamp);
            if (removed != null) {
                // Step 2: 更新总计数
                totalSize.addAndGet(-removed.size());
            }
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取当前缓冲区数据总条数
     *
     * @return 数据总条数
     */
    public int size() {
        return totalSize.get();
    }

    /**
     * 检查缓冲区是否为空
     *
     * @return true 表示为空
     */
    public boolean isEmpty() {
        lock.readLock().lock();
        try {
            return timeSlices.isEmpty();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 清空缓冲区
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            timeSlices.clear();
            totalSize.set(0);
            log.info("时间片缓存已清空|TimeSlice_buffer_cleared");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取时间片数量
     *
     * @return 时间片的个数（不同秒的数量）
     */
    public int sliceCount() {
        lock.readLock().lock();
        try {
            return timeSlices.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取最早的时间戳
     *
     * @return 最早时间戳（秒），缓冲区为空时返回 -1
     */
    public long getEarliestTimestamp() {
        lock.readLock().lock();
        try {
            return timeSlices.isEmpty() ? -1L : timeSlices.firstKey();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取最晚的时间戳
     *
     * @return 最晚时间戳（秒），缓冲区为空时返回 -1
     */
    public long getLatestTimestamp() {
        lock.readLock().lock();
        try {
            return timeSlices.isEmpty() ? -1L : timeSlices.lastKey();
        } finally {
            lock.readLock().unlock();
        }
    }
}
