package com.netease.nim.lbd;

import java.util.List;

/**
 * Created by caojiajun on 2025/12/10
 */
public class LbdStats {

    private List<SqlProxyStats> statsList;

    public List<SqlProxyStats> getStatsList() {
        return statsList;
    }

    public void setStatsList(List<SqlProxyStats> statsList) {
        this.statsList = statsList;
    }

    public static class SqlProxyStats {
        private SqlProxy sqlProxy;
        private boolean online;
        private boolean reachable;
        private int using;
        private int idle;

        public SqlProxy getSqlProxy() {
            return sqlProxy;
        }

        public void setSqlProxy(SqlProxy sqlProxy) {
            this.sqlProxy = sqlProxy;
        }

        public boolean isOnline() {
            return online;
        }

        public void setOnline(boolean online) {
            this.online = online;
        }

        public boolean isReachable() {
            return reachable;
        }

        public void setReachable(boolean reachable) {
            this.reachable = reachable;
        }

        public int getUsing() {
            return using;
        }

        public void setUsing(int using) {
            this.using = using;
        }

        public int getIdle() {
            return idle;
        }

        public void setIdle(int idle) {
            this.idle = idle;
        }
    }
}
