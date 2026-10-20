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
 * <p>
 * 从地址串里获取sql-proxy列表，此时host:port是sql-proxy的列表，需要填多个达到高可用的效果
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
    private static final String AZ = "az";
    private static final String IP = "ip";
    private static final String AZ_REFRESH_INTERVAL_SECONDS = "azRefreshIntervalSeconds";
    private static final String RECOVER_CHECK_COUNT = "recoverCheckCount";

    //可用区与本机 ip 属于「部署环境」的属性，同一个 url 常被一批机器共用（容器镜像、共享配置），
    //所以额外支持 -D 覆盖，且优先级高于 url 上的同名参数
    private static final String SYSTEM_PROPERTY_PREFIX = "lbd.";

    public static LBDriverUrl parseUrl(String url, Properties info) throws SQLException {
        if (url == null || url.isEmpty()) {
            throw new SQLException("url is null.");
        }
        //url 与其参数会被拼进请求参数并打印到日志，含换行/回车等控制字符可以伪造出独立的日志行
        //这里在入口拒绝而不是转义：jdbc url 本就应写成单行，带控制字符一定是配置错误
        if (containsControlChar(url)) {
            throw new SQLException("illegal char in url, url should not contain control char");
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
        String az = null;
        String ip = null;
        int azRefreshIntervalSeconds = Constants.AZ_REFRESH_INTERVAL_SECONDS;
        int recoverCheckCount = Constants.RECOVER_CHECK_COUNT;

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
                    case AZ:
                        az = value.isEmpty() ? null : value;
                        break;
                    case IP:
                        ip = value.isEmpty() ? null : value;
                        break;
                    case AZ_REFRESH_INTERVAL_SECONDS:
                        azRefreshIntervalSeconds = parsePositiveInt(value, AZ_REFRESH_INTERVAL_SECONDS,
                                Constants.AZ_REFRESH_INTERVAL_SECONDS, url);
                        break;
                    case RECOVER_CHECK_COUNT:
                        recoverCheckCount = parsePositiveInt(value, RECOVER_CHECK_COUNT,
                                Constants.RECOVER_CHECK_COUNT, url);
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
        builder.setAz(overrideFromSystemProperty(AZ, az));
        builder.setIp(overrideFromSystemProperty(IP, ip));
        builder.azRefreshIntervalSeconds(azRefreshIntervalSeconds);
        builder.recoverCheckCount(recoverCheckCount);

        if (type == LBDriverType.remote) {
            String[] split1 = addrs.split(":");
            if (split1.length == 0 || split1.length > 2) {
                throw new SQLException("config server parse error, url = " + url);
            }
            String configServerHost = split1[0];
            int configServerPort;
            if (split1.length == 1) {
                configServerPort = 80;
            } else {
                try {
                    configServerPort = Integer.parseInt(split1[1]);
                } catch (Exception e) {
                    throw new SQLException("config server port parse error, url = " + url);
                }
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

    //-Dlbd.az / -Dlbd.ip 覆盖 url 上的同名参数，优先级最高；取值为空串等价于「不配置」，
    //这样 url 上已配置时也能用 -D 清掉（同一份 url 被共享、单机需要特例时有用）
    private static String overrideFromSystemProperty(String name, String urlValue) throws SQLException {
        String propertyName = SYSTEM_PROPERTY_PREFIX + name;
        String value = System.getProperty(propertyName);
        if (value == null) {
            return urlValue;
        }
        String trimmed = value.trim();
        //与 url 参数一样，这些值会被拼进请求参数并打印到日志，不允许含控制字符
        if (containsControlChar(trimmed)) {
            throw new SQLException("illegal char in system property " + propertyName
                    + ", it should not contain control char");
        }
        if (trimmed.isEmpty()) {
            if (urlValue != null) {
                logger.debug("system property {} is empty, url param {} will not be used", propertyName, name);
            }
            return null;
        }
        logger.debug("system property {} = {} overrides url param {}, value of url param = {}",
                propertyName, trimmed, name, urlValue);
        return trimmed;
    }

    private static int parsePositiveInt(String value, String name, int defaultValue, String url) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed > 0) {
                return parsed;
            }
        } catch (Exception e) {
            //ignore
        }
        logger.warn("illegal {} = {}, use default {} instead, url = {}", name, value, defaultValue, url);
        return defaultValue;
    }

    private static boolean containsControlChar(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') {
                return true;
            }
        }
        return false;
    }
}
