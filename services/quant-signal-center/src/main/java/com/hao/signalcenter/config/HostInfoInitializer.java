package com.hao.signalcenter.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import util.HostUtils;
import util.IPUtil;

import java.util.List;

/**
 * 主机信息系统属性初始化器
 *
 * 设计目的：
 * 1. 在 logback 初始化前设置主机信息系统属性。
 * 2. 确保日志系统能够读取到主机名与 IP。
 *
 * 为什么需要该类：
 * - 需要在日志系统初始化前完成主机信息注入，否则 Kafka 日志中的 IP 会显示为 unknown。
 *
 * <p>执行时机：使用 @Order(Ordered.HIGHEST_PRECEDENCE) 确保最早执行
 *
 * @author hli
 * @since 2026-01-31
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HostInfoInitializer implements InitializingBean {

    @Value("${server.port:8807}")
    private String serverPort;

    @Value("${spring.application.name:signal-center}")
    private String serviceName;

    @Value("${spring.profiles.active:dev}")
    private String env;

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("主机信息系统属性初始化开始|Host_info_sysprop_init_start");

        try {
            // 获取主机名
            String hostName = HostUtils.getHostname();
            // 获取最佳 IP
            String hostIp = getBestHostIp();
            
            // 设置系统属性，供 logback 使用
            System.setProperty("HOST_NAME", hostName);
            System.setProperty("HOST_IP", hostIp);
            System.setProperty("server.port", serverPort);
            System.setProperty("spring.application.name", serviceName);
            System.setProperty("spring.profiles.active", env);
            System.setProperty("logging.kafka.topic", "log-" + serviceName);
            
            log.info("主机信息系统属性设置完成|Host_info_sysprop_set_done,hostName={},hostIp={},port={}", 
                    hostName, hostIp, serverPort);
            
        } catch (Exception e) {
            log.error("主机信息系统属性设置失败|Host_info_sysprop_set_failed,error={}", e.getMessage(), e);
            
            // 设置默认值
            System.setProperty("HOST_NAME", "unknown");
            System.setProperty("HOST_IP", "unknown");
            System.setProperty("server.port", serverPort);
            System.setProperty("spring.application.name", serviceName);
            System.setProperty("spring.profiles.active", env);
            System.setProperty("logging.kafka.topic", "log-" + serviceName);
            
            log.warn("已设置默认主机信息系统属性|Default_sysprop_applied");
        }
    }

    /**
     * 获取最佳主机 IP 地址
     * 逻辑优选 192.168.x.x 网段
     */
    private String getBestHostIp() {
        try {
            // 优先从环境变量读取
            String envHostIp = System.getenv("HOST_IP");
            if (envHostIp != null && !envHostIp.trim().isEmpty()) {
                return envHostIp.trim();
            }

            // 使用通用工具类获取可用 IP 列表
            List<String> ips = IPUtil.getIP();
            if (!ips.isEmpty()) {
                // 优先选择 192.168.x.x 网段（个人/局域网开发常用）
                for (String ip : ips) {
                    if (ip.startsWith("192.168.")) {
                        return ip;
                    }
                }
                // 其次选择 10.x.x.x 网段
                for (String ip : ips) {
                    if (ip.startsWith("10.")) {
                        return ip;
                    }
                }
                // 默认返回第一个
                return ips.get(0);
            }
            return "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }
}
