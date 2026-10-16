package com.netease.nim.lbd;

import java.io.InputStream;
import java.util.Properties;

/**
 * Created by caojiajun on 2025/12/3
 */
public class Constants {

    /**
     * driver
     */
    public static String MYSQL_DRIVER_CLASS_NAME = "com.mysql.jdbc.Driver";

    /**
     * url prefix
     */
    public static final String LB_DRIVER_URL_PREFIX = "jdbc:mysql:lb:";

    /**
     * url prefix for local mode
     */
    public static final String LB_DRIVER_LOCAL_URL_PREFIX = "jdbc:mysql:lb:local://";

    /**
     * url prefix for remote mode
     */
    public static final String LB_DRIVER_REMOTE_URL_PREFIX = "jdbc:mysql:lb:remote://";

    /**
     * mysql url prefix
     */
    public static final String MYSQL_DRIVER_URL_PREFIX = "jdbc:mysql://";

    /**
     * select 1 for health check
     */
    public static final String VALIDATION_QUERY = "/* ping */ SELECT 1";

    /**
     * default UnsupportedMethodBehavior
     */
    public static final UnsupportedMethodBehavior UNSUPPORTED_METHOD_BEHAVIOR = UnsupportedMethodBehavior.ThrowException;

    /**
     * default log stats
     */
    public static final boolean LOG_STATS = false;

    /**
     * default CHECK_BALANCE_INTERVAL_SECONDS
     */
    public static final int CHECK_BALANCE_INTERVAL_SECONDS = 10;

    /**
     * default CHECK_HEALTH_INTERVAL_SECONDS
     */
    public static final int CHECK_HEALTH_INTERVAL_SECONDS = 5;

    /**
     * default CONFIG_SERVER_TIMEOUT
     */
    public static final int CONFIG_SERVER_TIMEOUT = 5000;

    /**
     * default EXCEPTION_SORTER_CLASS_NAME
     */
    public static final String EXCEPTION_SORTER_CLASS_NAME = MySqlExceptionSorter.class.getName();

    /**
     * default AZ_REFRESH_INTERVAL_SECONDS，降级期间刷新 az 亲和集合拓扑的间隔
     */
    public static final int AZ_REFRESH_INTERVAL_SECONDS = 30;

    /**
     * default RECOVER_CHECK_COUNT，节点由不可达翻回可达所需的连续健康检查次数
     */
    public static final int RECOVER_CHECK_COUNT = 2;

    /**
     * client feature level，取值来自 ClientFeatureLevel.CURRENT，见 ClientFeatureLevel
     */
    public static final int CLIENT_FEATURE_LEVEL = ClientFeatureLevel.CURRENT.getLevel();

    /**
     * 无法解析出客户端版本时使用的占位值
     */
    public static final String UNKNOWN_CLIENT_VERSION = "unknown";

    /**
     * client version，仅用于 config-server 日志与灰度观测，不参与判定
     */
    public static final String CLIENT_VERSION = resolveClientVersion();

    private static String resolveClientVersion() {
        try (InputStream inputStream = Constants.class.getClassLoader()
                .getResourceAsStream("META-INF/maven/com.netease.nim/lb-driver/pom.properties")) {
            if (inputStream == null) {
                return UNKNOWN_CLIENT_VERSION;
            }
            Properties properties = new Properties();
            properties.load(inputStream);
            String version = properties.getProperty("version");
            return version != null ? version : UNKNOWN_CLIENT_VERSION;
        } catch (Exception e) {
            return UNKNOWN_CLIENT_VERSION;
        }
    }

}
