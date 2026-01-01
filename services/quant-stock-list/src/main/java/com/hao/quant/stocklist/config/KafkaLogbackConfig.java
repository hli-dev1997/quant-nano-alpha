package com.hao.quant.stocklist.config;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.github.danielwegener.logback.kafka.KafkaAppender;
import integration.kafka.KafkaConstants;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.composite.loggingevent.LoggingEventJsonProviders;
import net.logstash.logback.composite.loggingevent.LoggingEventPatternJsonProvider;
import net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

@Slf4j
@Configuration
public class KafkaLogbackConfig implements ApplicationListener<ApplicationReadyEvent> {

    @Value("${spring.application.name:quant-stock-list}")
    private String serviceName;

    @Value("${spring.kafka.bootstrap-servers:192.168.254.2:9092,192.168.254.3:9092,192.168.254.4:9092}")
    private String kafkaBootstrap;

    @Value("${server.port:8806}")
    private String serverPort;

    @Value("${spring.profiles.active:dev}")
    private String env;

    @Override
    public void onApplicationEvent(@NonNull ApplicationReadyEvent event) {
        updateKafkaConfig();
    }

    public void updateKafkaConfig() {
        try {
            log.info("应用启动完成开始更新Kafka配置|App_ready_update_kafka_logback");
            
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            String hostIp = getBestHostIp();
            String hostName = getHostName();
            
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
                
                // 动态更新 Encoder Pattern
                updateEncoderPattern(kafkaAppender, env, serviceName, hostIp, serverPort);

                kafkaAppender.start();
                log.info("Kafka配置更新成功|Kafka_logback_update_done");
                
                // 打印关键信息确认
                log.info("Kafka集群|Kafka_bootstrap,bootstrap={}", kafkaBootstrap);
                log.info("主机名|Host_name,name={}", hostName);
                log.info("主机IP|Host_ip,ip={}", hostIp);
                log.info("服务端口|Service_port,port={}", serverPort);
                log.info("服务名|Service_name,name={}", serviceName);
                log.info("运行环境|Active_profile,profile={}", env);
                log.info("日志主题|Log_topic,topic={}", KafkaConstants.LOG_TOPIC_PREFIX + serviceName);
                // 打印InstanceId验证
                log.info("实例ID|Instance_id,instanceId={}-{}-{}", serviceName, hostIp, serverPort);

                // 发送一条测试日志
                log.info("Kafka日志推送恢复|Kafka_log_push_recovered");
            } else {
                log.warn("未找到KafkaAppender|Kafka_appender_not_found");
            }

        } catch (Exception e) {
            log.error("更新Kafka配置失败|Update_kafka_config_failed", e);
        }
    }

    private void updateEncoderPattern(KafkaAppender<ILoggingEvent> kafkaAppender, String env, String service, String hostIp, String port) {
        try {
            // 直接创建新的 Encoder，不依赖旧的
            LoggingEventCompositeJsonEncoder encoder = new LoggingEventCompositeJsonEncoder();
            encoder.setContext(kafkaAppender.getContext());

            // 创建新的 JsonProviders
            LoggingEventJsonProviders jsonProviders = new LoggingEventJsonProviders();
            jsonProviders.setContext(kafkaAppender.getContext());

            // 构建动态 JSON Pattern
            LoggingEventPatternJsonProvider patternProvider = new LoggingEventPatternJsonProvider();
            patternProvider.setContext(kafkaAppender.getContext());
            
            // 使用实际值替换占位符构建 JSON pattern
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
            
            jsonProviders.addProvider(patternProvider);
            
            // 重新设置 Providers
            encoder.setProviders(jsonProviders);
            encoder.start();
            
            // 将新 Encoder 设置给 Appender
            kafkaAppender.setEncoder(encoder);
            
            log.info("动态EncoderPattern更新成功|Dynamic_encoder_pattern_updated");
        } catch (Exception e) {
            log.error("动态更新EncoderPattern失败|Dynamic_update_encoder_failed", e);
        }
    }

    private String getBestHostIp() {
        try {
            // 1. 尝试获取 HOST_IP 环境变量 (容器环境)
            String envHostIp = System.getenv("HOST_IP");
            if (envHostIp != null && !envHostIp.isEmpty()) {
                return envHostIp;
            }

            // 2. 获取本机网络接口 IP
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                // 排除回环接口、未启动接口、虚拟接口
                if (networkInterface.isLoopback() || !networkInterface.isUp() || networkInterface.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    // 仅获取 IPv4 地址，且不是回环地址
                    if (!address.isLoopbackAddress() && address.getHostAddress().indexOf(':') == -1) {
                        return address.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            log.warn("无法获取本机IP|Get_host_ip_failed", e);
            return "unknown";
        }
    }
    
    private String getHostName() {
         try {
             return InetAddress.getLocalHost().getHostName();
         } catch (Exception e) {
             return "unknown";
         }
    }
}
