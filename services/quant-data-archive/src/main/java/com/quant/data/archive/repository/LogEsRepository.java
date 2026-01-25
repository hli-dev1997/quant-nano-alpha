package com.quant.data.archive.repository;

import com.quant.data.archive.model.es.LogDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

/**
 * Elasticsearch 日志 Repository
 *
 * 设计目的：
 * 1. 提供日志文档的 CRUD 操作
 * 2. 支持批量写入和查询
 *
 * 为什么需要该接口：
 * - 继承 ElasticsearchRepository 自动获得基础方法
 * - save(), saveAll(), findById() 等开箱即用
 *
 * 扩展说明：
 * - 可通过方法命名规则添加自定义查询
 * - 如 findByService(), findByLevel() 等
 */
@Repository
public interface LogEsRepository extends ElasticsearchRepository<LogDocument, String> {
    // 自带方法：save(), saveAll(), findById(), delete(), count() 等
    // 如需自定义查询，添加方法签名即可，如：
    // List<LogDocument> findByServiceAndLevel(String service, String level);
}
