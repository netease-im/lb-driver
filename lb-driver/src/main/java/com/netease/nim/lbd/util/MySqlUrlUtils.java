package com.netease.nim.lbd.util;

import com.netease.nim.lbd.SqlProxy;
import com.netease.nim.lbd.Constants;

/**
 * Created by caojiajun on 2025/12/3
 */
public class MySqlUrlUtils {

    public static String url(SqlProxy sqlProxy, String database) {
        return Constants.MYSQL_DRIVER_URL_PREFIX + sqlProxy.getHost() + ':' + sqlProxy.getPort() + "/" +
                database;
    }
}
