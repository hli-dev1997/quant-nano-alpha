package com.xxl.job.admin.core.conf;

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
 * Kafka Logback 配置热更新器 - quant-xxl-job
 */
@Slf4j
@Component
@RefreshScope
public class KafkaLogbackConfig implements ApplicationListener<ApplicationReadyEvent> {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String kafkaBootstrap;

    @Value("${spring.profiles.active:dev}")
    private String env;

    @Value("${server.port:8806}")
    private String serverPort;

    @Value("${spring.application.name:xxl-job}")
    private String serviceName;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        updateKafkaConfig();
    }

    public void updateKafkaConfig() {
        try {
            String hostName = HostUtils.getHostname();
            String hostIp = getBestHostIp();
            
            System.setProperty("HOST_IP", hostIp);
            
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
                updateEncoderPattern(kafkaAppender, loggerContext, hostIp, serverPort, serviceName, env);
                kafkaAppender.start();
                log.info("Kafka Logback 配置更新成功 | service={}, ip={}", serviceName, hostIp);
            }
        } catch (Exception e) {
            log.error("Kafka Logback 配置更新失败", e);
        }
    }

    private void updateEncoderPattern(KafkaAppender kafkaAppender, LoggerContext loggerContext,
                                       String hostIp, String port, String service, String env) {
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
                {"env":"%s","service":"%s","ip":"%s","port":"%s","level":"%%level","thread":"%%thread","logger":"%%logger{36}","timestamp":"%%date{yyyy-MM-dd'T'HH:mm:ss.SSS'Z'}","message":"%%msg","exception":"%%exception"}
                """, env, service, hostIp, port);
            
            patternProvider.setPattern(pattern);
            providers.addProvider(patternProvider);
            newEncoder.setProviders(providers);
            newEncoder.start();
            kafkaAppender.setEncoder(newEncoder);
        } catch (Exception e) {
            log.warn("Encoder pattern 更新失败", e);
        }
    }

    private String getBestHostIp() {
        try {
            List<String> ips = IPUtil.getIP();
            for (String ip : ips) if (ip.startsWith("192.168.")) return ip;
            return ips.isEmpty() ? "unknown" : ips.get(0);
        } catch (Exception e) { return "unknown"; }
    }
}
