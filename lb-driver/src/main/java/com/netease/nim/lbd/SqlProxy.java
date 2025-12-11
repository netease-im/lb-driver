package com.netease.nim.lbd;

import java.util.Objects;

/**
 * Created by caojiajun on 2025/12/3
 */
public class SqlProxy implements Comparable<SqlProxy> {

    private final String host;
    private final int port;

    public SqlProxy(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return host + ":" + port;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        SqlProxy sqlProxy = (SqlProxy) o;
        return port == sqlProxy.port && Objects.equals(host, sqlProxy.host);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port);
    }

    @Override
    public int compareTo(SqlProxy o) {
        return toString().compareTo(o.toString());
    }
}
