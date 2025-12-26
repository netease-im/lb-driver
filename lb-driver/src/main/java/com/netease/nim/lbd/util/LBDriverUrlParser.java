package com.netease.nim.lbd.util;

import com.netease.nim.lbd.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 从config-server获取sql-proxy列表，此时host:port是config-server的地址，仅能填一个，可以是域名
 * jdbc:mysql:lb:remote://config-server.xxx.com:8080/mydatabase?connectTimeout=5000&socketTimeout=10000&configServerApiKey=xxx&configServerSchema=im_user&logStats=true
 * <p>
 * 从地址串里获取sql-proxy列表，此时host:port是sql-proxy的列表，需要填多个达到高可用的效果
 * jdbc:mysql:lb:local://10.189.0.1:6000,10.189.0.2:6000,10.189.0.3:6000/mydatabase?connectTimeout=5000&socketTimeout=10000&logStats=true
 * <p>
 * Created by caojiajun on 2025/12/3
 */
public class LBDriverUrlParser {

    private static final Logger logger = LoggerFactory.getLogger(LBDriverUrlParser.class);

    private static final String UNSUPPORTED_METHOD_BEHAVIOR = "unsupportedMethodBehavior";
    private static final String CONFIG_SERVER_SCHEMA = "configServerSchema";
    private static final String CONFIG_SERVER_API_KEY = "configServerApiKey";
    private static final String LOG_STATS = "logStats";
    private static final String CHECK_BALANCE_INTERVAL_SECONDS = "checkBalanceIntervalSeconds";
    private static final String CHECK_HEALTH_INTERVAL_SECONDS = "checkHealthIntervalSeconds";
    private static final String CONFIG_SERVER_TIMEOUT = "configServerTimeout";
    private static final String EXCEPTION_SORTER_CLASS_NAME = "exceptionSorter";

    public static LBDriverUrl parseUrl(String url, Properties info) throws SQLException {
        if (url == null || url.isEmpty()) {
            throw new SQLException("url is null.");
        }
        if (!url.startsWith(Constants.LB_DRIVER_URL_PREFIX)) {
            logger.warn("not lbd driver url = {}, props = {}", url, info);
            return null;
        }

        LBDriverUrl.Builder builder = new LBDriverUrl.Builder(url);
        LBDriverType type;
        if (url.startsWith(Constants.LB_DRIVER_LOCAL_URL_PREFIX)) {
            type = LBDriverType.local;
        } else if (url.startsWith(Constants.LB_DRIVER_REMOTE_URL_PREFIX)) {
            type = LBDriverType.remote;
        } else {
            logger.warn("illegal lbd driver url = {}, props = {}", url, info);
            return null;
        }
        builder.setType(type);

        Properties urlProps = new Properties();
        for (Map.Entry<Object, Object> entry : info.entrySet()) {
            urlProps.setProperty((String) entry.getKey(), (String) entry.getValue());
        }

        int index = url.indexOf("//");
        String configServerAndParams = url.substring(index + 2);

        String[] split = configServerAndParams.split("/");
        if (split.length != 2) {
            throw new SQLException("LBDriver url must identify database with '/'");
        }

        String configServerApiKey = null;
        String configServerSchema = null;
        UnsupportedMethodBehavior unSupportMethodBehavior = Constants.UNSUPPORTED_METHOD_BEHAVIOR;
        boolean logStats = Constants.LOG_STATS;
        int checkBalanceIntervalSeconds = Constants.CHECK_BALANCE_INTERVAL_SECONDS;
        int checkHealthIntervalSeconds = Constants.CHECK_HEALTH_INTERVAL_SECONDS;
        int configServerTimeout = Constants.CONFIG_SERVER_TIMEOUT;
        String exceptionSorterClassName = Constants.EXCEPTION_SORTER_CLASS_NAME;

        String addrs = split[0];
        String[] schemaAndProps = split[1].split("\\?");
        String schemaName = schemaAndProps[0];
        String props = schemaAndProps.length > 1 ? schemaAndProps[1] : null;
        if (props != null) {
            for (String param : props.split("&")) {
                String[] nameAndValue = param.split("=");
                if (nameAndValue.length != 2) {
                    throw new SQLException("Bad properties resolving url " + url);
                }
                String key = nameAndValue[0].trim();
                String value = nameAndValue[1].trim();
                switch (key) {
                    case UNSUPPORTED_METHOD_BEHAVIOR:
                        unSupportMethodBehavior = UnsupportedMethodBehavior.fromName(value);
                        if (unSupportMethodBehavior == null) {
                            throw new SQLException("Bad properties on " + UNSUPPORTED_METHOD_BEHAVIOR + ", url = " + url);
                        }
                        break;
                    case CONFIG_SERVER_API_KEY:
                        configServerApiKey = value;
                        break;
                    case CONFIG_SERVER_SCHEMA:
                        configServerSchema = value;
                        break;
                    case LOG_STATS:
                        logStats = Boolean.parseBoolean(value);
                        break;
                    case CHECK_BALANCE_INTERVAL_SECONDS:
                        checkBalanceIntervalSeconds = Integer.parseInt(value);
                        break;
                    case CHECK_HEALTH_INTERVAL_SECONDS:
                        checkHealthIntervalSeconds = Integer.parseInt(value);
                        break;
                    case CONFIG_SERVER_TIMEOUT:
                        configServerTimeout = Integer.parseInt(value);
                        break;
                    case EXCEPTION_SORTER_CLASS_NAME:
                        exceptionSorterClassName = value;
                        break;
                    default:
                        urlProps.setProperty(key, value);
                        break;
                }
            }
        }

        builder.setInfo(urlProps);
        builder.setSchemaName(schemaName);
        builder.setConfigServerApiKey(configServerApiKey);
        builder.setConfigServerSchema(configServerSchema);
        builder.setUnsupportedMethodBehavior(unSupportMethodBehavior);
        builder.setLogStats(logStats);
        builder.configServerTimeout(configServerTimeout);
        builder.checkBalanceIntervalSeconds(checkBalanceIntervalSeconds);
        builder.checkHealthIntervalSeconds(checkHealthIntervalSeconds);
        builder.exceptionSorter(exceptionSorterClassName);

        if (type == LBDriverType.remote) {
            String[] split1 = addrs.split(":");
            if (split1.length != 2) {
                throw new SQLException("config server parse error, url = " + url);
            }
            String configServerHost = split1[0];
            int configServerPort;
            try {
                configServerPort = Integer.parseInt(split1[1]);
            } catch (Exception e) {
                throw new SQLException("config server port parse error, url = " + url);
            }
            builder.setConfigServerHost(configServerHost);
            builder.setConfigServerPort(configServerPort);
        } else {
            String[] split1 = addrs.split(",");
            List<SqlProxy> list = new ArrayList<>();
            for (String string : split1) {
                String[] split2 = string.split(":");
                if (split2.length != 2) {
                    throw new SQLException("sql proxy parse error, url = " + url);
                }
                String sqlProxyHost = split2[0];
                int sqlProxyPort;
                try {
                    sqlProxyPort = Integer.parseInt(split2[1]);
                } catch (Exception e) {
                    throw new SQLException("sql proxy port parse error, url = " + url);
                }
                list.add(new SqlProxy(sqlProxyHost, sqlProxyPort));
            }
            builder.setSqlProxyList(list);
        }
        return builder.build();
    }
}
