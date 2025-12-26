package com.netease.nim.lbd;

import java.sql.SQLException;

/**
 * Created by caojiajun on 2025/12/26
 */
public interface ExceptionSorter {

    boolean isExceptionFatal(SQLException e);
}
