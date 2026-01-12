package com.netease.nim.lbd;

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

}
