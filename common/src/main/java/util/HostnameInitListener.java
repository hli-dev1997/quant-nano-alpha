package util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;

/**
 * 主机名初始化监听器
 * <p>
 * 在 Spring 环境准备阶段设置主机名系统属性，
 * 解决 Windows 环境下 Spring Cloud InetUtils "Cannot determine local hostname" 警告。
 * </p>
 *
 * @author hli
 * @since 2026-01-18
 */
public class HostnameInitListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(HostnameInitListener.class);

    private static volatile boolean initialized = false;

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        if (initialized) {
            return;
        }

        synchronized (HostnameInitListener.class) {
            if (initialized) {
                return;
            }

            try {
                String hostname = HostUtils.getHostname();
                if (hostname != null && !hostname.isEmpty()) {
                    // 设置系统属性供其他组件使用
                    System.setProperty("host.name", hostname);
                    LOG.info("主机名初始化完成|Hostname_initialized,hostname={},os={}", 
                            hostname, HostUtils.getOsName());
                }
            } catch (Exception e) {
                LOG.warn("主机名初始化失败|Hostname_init_failed,error={}", e.getMessage());
            }

            initialized = true;
        }
    }
}
