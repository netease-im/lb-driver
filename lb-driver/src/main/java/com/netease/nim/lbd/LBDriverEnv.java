package com.netease.nim.lbd;

import java.sql.Driver;
import java.sql.DriverManager;

/**
 * LBDriverEnv
 * Created by caojiajun on 2025/12/3
 */
public class LBDriverEnv {

    private static Driver realDriver;

    /**
     * init method
     */
    public static void init() {
        try {
            DriverManager.registerDriver(new LBDriver());
            realDriver = (Driver) Class.forName(Constants.MYSQL_DRIVER_CLASS_NAME).newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Can't register LBDriver", e);
        }
    }

    /**
     * getRealDriver
     * @return Driver
     */
    public static Driver getRealDriver() {
        return realDriver;
    }
}
