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

    /**
     * get url
     * @return url
     */
    public String getUrl() {
        return url;
    }

    /**
     * get type
     * @return LBDriverType
     */
    public LBDriverType getType() {
        return type;
    }

    /**
     * getConfigServerHost
     * @return configServerHost
     */
    public String getConfigServerHost() {
        return configServerHost;
    }

    /**
     * getConfigServerPort
     * @return configServerPort
     */
    public int getConfigServerPort() {
        return configServerPort;
    }

    /**
     * getConfigServerSchema
     * @return configServerSchema
     */
    public String getConfigServerSchema() {
        return configServerSchema;
    }

    /**
     * getConfigServerApiKey
     * @return configServerApiKey
     */
    public String getConfigServerApiKey() {
        return configServerApiKey;
    }

    /**
     * getSqlProxyList
     * @return sqlProxyList
     */
    public List<SqlProxy> getSqlProxyList() {
        return sqlProxyList;
    }

    /**
     * getSchemaName
     * @return schemaName
     */
    public String getSchemaName() {
        return schemaName;
    }

    /**
     * getInfo
     * @return info
     */
    public Properties getInfo() {
        return info;
    }

    /**
     * getUnsupportedMethodBehavior
     * @return unsupportedMethodBehavior
     */
    public UnsupportedMethodBehavior getUnsupportedMethodBehavior() {
        return unsupportedMethodBehavior;
    }

    /**
     * isLogStats
     * @return logStats
     */
    public boolean isLogStats() {
        return logStats;
    }

    /**
     * getCheckBalanceIntervalSeconds
     * @return checkBalanceIntervalSeconds
     */
    public int getCheckBalanceIntervalSeconds() {
        return checkBalanceIntervalSeconds;
    }

    /**
     * getCheckHealthIntervalSeconds
     * @return checkHealthIntervalSeconds
     */
    public int getCheckHealthIntervalSeconds() {
        return checkHealthIntervalSeconds;
    }

    /**
     * getConfigServerTimeout
     * @return configServerTimeout
     */
    public int getConfigServerTimeout() {
        return configServerTimeout;
    }

    /**
     * getExceptionSorter
     * @return exceptionSorter
     */
    public ExceptionSorter getExceptionSorter() {
        return exceptionSorter;
    }

    public static class Builder {

        private final LBDriverUrl lbDriverUrl;

        /**
         * Builder
         * @param url url
         */
        public Builder(String url) {
            lbDriverUrl = new LBDriverUrl();
            lbDriverUrl.url = url;
        }

        /**
         * setType
         * @param type LBDriverType
         */
        public void setType(LBDriverType type) {
            lbDriverUrl.type = type;
        }

        /**
         * setConfigServerHost
         * @param configServerHost configServerHost
         */
        public void setConfigServerHost(String configServerHost) {
            lbDriverUrl.configServerHost = configServerHost;
        }

        /**
         * setConfigServerPort
         * @param configServerPort configServerPort
         */
        public void setConfigServerPort(int configServerPort) {
            lbDriverUrl.configServerPort = configServerPort;
        }

        /**
         * setConfigServerSchema
         * @param configServerSchema configServerSchema
         */
        public void setConfigServerSchema(String configServerSchema) {
            lbDriverUrl.configServerSchema = configServerSchema;
        }

        /**
         * setConfigServerApiKey
         * @param configServerApiKey configServerApiKey
         */
        public void setConfigServerApiKey(String configServerApiKey) {
            lbDriverUrl.configServerApiKey = configServerApiKey;
        }

        /**
         * setSchemaName
         * @param schemaName schemaName
         */
        public void setSchemaName(String schemaName) {
            lbDriverUrl.schemaName = schemaName;
        }

        /**
         * setInfo
         * @param info info
         */
        public void setInfo(Properties info) {
            lbDriverUrl.info = info;
        }

        /**
         * setUnsupportedMethodBehavior
         * @param unsupportedMethodBehavior unsupportedMethodBehavior
         */
        public void setUnsupportedMethodBehavior(UnsupportedMethodBehavior unsupportedMethodBehavior) {
            lbDriverUrl.unsupportedMethodBehavior = unsupportedMethodBehavior;
        }

        /**
         * setSqlProxyList
         * @param list list
         */
        public void setSqlProxyList(List<SqlProxy> list) {
            lbDriverUrl.sqlProxyList = list;
        }

        /**
         * setLogStats
         * @param logStats logStats
         */
        public void setLogStats(boolean logStats) {
            lbDriverUrl.logStats = logStats;
        }

        /**
         * checkBalanceIntervalSeconds
         * @param checkBalanceIntervalSeconds checkBalanceIntervalSeconds
         */
        public void checkBalanceIntervalSeconds(int checkBalanceIntervalSeconds) {
            lbDriverUrl.checkBalanceIntervalSeconds = checkBalanceIntervalSeconds;
        }

        /**
         * checkHealthIntervalSeconds
         * @param checkHealthIntervalSeconds checkHealthIntervalSeconds
         */
        public void checkHealthIntervalSeconds(int checkHealthIntervalSeconds) {
            lbDriverUrl.checkHealthIntervalSeconds = checkHealthIntervalSeconds;
        }

        /**
         * configServerTimeout
         * @param configServerTimeout configServerTimeout
         */
        public void configServerTimeout(int configServerTimeout) {
            lbDriverUrl.configServerTimeout = configServerTimeout;
        }

        /**
         * exceptionSorter
         * @param exceptionSorterClassName exceptionSorterClassName
         */
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

        /**
         * build
         * @return LBDriverUrl
         */
        public LBDriverUrl build() {
            return lbDriverUrl;
        }
    }
}
