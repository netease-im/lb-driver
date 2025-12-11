package com.netease.nim.lbd;

import java.sql.Driver;
import java.sql.DriverManager;

/**
 * Created by caojiajun on 2025/12/3
 */
public class LBDriverEnv {

    private static Driver realDriver;

    public static void init() {
        try {
            DriverManager.registerDriver(new LBDriver());
            realDriver = (Driver) Class.forName(Constants.MYSQL_DRIVER_CLASS_NAME).newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Can't register LBDriver", e);
        }
    }

    public static Driver getRealDriver() {
        return realDriver;
    }
}
