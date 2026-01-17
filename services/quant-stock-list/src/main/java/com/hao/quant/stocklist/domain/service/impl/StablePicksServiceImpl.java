package com.hao.quant.stocklist.domain.service.impl;

import com.hao.quant.stocklist.application.assembler.StablePicksAssembler;
import com.hao.quant.stocklist.application.dto.StablePicksQueryDTO;
import com.hao.quant.stocklist.application.vo.StablePicksVO;
import com.hao.quant.stocklist.common.dto.PageResult;
import com.hao.quant.stocklist.common.exception.BusinessException;
import com.hao.quant.stocklist.domain.bloom.StablePicksBloomFilter;
import com.hao.quant.stocklist.domain.lock.StablePicksLockManager;
import com.hao.quant.stocklist.domain.model.StablePick;
import com.hao.quant.stocklist.domain.repository.StablePicksRepository;
import com.hao.quant.stocklist.domain.service.StablePicksService;
import com.hao.quant.stocklist.infrastructure.cache.CacheWrapper;
import com.hao.quant.stocklist.infrastructure.cache.StablePicksCacheKeyBuilder;
import com.hao.quant.stocklist.infrastructure.cache.StablePicksCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 每日精选股票领域服务实现。
 * <p>
 * 该类围绕"查询请求 → 多级缓存 → 仓储回源"流程构建,内部抽象出统一的缓存访问模板方法,以减少重复逻辑,并结合布隆过滤器、
 * 分布式锁与异步刷新策略,在保证一致性的前提下提升热点数据命中率与响应速度。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StablePicksServiceImpl implements StablePicksService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_DATE;

    private final StablePicksRepository repository;
    private final StablePicksAssembler assembler;
    private final StablePicksCacheRepository cacheRepository;
    private final StablePicksCacheKeyBuilder cacheKeyBuilder;
    private final StablePicksBloomFilter bloomFilter;
    private final StablePicksLockManager lockManager;
    @Qualifier("stablePicksScheduler")
    private final TaskScheduler stablePicksScheduler;

    @Value("${stable-picks.cache.redis-ttl:PT10M}")
    private Duration redisTtl;

    @Value("${stable-picks.cache.lock-wait:PT1S}")
    private Duration lockWait;

    @Value("${stable-picks.cache.lock-lease:PT5S}")
    private Duration lockLease;

    /**
     * 查询指定交易日的精选股票分页列表,优先命中多级缓存并在必要时触发异步刷新。
     *
     * @param query 查询参数
     * @return 分页结果
     */
    @Override
    public PageResult<StablePicksVO> queryDailyPicks(StablePicksQueryDTO query) {
        // 交易日是缓存Key与数据库查询的核心参数,必须确保存在
        LocalDate tradeDate = query.getTradeDate();
        if (tradeDate == null) {
            throw new BusinessException("交易日期不能为空");
        }
        // 标准化分页参数,避免异常值导致分页越界
        int pageNum = query.getPageNum() == null || query.getPageNum() <= 0 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 20 : Math.min(query.getPageSize(), 200);
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);

        // 使用布隆过滤器阻挡明显无效的交易日请求,减少回源
        if (!bloomFilter.mightContainTradeDate(tradeDate)) {
            log.warn("布隆过滤器拦截|Bloom_filter_blocked,tradeDate={}", tradeDate);
            return PageResult.empty(pageNum, pageSize);
        }

        String cacheKey = cacheKeyBuilder.buildDailyKey(query);
        // 通过模板方法执行多级缓存查询,缺失时自动回源刷新
        return queryWithCache(
                cacheKey,
                () -> loadDailyCache(query, cacheKey),
                () -> PageResult.empty(pageNum, pageSize)
        );
    }

    /**
     * 查询最新交易日的精选股票列表,根据策略与条数进行缓存聚合。
     *
     * @param strategyId 策略标识
     * @param limit      结果条数限制
     * @return 股票列表
     */
    @Override
    public List<StablePicksVO> queryLatestPicks(String strategyId, Integer limit) {
        int queryLimit = (limit == null || limit <= 0) ? 50 : Math.min(limit, 200);
        String cacheKey = cacheKeyBuilder.buildLatestKey(strategyId, queryLimit);
        return queryWithCache(
                cacheKey,
                () -> loadLatestCache(strategyId, queryLimit, cacheKey),
                Collections::emptyList
        );
    }

    /**
     * 查询单只股票在指定交易日的策略详情。
     *
     * @param stockCode 股票代码
     * @param tradeDate 交易日
     * @return 股票详情视图
     */
    @Override
    public StablePicksVO queryStockDetail(String stockCode, LocalDate tradeDate) {
        if (tradeDate == null) {
            throw new BusinessException("交易日期不能为空");
        }
        if (!bloomFilter.mightContainTradeDate(tradeDate)) {
            throw new BusinessException("非法交易日,请检查日期是否正确");
        }
        String cacheKey = cacheKeyBuilder.buildDetailKey(stockCode, DATE_FORMAT.format(tradeDate));
        return queryWithCache(
                cacheKey,
                () -> loadDetailCache(stockCode, tradeDate, cacheKey),
                () -> null
        );
    }

    /**
     * 手动刷新指定交易日与策略的缓存,用于后台运营工具。
     *
     * @param tradeDate 交易日
     * @param strategyId 策略标识
     */
    @Override
    public void manualRefreshCache(LocalDate tradeDate, String strategyId) {
        log.info("手动刷新缓存|Manual_cache_refresh,tradeDate={},strategyId={}", tradeDate, strategyId);
        if (tradeDate == null) {
            throw new BusinessException("交易日期不能为空");
        }
        StablePicksQueryDTO query = StablePicksQueryDTO.builder()
                .tradeDate(tradeDate)
                .strategyId(strategyId)
                .pageNum(1)
                .pageSize(50)
                .build();
        loadDailyCache(query, cacheKeyBuilder.buildDailyKey(query));
        loadLatestCache(strategyId, 50, cacheKeyBuilder.buildLatestKey(strategyId, 50));
    }

    /**
     * 在开盘前预热热点交易日的缓存数据。
     *
     * @param tradeDate 交易日
     */
    @Override
    public void warmupCache(LocalDate tradeDate) {
        if (tradeDate == null) {
            throw new BusinessException("交易日期不能为空");
        }
        List<StablePick> picks = repository.queryDaily(tradeDate, null, null, 0, 200);
        if (CollectionUtils.isEmpty(picks)) {
            return;
        }
        bloomFilter.addTradeDate(tradeDate);
        StablePicksQueryDTO query = StablePicksQueryDTO.builder()
                .tradeDate(tradeDate)
                .pageNum(1)
                .pageSize(50)
                .build();
        loadDailyCache(query, cacheKeyBuilder.buildDailyKey(query));
    }

    /**
     * 从仓储加载每日精选数据并回写多级缓存。
     *
     * @param query    查询参数
     * @param cacheKey 缓存键
     * @return 包裹了分页结果的缓存对象
     */
    private CacheWrapper<PageResult<StablePicksVO>> loadDailyCache(StablePicksQueryDTO query, String cacheKey) {
        // 计算分页偏移量,并执行数据库查询
        int offset = (query.getPageNum() - 1) * query.getPageSize();
        List<StablePick> picks = repository.queryDaily(query.getTradeDate(), query.getStrategyId(), query.getIndustry(), offset, query.getPageSize());
        long total = repository.countDaily(query.getTradeDate(), query.getStrategyId(), query.getIndustry());

        PageResult<StablePicksVO> page;
        if (total == 0) {
            // 缓存空页面,防止缓存穿透
            page = PageResult.empty(query.getPageNum(), query.getPageSize());
        } else {
            page = PageResult.<StablePicksVO>builder()
                    .total(total)
                    .pageNum(query.getPageNum())
                    .pageSize(query.getPageSize())
                    .records(assembler.toView(picks))
                    .build();
            // 数据命中时回填布隆过滤器,供后续请求拦截
            bloomFilter.addTradeDate(query.getTradeDate());
        }

        CacheWrapper<PageResult<StablePicksVO>> wrapper = CacheWrapper.of(page, redisTtl);
        cacheRepository.put(cacheKey, wrapper, redisTtl.plusSeconds(randomTtlOffset()));

        // 记录 Key 到日期分组，便于策略更新时批量清理
        // Index Key TTL 设置得比数据略长，防止索引先过期
        String indexKey = "stable:picks:index:" + DATE_FORMAT.format(query.getTradeDate());
        cacheRepository.trackKeyInGroup(indexKey, cacheKey, redisTtl.plusHours(1));

        return wrapper;
    }

    /**
     * 加载最新策略股票列表并写入缓存,同时刷新布隆过滤器。
     *
     * @param strategyId 策略标识
     * @param limit      限制条数
     * @param cacheKey   缓存键
     * @return 包裹的缓存对象
     */
    private CacheWrapper<List<StablePicksVO>> loadLatestCache(String strategyId, int limit, String cacheKey) {
        List<StablePick> picks = repository.queryLatest(strategyId, limit);
        List<StablePicksVO> result = assembler.toView(picks);
        CacheWrapper<List<StablePicksVO>> wrapper = CacheWrapper.of(result, redisTtl);
        cacheRepository.put(cacheKey, wrapper, redisTtl.plusSeconds(randomTtlOffset()));

        // 记录到 Latest 分组
        cacheRepository.trackKeyInGroup("stable:picks:index:latest", cacheKey, redisTtl.plusHours(1));

        if (!CollectionUtils.isEmpty(picks)) {
            // 回写所有出现的交易日,确保布隆过滤器数据新鲜
            bloomFilter.addTradeDates(picks.stream().map(StablePick::tradeDate).distinct().toList());
        }
        return wrapper;
    }

    /**
     * 加载单支股票详情并写入缓存。
     *
     * @param stockCode 股票代码
     * @param tradeDate 交易日
     * @param cacheKey  缓存键
     * @return 包裹的缓存对象
     */
    private CacheWrapper<StablePicksVO> loadDetailCache(String stockCode, LocalDate tradeDate, String cacheKey) {
        Optional<StablePick> pickOpt = repository.findDetail(stockCode, tradeDate);
        StablePicksVO vo = pickOpt.map(assembler::toView).orElse(null);
        CacheWrapper<StablePicksVO> wrapper = CacheWrapper.of(vo, redisTtl);
        cacheRepository.put(cacheKey, wrapper, redisTtl.plusSeconds(randomTtlOffset()));

        // 记录到日期分组
        String indexKey = "stable:picks:index:" + DATE_FORMAT.format(tradeDate);
        cacheRepository.trackKeyInGroup(indexKey, cacheKey, redisTtl.plusHours(1));

        pickOpt.ifPresent(pick -> bloomFilter.addTradeDate(pick.tradeDate()));
        return wrapper;
    }

    /**
     * 根据缓存软过期策略触发异步刷新,避免请求线程阻塞。
     *
     * @param cacheKey 缓存键
     * @param loader   刷新逻辑
     * @param wrapper  当前缓存内容
     * @param <T>      数据类型
     */
    private <T> void triggerAsyncRefreshIfNecessary(String cacheKey,
                                                    Supplier<CacheWrapper<T>> loader,
                                                    CacheWrapper<?> wrapper) {
        if (!wrapper.shouldRefreshAsync()) {
            return;
        }
        stablePicksScheduler.schedule(() -> {
            try {
                loader.get();
            } catch (Exception ex) {
                log.error("异步刷新缓存失败|Async_cache_refresh_failed,cacheKey={}", cacheKey, ex);
            }
        }, java.time.Instant.now().plusMillis(asyncRefreshDelayMillis()));
    }

    /**
     * 通用的多级缓存查询模板,封装本地缓存、分布式缓存与分布式锁降级逻辑。
     *
     * @param cacheKey     缓存键
     * @param cacheLoader  缺失时的加载逻辑
     * @param emptySupplier 缓存缺失且加载失败时的兜底数据
     * @param <T>          数据类型
     * @return 查询结果
     */
    private <T> T queryWithCache(String cacheKey,
                                 Supplier<CacheWrapper<T>> cacheLoader,
                                 Supplier<T> emptySupplier) {
        Optional<CacheWrapper<T>> local = cacheRepository.getLocal(cacheKey);
        if (local.isPresent() && !local.get().isExpired()) {
            triggerAsyncRefreshIfNecessary(cacheKey, cacheLoader, local.get());
            return local.get().getData();
        }

        Optional<CacheWrapper<T>> distributed = cacheRepository.getDistributed(cacheKey);
        if (distributed.isPresent() && !distributed.get().isExpired()) {
            cacheRepository.putLocal(cacheKey, distributed.get());
            triggerAsyncRefreshIfNecessary(cacheKey, cacheLoader, distributed.get());
            return distributed.get().getData();
        }

        return (T) lockManager.executeWithLock(
                "lock:" + cacheKey,
                lockWait,
                lockLease,
                () -> cacheLoader.get().getData(),
                () -> cacheRepository.getDistributed(cacheKey)
                        // 再次尝试从分布式缓存读取,尽量复用其他节点刚写入的数据
                        .filter(wrapper -> !wrapper.isExpired())
                        .map(CacheWrapper::getData)
                        .orElseGet(emptySupplier)
        );
    }

    /**
     * 随机化 Redis 过期时间,降低缓存同时失效的风险。
     *
     * @return 额外过期时间秒数
     */
    private long randomTtlOffset() {
        return (long) (Math.random() * 120);
    }

    /**
     * 为异步刷新添加轻微抖动,缓解瞬时并发的刷新压力。
     *
     * @return 延迟毫秒数
     */
    private long asyncRefreshDelayMillis() {
        // 为异步刷新添加轻微抖动,避免瞬时高并发导致刷新风暴
        return (long) (Math.random() * 500);
    }
}
