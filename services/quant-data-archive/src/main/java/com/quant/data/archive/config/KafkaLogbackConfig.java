package com.quant.data.archive.config;

import ch.qos.logback.classic.LoggerContext;
import com.github.danielwegener.logback.kafka.KafkaAppender;
import integration.kafka.KafkaConstants;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import util.HostUtils;
import util.IPUtil;

import java.util.List;

/**
 * Kafka Logback 配置热更新器
 *
 * <p>问题背景：
 * - logback 在 Spring 容器启动之前就初始化，此时 Nacos 配置还未加载
 * - 导致 logback-spring.xml 中无法获取到正确的 Kafka 集群地址和主机信息
 * - 最终使用默认值导致主机信息缺失（hostname=unknown, ip=unknown）
 *
 * <p>解决方案：
 * - 在应用完全启动后（ApplicationReadyEvent），重新配置 Kafka Appender
 * - 此时 Nacos 配置已加载，可以获取到正确的配置信息
 * - 动态更新 KafkaAppender 的所有配置参数
 *
 * @author quant-team
 * @since 2026-01-18
 */
@Slf4j
@Component
@RefreshScope
public class KafkaLogbackConfig implements ApplicationListener<ApplicationReadyEvent> {

    @Value("${spring.kafka.bootstrap-servers}")
    private String kafkaBootstrap;

