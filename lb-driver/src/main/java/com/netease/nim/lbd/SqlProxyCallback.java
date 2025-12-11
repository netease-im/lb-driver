package com.netease.nim.lbd;

import java.util.List;

/**
 * Created by caojiajun on 2025/12/9
 */
public interface SqlProxyCallback {

    void add(List<SqlProxy> list);

    void remove(List<SqlProxy> list);

}
