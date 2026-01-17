package com.xxl.job.admin.config;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.github.danielwegener.logback.kafka.KafkaAppender;
import integration.kafka.KafkaConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.lang.NonNull;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * Kafka Logback 配置热更新器
 */
@Component
public class KafkaLogbackConfig implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(KafkaLogbackConfig.class);

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String kafkaBootstrap;

    @Value("${spring.profiles.active:dev}")
    private String env;

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${spring.application.name:xxl-job}")
    private String serviceName;

    @Override
    public void onApplicationEvent(@NonNull ApplicationReadyEvent event) {
        log.info("应用启动完成开始更新Kafka配置|App_ready_update_kafka_logback");
        updateKafkaConfig();
    }

    public void updateKafkaConfig() {
        try {
            String hostName = getHostName();
            String hostIp = getBestHostIp();
            
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
            
            KafkaAppender<ILoggingEvent> kafkaAppender = (KafkaAppender<ILoggingEvent>) loggerContext.getLogger("ROOT")
                    .getAppender(KafkaConstants.KAFKA_APPENDER_NAME);

            if (kafkaAppender != null) {
                log.info("找到KafkaAppender开始更新配置|Kafka_appender_found_update_start");
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
                
                // 更新 encoder 的 pattern，注入实际的主机信息
                updateEncoderPattern(kafkaAppender, loggerContext, hostName, hostIp, serverPort, serviceName, env);
                
                kafkaAppender.start();
                
                log.info("Kafka配置更新成功|Kafka_logback_update_done");
                log.info("主机IP|Host_ip,ip={}", hostIp);
                log.info("服务名|Service_name,name={}", serviceName);
                log.info("实例ID|Instance_id,instanceId={}-{}-{}", serviceName, hostIp, serverPort);
            } else {
                log.warn("未找到KafkaAppender|Kafka_appender_not_found");
            }
        } catch (Exception e) {
            log.error("更新Kafka配置失败|Kafka_logback_update_failed,error={}", e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private void updateEncoderPattern(KafkaAppender<ILoggingEvent> kafkaAppender, LoggerContext loggerContext,
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
            log.debug("Encoder_pattern更新成功|Encoder_pattern_updated");
        } catch (Exception e) {
            log.warn("更新Encoder_pattern失败|Encoder_pattern_update_failed,error={}", e.getMessage());
        }
    }

    private String getHostName() {
        try {
            String envHostName = System.getenv("HOSTNAME");
            if (envHostName != null && !envHostName.trim().isEmpty()) {
                return envHostName.trim();
            }
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String getBestHostIp() {
        try {
            String envHostIp = System.getenv("HOST_IP");
            if (envHostIp != null && !envHostIp.trim().isEmpty()) {
                return envHostIp.trim();
            }
            
            String bestIp = null;
            int bestScore = -1;
            
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) continue;
                
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address.isLoopbackAddress() || !address.isSiteLocalAddress()) continue;
                    
                    String ip = address.getHostAddress();
                    int score = calculateIpScore(ip, networkInterface.getName());
                    if (score > bestScore) {
                        bestScore = score;
                        bestIp = ip;
                    }
                }
            }
            return bestIp != null ? bestIp : InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private int calculateIpScore(String ip, String interfaceName) {
        int score = 0;
        if (ip.contains(".")) score += 10;
        if (ip.startsWith("192.168.")) score += 30;
        else if (ip.startsWith("10.")) score += 20;
        
        String lowerName = interfaceName.toLowerCase();
        if (lowerName.startsWith("eth") || lowerName.startsWith("en")) score += 20;
        else if (lowerName.contains("docker") || lowerName.contains("veth")) score -= 10;
        return score;
    }
}
