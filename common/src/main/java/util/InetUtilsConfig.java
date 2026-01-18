package util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.cloud.commons.util.InetUtilsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.net.InetAddress;

/**
 * Spring Cloud InetUtils 自定义配置
 * <p>
 * 解决 Windows 环境下 "Cannot determine local hostname" 警告问题。
 * 通过自定义 InetUtils Bean 在 Windows 上使用环境变量获取主机名。
 * </p>
 *
 * @author hli
 * @since 2026-01-18
 */
@Configuration
public class InetUtilsConfig {

    private static final Logger LOG = LoggerFactory.getLogger(InetUtilsConfig.class);

    /**
     * 自定义 InetUtils Bean
     * <p>
     * 使用 @Primary 注解覆盖 Spring Cloud 默认的 InetUtils。
     * 在获取主机名前先尝试使用 HostUtils 的跨平台方法。
     * </p>
     */
    @Bean
    @Primary
    public InetUtils inetUtils(InetUtilsProperties properties) {
        // 在创建 InetUtils 之前，先预热 HostUtils 的主机名缓存
        String hostname = HostUtils.getHostname();
        LOG.info("主机名初始化完成|Hostname_initialized,hostname={},os={}", hostname, HostUtils.getOsName());

        // 在 Windows 上设置 hostname 到系统属性，供后续使用
        if (HostUtils.isWindows() && hostname != null && !hostname.isEmpty()) {
            // 设置 JVM 系统属性 (某些库会读取这个)
            System.setProperty("hostname", hostname);

            // 尝试更新 InetAddress 缓存
            try {
                InetAddress.getByName(hostname);
            } catch (Exception e) {
                LOG.debug("InetAddress缓存更新失败|InetAddress_cache_update_failed,error={}", e.getMessage());
            }
        }

        return new InetUtils(properties);
    }
}