    @Value("${spring.profiles.active}")
    private String env;

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${spring.application.name:data-archive}")
    private String serviceName;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("应用启动完成开始更新Kafka配置|App_ready_update_kafka_logback");
        updateKafkaConfig();
    }

    public void updateKafkaConfig() {
        try {
            // 使用 common 模块的 HostUtils 和 IPUtil 获取主机信息
            String hostName = HostUtils.getHostname();
            String hostIp = getBestHostIp();
            
            // 设置系统属性
            System.setProperty("HOST_IP", hostIp);
            System.setProperty("server.port", serverPort);
            System.setProperty("spring.application.name", serviceName);
            System.setProperty("spring.profiles.active", env);
            System.setProperty("logging.kafka.topic", KafkaConstants.LOG_TOPIC_PREFIX + serviceName);
            
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            
            // 使用 LoggerContext.putProperty() 设置属性
            loggerContext.putProperty("HOST_IP", hostIp);
            loggerContext.putProperty(KafkaConstants.LOGBACK_PROP_HOST_NAME, hostName);
            loggerContext.putProperty(KafkaConstants.LOGBACK_PROP_HOST_IP, hostIp);
            loggerContext.putProperty(KafkaConstants.LOGBACK_PROP_HOST_PORT, serverPort);
            loggerContext.putProperty(KafkaConstants.LOGBACK_PROP_SERVICE, serviceName);
            loggerContext.putProperty(KafkaConstants.LOGBACK_PROP_ENV, env);
            loggerContext.putProperty(KafkaConstants.LOGBACK_PROP_TOPIC, KafkaConstants.LOG_TOPIC_PREFIX + serviceName);
            
            KafkaAppender kafkaAppender = (KafkaAppender) loggerContext.getLogger("ROOT")
                    .getAppender(KafkaConstants.KAFKA_APPENDER_NAME);

            if (kafkaAppender != null) {
                log.info("找到KafkaAppender开始更新配置|Kafka_appender_found_update_start");
                
                // 停止 appender
                kafkaAppender.stop();
                
                // 更新 Kafka 配置
                kafkaAppender.addProducerConfig("bootstrap.servers=" + kafkaBootstrap);
                kafkaAppender.addProducerConfig("acks=all");
                kafkaAppender.addProducerConfig("retries=2147483647");
                kafkaAppender.addProducerConfig("enable.idempotence=true");
                kafkaAppender.addProducerConfig("max.in.flight.requests.per.connection=5");
                kafkaAppender.addProducerConfig("linger.ms=5");
                kafkaAppender.addProducerConfig("batch.size=32768");
                kafkaAppender.addProducerConfig("buffer.memory=67108864");
                kafkaAppender.addProducerConfig("compression.type=snappy");
                
                // 更新 encoder 的 pattern
                updateEncoderPattern(kafkaAppender, loggerContext, hostName, hostIp, serverPort, serviceName, env);
                
                // 重新启动 appender
                kafkaAppender.start();
                
                log.info("Kafka配置更新成功|Kafka_logback_update_done");
                log.info("Kafka集群|Kafka_bootstrap,bootstrap={}", kafkaBootstrap);
                log.info("主机名|Host_name,name={}", hostName);
                log.info("主机IP|Host_ip,ip={}", hostIp);
                log.info("服务端口|Service_port,port={}", serverPort);
                log.info("服务名|Service_name,name={}", serviceName);
                log.info("运行环境|Active_profile,profile={}", env);
                log.info("日志主题|Log_topic,topic=log-{}", serviceName);
                log.info("实例ID|Instance_id,instanceId={}-{}-{}", serviceName, hostIp, serverPort);
                
            } else {
                log.warn("未找到KafkaAppender|Kafka_appender_not_found,appenderName={}", KafkaConstants.KAFKA_APPENDER_NAME);
            }
            
        } catch (Exception e) {
            log.error("更新Kafka配置失败|Kafka_logback_update_failed,error={}", e.getMessage(), e);
        }
    }

    /**
     * 更新 Encoder 的 Pattern
     */
    @SuppressWarnings("unchecked")
    private void updateEncoderPattern(KafkaAppender kafkaAppender, LoggerContext loggerContext,
                                       String hostName, String hostIp, String port, 
                                       String service, String env) {
        try {
            net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder newEncoder = 
                new net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder();
            newEncoder.setContext(loggerContext);
            
            net.logstash.logback.composite.loggingevent.LoggingEventJsonProviders providers = 
                new net.logstash.logback.composite.loggingevent.LoggingEventJsonProviders();
            providers.setContext(loggerContext);
            
            net.logstash.logback.composite.loggingevent.LoggingEventPatternJsonProvider patternProvider = 
                new net.logstash.logback.composite.loggingevent.LoggingEventPatternJsonProvider();
            patternProvider.setContext(loggerContext);
            
            String pattern = String.format("""
                {
                "env": "%s",
                "service": "%s",
                "ip": "%s",
                "port": "%s",
                "level": "%%level",
                "thread": "%%thread",
                "logger": "%%logger{36}",
                "timestamp": "%%date{yyyy-MM-dd'T'HH:mm:ss.SSS'Z'}",
                "message": "%%msg",
                "exception": "%%exception"
                }
                """, env, service, hostIp, port);
            
            patternProvider.setPattern(pattern);
            providers.addProvider(patternProvider);
            
            newEncoder.setProviders(providers);
            newEncoder.start();
            
            kafkaAppender.setEncoder(newEncoder);
            
            log.debug("Encoder pattern 更新成功|Encoder_pattern_updated");
        } catch (Exception e) {
            log.warn("更新 Encoder pattern 失败|Encoder_pattern_update_failed,error={}", e.getMessage());
        }
    }

    /**
     * 获取最佳主机 IP 地址
     * 使用 common 模块的 IPUtil
     */
    private String getBestHostIp() {
        try {
            // 优先使用环境变量
            String envHostIp = System.getenv("HOST_IP");
            if (envHostIp != null && !envHostIp.trim().isEmpty()) {
                return envHostIp.trim();
            }
            
            // 使用 IPUtil 获取所有 IP
            List<String> ips = IPUtil.getIP();
            if (!ips.isEmpty()) {
                // 优先选择 192.168 开头的 IP
                for (String ip : ips) {
                    if (ip.startsWith("192.168.")) {
                        return ip;
                    }
                }
                // 其次选择 10. 开头的 IP
                for (String ip : ips) {
                    if (ip.startsWith("10.")) {
                        return ip;
                    }
                }
                // 返回第一个可用 IP
                return ips.get(0);
            }
            
            return "unknown";
        } catch (Exception e) {
            log.debug("获取主机IP失败|Host_ip_load_failed,error={}", e.getMessage());
            return "unknown";
        }
    }

    /**
     * 手动刷新配置
     */
    public void refreshKafkaConfig() {
        log.info("手动刷新Kafka配置|Manual_refresh_kafka_logback");
        updateKafkaConfig();
    }
}
