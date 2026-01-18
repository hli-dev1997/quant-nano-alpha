package util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.nio.charset.Charset;

/**
 * 跨平台主机名工具类
 * <p>
 * 解决 Windows 环境下 Spring Cloud InetUtils 无法获取主机名的问题。
 * 在 Windows 上使用 COMPUTERNAME 环境变量，Linux/Mac 使用标准Java API。
 * </p>
 *
 * @author hli
 * @since 2026-01-18
 */
public class HostUtils {

    private static final Logger LOG = LoggerFactory.getLogger(HostUtils.class);

    /**
     * 操作系统类型
     */
    private static final String OS_NAME = System.getProperty("os.name", "").toLowerCase();

    /**
     * 是否为 Windows 系统
     */
    private static final boolean IS_WINDOWS = OS_NAME.contains("windows");

    /**
     * 是否为 Linux 系统
     */
    private static final boolean IS_LINUX = OS_NAME.contains("linux");

    /**
     * 是否为 Mac 系统
     */
    private static final boolean IS_MAC = OS_NAME.contains("mac");

    /**
     * 缓存的主机名
     */
    private static volatile String cachedHostname;

    /**
     * 获取本机主机名（跨平台）
     * <p>
     * 优先级：
     * 1. Windows: COMPUTERNAME 环境变量
     * 2. Linux/Mac: HOSTNAME 环境变量
     * 3. Java InetAddress.getLocalHost()
     * 4. hostname 命令
     * 5. 默认值 "localhost"
     * </p>
     *
     * @return 主机名
     */
    public static String getHostname() {
        if (cachedHostname != null) {
            return cachedHostname;
        }

        synchronized (HostUtils.class) {
            if (cachedHostname != null) {
                return cachedHostname;
            }

            String hostname = null;

            // 1. 尝试从环境变量获取
            hostname = getHostnameFromEnv();
            if (isValidHostname(hostname)) {
                LOG.debug("主机名从环境变量获取成功|Hostname_from_env,hostname={}", hostname);
                cachedHostname = hostname;
                return cachedHostname;
            }

            // 2. 尝试使用 Java API
            hostname = getHostnameFromJava();
            if (isValidHostname(hostname)) {
                LOG.debug("主机名从JavaAPI获取成功|Hostname_from_java,hostname={}", hostname);
                cachedHostname = hostname;
                return cachedHostname;
            }

            // 3. 尝试使用系统命令
            hostname = getHostnameFromCommand();
            if (isValidHostname(hostname)) {
                LOG.debug("主机名从系统命令获取成功|Hostname_from_command,hostname={}", hostname);
                cachedHostname = hostname;
                return cachedHostname;
            }

            // 4. 使用默认值
            LOG.warn("无法获取主机名使用默认值|Hostname_fallback_to_default,default=localhost");
            cachedHostname = "localhost";
            return cachedHostname;
        }
    }

    /**
     * 从环境变量获取主机名
     */
    private static String getHostnameFromEnv() {
        if (IS_WINDOWS) {
            // Windows 使用 COMPUTERNAME
            return System.getenv("COMPUTERNAME");
        } else {
            // Linux/Mac 使用 HOSTNAME 或 HOST
            String hostname = System.getenv("HOSTNAME");
            if (!isValidHostname(hostname)) {
                hostname = System.getenv("HOST");
            }
            return hostname;
        }
    }

    /**
     * 使用 Java API 获取主机名
     */
    private static String getHostnameFromJava() {
        try {
            InetAddress addr = InetAddress.getLocalHost();
            return addr.getHostName();
        } catch (Exception e) {
            LOG.debug("Java主机名获取失败|Java_hostname_failed,error={}", e.getMessage());
            return null;
        }
    }

    /**
     * 使用系统命令获取主机名
     */
    private static String getHostnameFromCommand() {
        String command = IS_WINDOWS ? "hostname" : "hostname";
        try {
            ProcessBuilder pb = new ProcessBuilder();
            if (IS_WINDOWS) {
                pb.command("cmd.exe", "/c", command);
            } else {
                pb.command("/bin/sh", "-c", command);
            }

            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), 
                            IS_WINDOWS ? Charset.forName("GBK") : Charset.defaultCharset()))) {
                String line = reader.readLine();
                process.waitFor();
                return line != null ? line.trim() : null;
            }
        } catch (Exception e) {
            LOG.debug("系统命令获取主机名失败|Command_hostname_failed,error={}", e.getMessage());
            return null;
        }
    }

    /**
     * 检查主机名是否有效
     */
    private static boolean isValidHostname(String hostname) {
        return hostname != null && !hostname.isEmpty() && !hostname.equalsIgnoreCase("localhost");
    }

    /**
     * 判断是否为 Windows 系统
     */
    public static boolean isWindows() {
        return IS_WINDOWS;
    }

    /**
     * 判断是否为 Linux 系统
     */
    public static boolean isLinux() {
        return IS_LINUX;
    }

    /**
     * 判断是否为 Mac 系统
     */
    public static boolean isMac() {
        return IS_MAC;
    }

    /**
     * 获取操作系统名称
     */
    public static String getOsName() {
        return OS_NAME;
    }
}
