package com.netease.nim.lbd;

import com.netease.nim.lbd.util.NamedThreadFactory;
import com.netease.nim.lbd.util.ParamBuilder;
import com.netease.nim.lbd.util.TinyJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Created by caojiajun on 2025/12/9
 */
public class RemoteSqlProxyProvider implements SqlProxyProvider {

    private static final Logger logger = LoggerFactory.getLogger(RemoteSqlProxyProvider.class);

    private final LBDriverUrl lbDriverUrl;
    private final List<SqlProxyCallback> callbackList = new ArrayList<>();

    private String md5;
    private List<SqlProxy> sqlProxyList;

    public RemoteSqlProxyProvider(LBDriverUrl lbDriverUrl) {
        this.lbDriverUrl = lbDriverUrl;
        FetchResponse response = null;
        for (int i=0; i<3; i++) {
            response = fetchSqlProxyList();
            if (response != null) {
                break;
            }
        }
        if (response == null) {
            throw new IllegalArgumentException("fetch sql proxy list error");
        }
        this.sqlProxyList = response.list;
        if (sqlProxyList == null || sqlProxyList.isEmpty()) {
            throw new IllegalArgumentException("fetch sql proxy list empty");
        }
        Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("lbd-sql-proxy-provider"))
                .scheduleAtFixedRate(this::checkSqlProxyListChange, 5, 5, TimeUnit.SECONDS);
        logger.info("lbd remote sql proxy provider init success, configServer = {}, schema = {}, sqlProxyList = {}",
                lbDriverUrl.getConfigServerHost() + ":" + lbDriverUrl.getConfigServerPort(), lbDriverUrl.getConfigServerSchema(), sqlProxyList);
    }

    @Override
    public List<SqlProxy> load() {
        List<SqlProxy> list = new ArrayList<>(sqlProxyList);
        Collections.shuffle(list);
        return list;
    }

    @Override
    public void addSqlProxyCallback(SqlProxyCallback callback) {
        synchronized (callbackList) {
            callbackList.add(callback);
        }
    }

    private void checkSqlProxyListChange() {
        try {
            List<SqlProxy> oldList = new ArrayList<>(this.sqlProxyList);
            FetchResponse fetchResponse = fetchSqlProxyList();
            if (fetchResponse == null) {
                return;
            }
            List<SqlProxy> newList = fetchResponse.list;
            if (newList == null) {
                return;
            }
            if (newList.isEmpty()) {
                logger.error("fetch sql proxy list empty, skip update");
                return;
            }
            List<SqlProxy> added = new ArrayList<>(newList);
            added.removeAll(oldList);
            List<SqlProxy> removed = new ArrayList<>(oldList);
            removed.removeAll(newList);
            if (!added.isEmpty()) {
                Collections.shuffle(added);
                synchronized (callbackList) {
                    for (SqlProxyCallback callback : callbackList) {
                        try {
                            callback.add(added);
                        } catch (Exception e) {
                            logger.error("SqlProxyCallback add error", e);
                        }
                    }
                }
            }
            if (!removed.isEmpty()) {
                Collections.shuffle(removed);
                synchronized (callbackList) {
                    for (SqlProxyCallback callback : callbackList) {
                        try {
                            callback.remove(removed);
                        } catch (Exception e) {
                            logger.error("SqlProxyCallback remove error", e);
                        }
                    }
                }
            }
            this.sqlProxyList = newList;
            this.md5 = fetchResponse.md5;
        } catch (Exception e) {
            logger.error("checkSqlProxyListChange error, configServer = {}, schema = {}",
                    lbDriverUrl.getConfigServerHost() + ":" + lbDriverUrl.getConfigServerPort(), lbDriverUrl.getConfigServerSchema(), e);
        }
    }

    private static class FetchResponse {
        int code;
        String md5;
        List<SqlProxy> list;

        public FetchResponse(int code, String md5, List<SqlProxy> list) {
            this.code = code;
            this.md5 = md5;
            this.list = list;
        }
    }

    private FetchResponse fetchSqlProxyList() {
        ParamBuilder builder = new ParamBuilder();
        if (md5 != null) {
            builder.addParam("md5", md5);
        }
        builder.addParam("schema", lbDriverUrl.getConfigServerSchema());
        String fullUrl = "http://" + lbDriverUrl.getConfigServerHost() + ":" + lbDriverUrl.getConfigServerPort() + "/fetch_sql_proxy_list?" + builder.build();

        HttpURLConnection conn = null;
        try {
            URL url = new URL(fullUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Accept", "application/json");
            if (lbDriverUrl.getConfigServerApiKey() != null) {
                conn.setRequestProperty("Authorization", "Bearer " + lbDriverUrl.getConfigServerApiKey());
            }
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");
            //
            int status = conn.getResponseCode();
            if (status != 200) {
                logger.error("config server error, status code = {}", status);
                return null;
            }
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line).append(System.lineSeparator());
                }
            }
            Map<String, Object> json = TinyJson.parseObject(response.toString());
            int code = Integer.parseInt(json.get("code").toString());
            if (code == 304) {
                return new FetchResponse(304, null, null);
            }
            if (code != 200) {
                logger.error("config server error, response = {}", response);
                return null;
            }
            String md5 = json.get("md5").toString();
            List<String> list = (List<String>) json.get("data");
            List<SqlProxy> newList = new ArrayList<>();
            for (String string : list) {
                String[] split = string.split(":");
                if (split.length != 2) {
                    throw new IllegalArgumentException("parse sql-proxy error = " + string);
                }
                SqlProxy sqlProxy = new SqlProxy(split[0], Integer.parseInt(split[1]));
                newList.add(sqlProxy);
            }
            return new FetchResponse(200, md5, newList);
        } catch (Exception e) {
            logger.error("fetch sql proxy list error, configServer = {}, schema = {}",
                    lbDriverUrl.getConfigServerHost() + ":" + lbDriverUrl.getConfigServerPort(), lbDriverUrl.getConfigServerSchema(), e);
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
