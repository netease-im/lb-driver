package com.netease.nim.lbd.test;

import com.netease.nim.lbd.LBDriverUrl;
import com.netease.nim.lbd.UnsupportedMethodBehavior;
import com.netease.nim.lbd.util.LBDriverUrlParser;
import org.junit.Assert;
import org.junit.Test;

import java.sql.SQLException;
import java.util.Properties;

/**
 * Created by caojiajun on 2025/12/3
 */
public class LBDriverUrlTest {

    @Test
    public void test1() throws SQLException {
        String url = "jdbc:mysql:lb:remote://10.0.0.1:80/mydatabase?connectTimeout=5000&socketTimeout=60000";
        LBDriverUrl lbDriverUrl = LBDriverUrlParser.parseUrl(url, new Properties());
        Assert.assertNotNull(lbDriverUrl);
        Assert.assertEquals("mydatabase", lbDriverUrl.getSchemaName());
        Assert.assertEquals("10.0.0.1", lbDriverUrl.getConfigServerHost());
        Assert.assertEquals(80, lbDriverUrl.getConfigServerPort());
        Assert.assertEquals(UnsupportedMethodBehavior.ThrowException, lbDriverUrl.getUnsupportedMethodBehavior());
        Assert.assertNull(lbDriverUrl.getConfigServerApiKey());
        Assert.assertEquals(2, lbDriverUrl.getInfo().size());
    }

    @Test
    public void test2() throws SQLException {
        String url = "jdbc:mysql:lb:remote://10.0.0.1:80/mydatabase?connectTimeout=5000&socketTimeout=60000&configServerApiKey=abc&unsupportedMethodBehavior=ignoreCall";
        LBDriverUrl lbDriverUrl = LBDriverUrlParser.parseUrl(url, new Properties());
        Assert.assertNotNull(lbDriverUrl);
        Assert.assertEquals("mydatabase", lbDriverUrl.getSchemaName());
        Assert.assertEquals("10.0.0.1", lbDriverUrl.getConfigServerHost());
        Assert.assertEquals(80, lbDriverUrl.getConfigServerPort());
        Assert.assertEquals(UnsupportedMethodBehavior.IgnoreCall, lbDriverUrl.getUnsupportedMethodBehavior());
        Assert.assertEquals("abc", lbDriverUrl.getConfigServerApiKey());
        Assert.assertEquals(2, lbDriverUrl.getInfo().size());
    }

    @Test
    public void test3() throws SQLException {
        String url = "jdbc:mysql:lb:remote://10.0.0.1:80/mydatabase?connectTimeout=5000&socketTimeout=60000&configServerApiKey=abc&unsupportedMethodBehavior=ignoreCall&checkBalanceIntervalSeconds=3&checkHealthIntervalSeconds=4&configServerTimeout=6000";
        LBDriverUrl lbDriverUrl = LBDriverUrlParser.parseUrl(url, new Properties());
        Assert.assertNotNull(lbDriverUrl);
        Assert.assertEquals("mydatabase", lbDriverUrl.getSchemaName());
        Assert.assertEquals("10.0.0.1", lbDriverUrl.getConfigServerHost());
        Assert.assertEquals(80, lbDriverUrl.getConfigServerPort());
        Assert.assertEquals(UnsupportedMethodBehavior.IgnoreCall, lbDriverUrl.getUnsupportedMethodBehavior());
        Assert.assertEquals("abc", lbDriverUrl.getConfigServerApiKey());
        Assert.assertEquals(2, lbDriverUrl.getInfo().size());
        Assert.assertEquals(3, lbDriverUrl.getCheckBalanceIntervalSeconds());
        Assert.assertEquals(4, lbDriverUrl.getCheckHealthIntervalSeconds());
        Assert.assertEquals(6000, lbDriverUrl.getConfigServerTimeout());
    }
}
