package com.netease.nim.lbd;


import java.util.List;
import java.util.Properties;

/**
 * Created by caojiajun on 2025/12/3
 */
public class LBDriverUrl {

    private String url;

    private LBDriverType type;

    private List<SqlProxy> sqlProxyList;

    private boolean logStats = Constants.LOG_STATS;
    private int checkBalanceIntervalSeconds = Constants.CHECK_BALANCE_INTERVAL_SECONDS;
    private int checkHealthIntervalSeconds = Constants.CHECK_HEALTH_INTERVAL_SECONDS;

    private String configServerHost;
    private int configServerPort;
    private int configServerTimeout = Constants.CONFIG_SERVER_TIMEOUT;

    private String configServerSchema;
    private String configServerApiKey;

    private String schemaName;
    private Properties info;

    private UnsupportedMethodBehavior unsupportedMethodBehavior;

    private ExceptionSorter exceptionSorter = new MySqlExceptionSorter();

    private LBDriverUrl() {
    }

    public String getUrl() {
        return url;
    }

    public LBDriverType getType() {
        return type;
    }

    public String getConfigServerHost() {
        return configServerHost;
    }

    public int getConfigServerPort() {
        return configServerPort;
    }

    public String getConfigServerSchema() {
        return configServerSchema;
    }

    public String getConfigServerApiKey() {
        return configServerApiKey;
    }

    public List<SqlProxy> getSqlProxyList() {
        return sqlProxyList;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public Properties getInfo() {
        return info;
    }

    public UnsupportedMethodBehavior getUnsupportedMethodBehavior() {
        return unsupportedMethodBehavior;
    }

    public boolean isLogStats() {
        return logStats;
    }

    public int getCheckBalanceIntervalSeconds() {
        return checkBalanceIntervalSeconds;
    }

    public int getCheckHealthIntervalSeconds() {
        return checkHealthIntervalSeconds;
    }

    public int getConfigServerTimeout() {
        return configServerTimeout;
    }

    public ExceptionSorter getExceptionSorter() {
        return exceptionSorter;
    }

    public static class Builder {

        private final LBDriverUrl lbDriverUrl;

        public Builder(String url) {
            lbDriverUrl = new LBDriverUrl();
            lbDriverUrl.url = url;
        }

        public void setType(LBDriverType type) {
            lbDriverUrl.type = type;
        }

        public void setConfigServerHost(String configServerHost) {
            lbDriverUrl.configServerHost = configServerHost;
        }

        public void setConfigServerPort(int configServerPort) {
            lbDriverUrl.configServerPort = configServerPort;
        }

        public void setConfigServerSchema(String configServerSchema) {
            lbDriverUrl.configServerSchema = configServerSchema;
        }

        public void setConfigServerApiKey(String configServerApiKey) {
            lbDriverUrl.configServerApiKey = configServerApiKey;
        }

        public void setSchemaName(String schemaName) {
            lbDriverUrl.schemaName = schemaName;
        }

        public void setInfo(Properties info) {
            lbDriverUrl.info = info;
        }

        public void setUnsupportedMethodBehavior(UnsupportedMethodBehavior unsupportedMethodBehavior) {
            lbDriverUrl.unsupportedMethodBehavior = unsupportedMethodBehavior;
        }

        public void setSqlProxyList(List<SqlProxy> list) {
            lbDriverUrl.sqlProxyList = list;
        }

        public void setLogStats(boolean logStats) {
            lbDriverUrl.logStats = logStats;
        }

        public void checkBalanceIntervalSeconds(int checkBalanceIntervalSeconds) {
            lbDriverUrl.checkBalanceIntervalSeconds = checkBalanceIntervalSeconds;
        }

        public void checkHealthIntervalSeconds(int checkHealthIntervalSeconds) {
            lbDriverUrl.checkHealthIntervalSeconds = checkHealthIntervalSeconds;
        }

        public void configServerTimeout(int configServerTimeout) {
            lbDriverUrl.configServerTimeout = configServerTimeout;
        }

        public void exceptionSorter(String exceptionSorterClassName) {
            try {
                if (exceptionSorterClassName == null) {
                    return;
                }
                lbDriverUrl.exceptionSorter = (ExceptionSorter) Class.forName(exceptionSorterClassName).getConstructor().newInstance();
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        }

        public LBDriverUrl build() {
            return lbDriverUrl;
        }
    }
}
