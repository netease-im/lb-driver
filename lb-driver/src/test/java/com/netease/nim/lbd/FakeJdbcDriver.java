package com.netease.nim.lbd;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 测试用的假 jdbc driver
 * <p>
 * 只实现 driver 需要的 connect 语义：对 unreachable 集合里的地址抛 SQLException，其余返回可用的假连接
 * <p>
 * 提供两个独立的故障开关：{@link #setUnreachable(String, boolean)} 只影响新建连接，
 * {@link #setEstablishedBroken(String, boolean)} 让已经建立的连接开始报错（模拟故障持续期间先建好的链路坏掉），
 * 两个一起打开才是一次「节点整体挂掉」
 * <p>
 * Created by caojiajun on 2026/10/15
 */
public class FakeJdbcDriver implements Driver {

    private final Set<String> unreachable = ConcurrentHashMap.newKeySet();
    private final Set<String> establishedBroken = ConcurrentHashMap.newKeySet();

    //标记某个地址不可达，用于模拟 sql-proxy 故障
    public void setUnreachable(String hostPort, boolean value) {
        if (value) {
            unreachable.add(hostPort);
        } else {
            unreachable.remove(hostPort);
        }
    }

    //标记某个地址上已经建立的连接坏掉：连接上的操作开始抛异常，新建连接不受影响
    public void setEstablishedBroken(String hostPort, boolean value) {
        if (value) {
            establishedBroken.add(hostPort);
        } else {
            establishedBroken.remove(hostPort);
        }
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        for (String hostPort : unreachable) {
            if (url.contains("//" + hostPort + "/")) {
                throw new SQLException("mock connect error, url = " + url);
            }
        }
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{Connection.class}, new ConnectionHandler(url));
    }

    //从 jdbc:mysql://host:port/schema 里取出 host:port
    static String hostPortOf(String url) {
        int start = url.indexOf("//");
        if (start < 0) {
            return null;
        }
        start += 2;
        int end = url.indexOf('/', start);
        return end < 0 ? url.substring(start) : url.substring(start, end);
    }

    @Override
    public boolean acceptsURL(String url) {
        return true;
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
        return new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion() {
        return 0;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public java.util.logging.Logger getParentLogger() {
        return java.util.logging.Logger.getGlobal();
    }

    private class ConnectionHandler implements InvocationHandler {

        private final String url;
        private final String hostPort;

        ConnectionHandler(String url) {
            this.url = url;
            this.hostPort = hostPortOf(url);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "createStatement":
                    checkNotBroken();
                    return Proxy.newProxyInstance(getClass().getClassLoader(),
                            new Class[]{Statement.class}, new StatementHandler());
                case "prepareStatement":
                    checkNotBroken();
                    return Proxy.newProxyInstance(getClass().getClassLoader(),
                            new Class[]{PreparedStatement.class}, new StatementHandler());
                case "isValid":
                    return !isBroken();
                case "close":
                case "setAutoCommit":
                case "setTransactionIsolation":
                    return null;
                case "isClosed":
                    return false;
                case "getAutoCommit":
                    return true;
                case "toString":
                    return "FakeConnection(" + url + ")";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    return null;
            }
        }

        private void checkNotBroken() throws SQLException {
            if (isBroken()) {
                throw new SQLException("mock connection broken, url = " + url);
            }
        }

        private boolean isBroken() {
            return hostPort != null && establishedBroken.contains(hostPort);
        }
    }

    private static class StatementHandler implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "executeQuery":
                    return Proxy.newProxyInstance(getClass().getClassLoader(),
                            new Class[]{ResultSet.class}, new ResultSetHandler());
                case "close":
                    return null;
                case "toString":
                    return "FakeStatement";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    return null;
            }
        }
    }

    private static class ResultSetHandler implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "next":
                    return true;
                case "close":
                    return null;
                case "toString":
                    return "FakeResultSet";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    return null;
            }
        }
    }
}
