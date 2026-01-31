package com.hao.signalcenter.config;

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
 * <p>
 * 由于 Logback 初始化通常早于 Spring Bean 加载，导致启动初期的日志无法正确获取动态变化的 IP 等信息。
 * 本类通过监听 ApplicationReadyEvent，在应用完全就绪后，通过程序化的方式：
 * 1. 重新检测本机最佳 IP。
 * 2. 更新 LoggerContext 中的全局属性。
 * 3. 停止、修改并重启 KafkaAppender，确保后续日志携带正确的实例标识。
 *
 * @author hli
 * @since 2026-01-31
 */
@Slf4j
@Component
@RefreshScope
public class KafkaLogbackConfig implements ApplicationListener<ApplicationReadyEvent> {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String kafkaBootstrap;

    @Value("${spring.profiles.active:dev}")
    private String env;

    @Value("${server.port:8807}")
    private String serverPort;

    @Value("${spring.application.name:signal-center}")
    private String serviceName;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("应用启动完成开始更新Kafka配置|App_ready_update_kafka_logback");
        updateKafkaConfig();
    }

    /**
     * 更新 Kafka Appender 配置
     */
    public void updateKafkaConfig() {
        try {
            String hostName = HostUtils.getHostname();
            String hostIp = getBestHostIp();
            
            // 设置系统属性（冗余备份）
            System.setProperty("HOST_IP", hostIp);
            System.setProperty("server.port", serverPort);
            System.setProperty("spring.application.name", serviceName);
            System.setProperty("spring.profiles.active", env);
            System.setProperty("logging.kafka.topic", KafkaConstants.LOG_TOPIC_PREFIX + serviceName);
            
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            
            // 更新 LoggerContext 属性，这些属性会被 kafka-appender.xml 中的 ${} 引用
            loggerContext.putProperty("HOST_IP", hostIp);
            loggerContext.putProperty(KafkaConstants.LOGBACK_PROP_HOST_NAME, hostName);
            loggerContext.putProperty(KafkaConstants.LOGBACK_PROP_HOST_IP, hostIp);
            loggerContext.putProperty(KafkaConstants.LOGBACK_PROP_HOST_PORT, serverPort);
            loggerContext.putProperty(KafkaConstants.LOGBACK_PROP_SERVICE, serviceName);
            loggerContext.putProperty(KafkaConstants.LOGBACK_PROP_ENV, env);
            loggerContext.putProperty(KafkaConstants.LOGBACK_PROP_TOPIC, KafkaConstants.LOG_TOPIC_PREFIX + serviceName);
            
            // 获取 ROOT logger 下的 KafkaAppender
            KafkaAppender kafkaAppender = (KafkaAppender) loggerContext.getLogger("ROOT")
                    .getAppender(KafkaConstants.KAFKA_APPENDER_NAME);

            if (kafkaAppender != null) {
                // 必须 stop 后修改再 start，否则配置不生效
                kafkaAppender.stop();
                
                // 重新设置生产者配置
                kafkaAppender.addProducerConfig("bootstrap.servers=" + kafkaBootstrap);
                kafkaAppender.addProducerConfig("acks=all");
                kafkaAppender.addProducerConfig("retries=2147483647");
                kafkaAppender.addProducerConfig("enable.idempotence=true");
                kafkaAppender.addProducerConfig("max.in.flight.requests.per.connection=5");
                kafkaAppender.addProducerConfig("linger.ms=5");
                kafkaAppender.addProducerConfig("batch.size=32768");
                kafkaAppender.addProducerConfig("buffer.memory=67108864");
                kafkaAppender.addProducerConfig("compression.type=snappy");
                
                // 特别注意：Encoder 的 Pattern 也需要更新，否则 JSON 中的字段还是旧的
                updateEncoderPattern(kafkaAppender, loggerContext, hostName, hostIp, serverPort, serviceName, env);
                
                kafkaAppender.start();
                
                log.info("Kafka配置更新成功|Kafka_logback_update_done,service={},ip={},port={}", 
                        serviceName, hostIp, serverPort);
            } else {
                log.warn("未找到指定的KafkaAppender|KafkaAppender_not_found,name={}", KafkaConstants.KAFKA_APPENDER_NAME);
            }
        } catch (Exception e) {
            log.error("更新Kafka配置失败|Kafka_logback_update_failed", e);
        }
    }

    /**
     * 手动更新 Encoder 的 JSON 模式
     * 解决 LogstashEncoder 不会自动刷新内部 Pattern 的问题
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
            
            // 重新构建 JSON 结构
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
        } catch (Exception e) {
            log.warn("更新 Encoder pattern 失败|Update_encoder_pattern_failed", e);
        }
    }

    /**
     * 获取最佳业务 IP
     */
    private String getBestHostIp() {
        try {
            String envHostIp = System.getenv("HOST_IP");
            if (envHostIp != null && !envHostIp.trim().isEmpty()) {
                return envHostIp.trim();
            }
            List<String> ips = IPUtil.getIP();
            if (!ips.isEmpty()) {
                // 优先 192.168
                for (String ip : ips) {
                    if (ip.startsWith("192.168.")) return ip;
                }
                return ips.get(0);
            }
            return "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }
}
