package com.netease.nim.lbd.test;

import com.netease.nim.lbd.Constants;
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

    @Test
    public void test4() throws SQLException {
        String url = "jdbc:mysql:lb:remote://config-server.xxx.com/mydatabase?connectTimeout=5000&socketTimeout=60000";
        LBDriverUrl lbDriverUrl = LBDriverUrlParser.parseUrl(url, new Properties());
        Assert.assertNotNull(lbDriverUrl);
        Assert.assertEquals("mydatabase", lbDriverUrl.getSchemaName());
        Assert.assertEquals("config-server.xxx.com", lbDriverUrl.getConfigServerHost());
        Assert.assertEquals(80, lbDriverUrl.getConfigServerPort());
        Assert.assertEquals(2, lbDriverUrl.getInfo().size());
    }

    @Test
    public void testMultiAzParams() throws SQLException {
        String url = "jdbc:mysql:lb:remote://10.0.0.1:80/mydatabase?connectTimeout=5000&socketTimeout=60000"
                + "&az=az1&ip=10.1.1.1&azRefreshIntervalSeconds=15&recoverCheckCount=3";
        LBDriverUrl lbDriverUrl = LBDriverUrlParser.parseUrl(url, new Properties());
        Assert.assertNotNull(lbDriverUrl);
        Assert.assertEquals("az1", lbDriverUrl.getAz());
        Assert.assertEquals("10.1.1.1", lbDriverUrl.getIp());
        Assert.assertEquals(15, lbDriverUrl.getAzRefreshIntervalSeconds());
        Assert.assertEquals(3, lbDriverUrl.getRecoverCheckCount());
        //多 az 参数不进入透传给 mysql driver 的 info
        Assert.assertEquals(2, lbDriverUrl.getInfo().size());
    }

    @Test
    public void testMultiAzParamsDefaultValue() throws SQLException {
        String url = "jdbc:mysql:lb:remote://10.0.0.1:80/mydatabase?connectTimeout=5000&socketTimeout=60000";
        LBDriverUrl lbDriverUrl = LBDriverUrlParser.parseUrl(url, new Properties());
        Assert.assertNotNull(lbDriverUrl);
        Assert.assertNull(lbDriverUrl.getAz());
        Assert.assertNull(lbDriverUrl.getIp());
        Assert.assertEquals(Constants.AZ_REFRESH_INTERVAL_SECONDS, lbDriverUrl.getAzRefreshIntervalSeconds());
        Assert.assertEquals(Constants.RECOVER_CHECK_COUNT, lbDriverUrl.getRecoverCheckCount());
    }

    @Test
    public void testMultiAzParamsIllegalValue() throws SQLException {
        String url = "jdbc:mysql:lb:remote://10.0.0.1:80/mydatabase?connectTimeout=5000&socketTimeout=60000"
                + "&azRefreshIntervalSeconds=0&recoverCheckCount=-1";
        LBDriverUrl lbDriverUrl = LBDriverUrlParser.parseUrl(url, new Properties());
        Assert.assertNotNull(lbDriverUrl);
        //非法数值回退默认值并打 WARN
        Assert.assertNull(lbDriverUrl.getAz());
        Assert.assertEquals(Constants.AZ_REFRESH_INTERVAL_SECONDS, lbDriverUrl.getAzRefreshIntervalSeconds());
        Assert.assertEquals(Constants.RECOVER_CHECK_COUNT, lbDriverUrl.getRecoverCheckCount());
    }

    @Test
    public void testSystemPropertyOverridesUrlParams() throws SQLException {
        String url = "jdbc:mysql:lb:remote://10.0.0.1:80/mydatabase?connectTimeout=5000&socketTimeout=60000"
                + "&az=az1&ip=10.1.1.1";
        System.setProperty("lbd.az", "az9");
        System.setProperty("lbd.ip", "10.9.9.9");
        try {
            LBDriverUrl lbDriverUrl = LBDriverUrlParser.parseUrl(url, new Properties());
            //-D 优先级最高：覆盖 url 上的同名参数
            Assert.assertEquals("az9", lbDriverUrl.getAz());
            Assert.assertEquals("10.9.9.9", lbDriverUrl.getIp());
        } finally {
            System.clearProperty("lbd.az");
            System.clearProperty("lbd.ip");
        }
    }

    @Test
    public void testSystemPropertyOverridesPartially() throws SQLException {
        String url = "jdbc:mysql:lb:remote://10.0.0.1:80/mydatabase?connectTimeout=5000&socketTimeout=60000&ip=10.1.1.1";
        System.setProperty("lbd.az", " az2 ");
        try {
            LBDriverUrl lbDriverUrl = LBDriverUrlParser.parseUrl(url, new Properties());
            //只配了一边时另一边仍取 url 参数；-D 的值会去掉首尾空白
            Assert.assertEquals("az2", lbDriverUrl.getAz());
            Assert.assertEquals("10.1.1.1", lbDriverUrl.getIp());
        } finally {
            System.clearProperty("lbd.az");
        }
    }

    @Test
    public void testEmptySystemPropertyClearsUrlParam() throws SQLException {
        String url = "jdbc:mysql:lb:remote://10.0.0.1:80/mydatabase?connectTimeout=5000&socketTimeout=60000"
                + "&az=az1&ip=10.1.1.1";
        System.setProperty("lbd.az", "");
        System.setProperty("lbd.ip", "  ");
        try {
            LBDriverUrl lbDriverUrl = LBDriverUrlParser.parseUrl(url, new Properties());
            //空串等价于「不配置」：共享 url 上配了、个别机器不想要时用得上
            Assert.assertNull(lbDriverUrl.getAz());
            Assert.assertNull(lbDriverUrl.getIp());
        } finally {
            System.clearProperty("lbd.az");
            System.clearProperty("lbd.ip");
        }
    }

    @Test
    public void testControlCharInSystemPropertyRejected() {
        String url = "jdbc:mysql:lb:remote://10.0.0.1:80/mydatabase?connectTimeout=5000&socketTimeout=60000";
        System.setProperty("lbd.az", "az1\nfake.log.line=1");
        try {
            LBDriverUrlParser.parseUrl(url, new Properties());
            Assert.fail("含换行的 -Dlbd.az 应被拒绝");
        } catch (SQLException e) {
            Assert.assertTrue(e.getMessage().contains("control char"));
        } finally {
            System.clearProperty("lbd.az");
        }
    }

    @Test
    public void testNewLineInUrlRejected() {
        //url 与其参数会被拼进请求参数并打进日志，含换行可以伪造出独立的日志行
        String url = "jdbc:mysql:lb:remote://10.0.0.1:80/mydatabase?az=az1\nfake.log.line=1";
        try {
            LBDriverUrlParser.parseUrl(url, new Properties());
            Assert.fail("含换行的 url 应被拒绝");
        } catch (SQLException e) {
            Assert.assertTrue(e.getMessage().contains("control char"));
        }
    }

    @Test
    public void testTabInUrlRejected() {
        String url = "jdbc:mysql:lb:remote://10.0.0.1:80/mydatabase?az=az1&ip=10.1.1.1\trecoverCheckCount=3";
        try {
            LBDriverUrlParser.parseUrl(url, new Properties());
            Assert.fail("含 tab 的 url 应被拒绝");
        } catch (SQLException e) {
            Assert.assertTrue(e.getMessage().contains("control char"));
        }
    }
}
