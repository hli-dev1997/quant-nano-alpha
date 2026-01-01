package com.hao.strategyengine.config;

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

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

import ch.qos.logback.classic.spi.ILoggingEvent;
import net.logstash.logback.composite.loggingevent.LoggingEventJsonProviders;
import net.logstash.logback.composite.loggingevent.LoggingEventPatternJsonProvider;
import net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder;

/**
 * Kafka Logback 配置热更新器
 *
 * <p>问题背景：
 * - logback 在 Spring 容器启动之前就初始化，此时 Nacos 配置还未加载
 * - 导致 logback-spring.xml 中无法获取到正确的 Kafka 集群地址和主机信息
 *
 * <p>解决方案：
 * - 在应用完全启动后（ApplicationReadyEvent），重新配置 Kafka Appender
 * - 使用 LoggerContext.putProperty() 设置属性供 %property{...} 动态引用
 *
 * @author quant-team
 * @since 2025-10-22
 */
@Slf4j
@Component
@RefreshScope
public class KafkaLogbackConfig implements ApplicationListener<ApplicationReadyEvent> {

    @Value("${spring.kafka.bootstrap-servers}")
    private String kafkaBootstrap;

    @Value("${spring.profiles.active:dev}")
    private String env;

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${spring.application.name:strategy-engine}")
    private String serviceName;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("应用启动完成开始更新Kafka配置|App_ready_update_kafka_logback");
        updateKafkaConfig();
    }

    public void updateKafkaConfig() {
        try {
            // 获取主机信息
            String hostName = getHostName();
            String hostIp = getBestHostIp();
            
            // 设置系统属性
            System.setProperty("HOST_IP", hostIp);
            System.setProperty("server.port", serverPort);
            System.setProperty("spring.application.name", serviceName);
            System.setProperty("spring.profiles.active", env);
            System.setProperty("logging.kafka.topic", KafkaConstants.LOG_TOPIC_PREFIX + serviceName);
            
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            
            // 使用 LoggerContext.putProperty() 设置属性供 %property{...} 动态引用
            loggerContext.putProperty("HOST_IP", hostIp);
            loggerContext.putProperty(KafkaConstants.LOGBACK_PROP_HOST_NAME, hostName);
            loggerContext.putProperty(KafkaConstants.LOGBACK_PROP_HOST_IP, hostIp);
            loggerContext.putProperty(KafkaConstants.LOGBACK_PROP_HOST_PORT, serverPort);
            loggerContext.putProperty(KafkaConstants.LOGBACK_PROP_SERVICE, serviceName);
            loggerContext.putProperty(KafkaConstants.LOGBACK_PROP_ENV, env);
            loggerContext.putProperty(KafkaConstants.LOGBACK_PROP_TOPIC, KafkaConstants.LOG_TOPIC_PREFIX + serviceName);
            
            // Fix Raw Type Warning
            KafkaAppender<ILoggingEvent> kafkaAppender = (KafkaAppender<ILoggingEvent>) loggerContext.getLogger("ROOT")
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
                
                // 更新 encoder 的 pattern，注入实际的主机信息
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
                log.info("Kafka日志推送恢复|Kafka_log_push_recovered");
                
            } else {
                log.warn("未找到KafkaAppender|Kafka_appender_not_found,appenderName={}", KafkaConstants.KAFKA_APPENDER_NAME);
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
            // 直接创建新的 Encoder，不依赖旧的
            LoggingEventCompositeJsonEncoder encoder = new LoggingEventCompositeJsonEncoder();
            encoder.setContext(loggerContext);

            LoggingEventJsonProviders providers = 
                new LoggingEventJsonProviders();
            providers.setContext(loggerContext);
            
            LoggingEventPatternJsonProvider patternProvider = 
                new LoggingEventPatternJsonProvider();
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
            
            encoder.setProviders(providers);
            encoder.start();
            
            kafkaAppender.setEncoder(encoder);
            log.debug("Encoder pattern 更新成功|Encoder_pattern_updated");
        } catch (Exception e) {
            log.warn("更新 Encoder pattern 失败|Encoder_pattern_update_failed,error={}", e.getMessage());
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
            log.debug("获取主机名失败|Host_name_load_failed,error={}", e.getMessage());
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
                
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    
                    if (address.isLoopbackAddress() || !address.isSiteLocalAddress()) {
                        continue;
                    }
                    
                    String ip = address.getHostAddress();
                    int score = calculateIpScore(ip, networkInterface.getName());
                    
                    if (score > bestScore) {
                        bestScore = score;
                        bestIp = ip;
                    }
                }
            }
            
            if (bestIp != null) {
                return bestIp;
            }
            
            return InetAddress.getLocalHost().getHostAddress();
            
        } catch (Exception e) {
            log.debug("获取主机IP失败|Host_ip_load_failed,error={}", e.getMessage());
            return "unknown";
        }
    }

    private int calculateIpScore(String ip, String interfaceName) {
        int score = 0;
        
        if (ip.contains(".")) {
            score += 10;
        }
        
        if (ip.startsWith("192.168.")) {
            score += 30;
        } else if (ip.startsWith("10.")) {
            score += 20;
        } else if (ip.matches("172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")) {
            score += 15;
        }
        
        String lowerName = interfaceName.toLowerCase();
        if (lowerName.startsWith("eth") || lowerName.startsWith("en")) {
            score += 20;
        } else if (lowerName.contains("docker") || lowerName.contains("veth") || 
                   lowerName.contains("br-") || lowerName.contains("virbr")) {
            score -= 10;
        }
        
        return score;
    }
}
