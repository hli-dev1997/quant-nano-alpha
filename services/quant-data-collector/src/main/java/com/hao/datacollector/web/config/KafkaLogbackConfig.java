package com.hao.datacollector.web.config;

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
 * 使用 common 模块的工具类进行重构，保持全局一致性。
 */
@Slf4j
@Component
@RefreshScope
public class KafkaLogbackConfig implements ApplicationListener<ApplicationReadyEvent> {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String kafkaBootstrap;

    @Value("${spring.profiles.active:dev}")
    private String env;

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${spring.application.name:data-collector}")
    private String serviceName;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("应用启动完成开始更新Kafka配置|App_ready_update_kafka_logback");
        updateKafkaConfig();
    }

    public void updateKafkaConfig() {
        try {
            String hostName = HostUtils.getHostname();
            String hostIp = getBestHostIp();
            
            // 设置系统属性
            System.setProperty("HOST_IP", hostIp);
            System.setProperty("server.port", serverPort);
            System.setProperty("spring.application.name", serviceName);
            System.setProperty("spring.profiles.active", env);
            System.setProperty("logging.kafka.topic", KafkaConstants.LOG_TOPIC_PREFIX + serviceName);
            
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            
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
                kafkaAppender.stop();
                kafkaAppender.addProducerConfig("bootstrap.servers=" + kafkaBootstrap);
                kafkaAppender.addProducerConfig("acks=all");
                kafkaAppender.addProducerConfig("retries=2147483647");
                kafkaAppender.addProducerConfig("enable.idempotence=true");
                kafkaAppender.addProducerConfig("max.in.flight.requests.per.connection=5");
                kafkaAppender.addProducerConfig("linger.ms=5");
                kafkaAppender.addProducerConfig("batch.size=32768");
                kafkaAppender.addProducerConfig("buffer.memory=67108864");
                kafkaAppender.addProducerConfig("compression.type=snappy");
                
                updateEncoderPattern(kafkaAppender, loggerContext, hostName, hostIp, serverPort, serviceName, env);
                kafkaAppender.start();
                
                log.info("Kafka配置更新成功|Kafka_logback_update_done,service={},ip={},port={}", serviceName, hostIp, serverPort);
            }
        } catch (Exception e) {
            log.error("更新Kafka配置失败|Kafka_logback_update_failed", e);
        }
    }

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
        } catch (Exception e) {
            log.warn("更新 Encoder pattern 失败", e);
        }
    }

    private String getBestHostIp() {
        try {
            String envHostIp = System.getenv("HOST_IP");
            if (envHostIp != null && !envHostIp.trim().isEmpty()) {
                return envHostIp.trim();
            }
            List<String> ips = IPUtil.getIP();
            if (!ips.isEmpty()) {
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
