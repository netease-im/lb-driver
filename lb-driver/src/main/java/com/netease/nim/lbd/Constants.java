package com.netease.nim.lbd;

/**
 * Created by caojiajun on 2025/12/3
 */
public class Constants {

    public static String MYSQL_DRIVER_CLASS_NAME = "com.mysql.jdbc.Driver";

    public static final String LB_DRIVER_URL_PREFIX = "jdbc:mysql:lb:";

    public static final String LB_DRIVER_LOCAL_URL_PREFIX = "jdbc:mysql:lb:local://";
    public static final String LB_DRIVER_REMOTE_URL_PREFIX = "jdbc:mysql:lb:remote://";

    public static final String MYSQL_DRIVER_URL_PREFIX = "jdbc:mysql://";

    public static final String VALIDATION_QUERY = "/* ping */ SELECT 1";

    public static final UnsupportedMethodBehavior UNSUPPORTED_METHOD_BEHAVIOR = UnsupportedMethodBehavior.ThrowException;
    public static final boolean LOG_STATS = false;
    public static final int CHECK_BALANCE_INTERVAL_SECONDS = 10;
    public static final int CHECK_HEALTH_INTERVAL_SECONDS = 5;
    public static final int CONFIG_SERVER_TIMEOUT = 5000;
    public static final String EXCEPTION_SORTER_CLASS_NAME = MySqlExceptionSorter.class.getName();

}
