package com.netease.nim.lbd.util;

import com.netease.nim.lbd.RealConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Created by caojiajun on 2026/1/9
 */
public class CloseUtils {

    private static final Logger logger = LoggerFactory.getLogger(CloseUtils.class);

    public static void close(Statement statement) {
        if (statement == null) {
            return;
        }
        try {
            statement.close();
        } catch (Exception e) {
            logger.debug("close statement error", e);
        }
    }

    public static void close(ResultSet resultSet) {
        if (resultSet == null) {
            return;
        }
        try {
            resultSet.close();
        } catch (Exception e) {
            logger.debug("close result set error", e);
        }
    }

    public static void close(RealConnection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (Exception e) {
            logger.debug("close real connection error", e);
        }
    }

}
