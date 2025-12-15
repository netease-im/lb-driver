package com.netease.nim.lbd.config.server.service;

import com.alibaba.fastjson2.JSONObject;
import com.netease.nim.lbd.config.server.model.SchemaConfig;
import com.netease.nim.lbd.config.server.utils.ConfigUtils;
import com.netease.nim.lbd.config.server.utils.NamedThreadFactory;
import io.etcd.jetcd.*;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.options.GetOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by caojiajun on 2025/12/10
 */
public class EtcdConfigService implements ConfigService {

    private static final Logger logger = LoggerFactory.getLogger(EtcdConfigService.class);

    private static final ExecutorService reloadExecutor = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("etcd-config-service"));

    private final Map<String, SchemaConfig> schemaConfigMap = new ConcurrentHashMap<>();

    private String configKeyPrefix;
    private Client client;

    private final ReentrantLock lock = new ReentrantLock();

    @Override
    public void init(Map<String, String> config) {
        String etcdServer = null;
        try {
            // Get etcd config by prefix.
            String target = config.get("etcd.target");
            Client client;
            ClientBuilder builder;
            if (target != null) {
                //e.g  ip:///etcd0:2379,etcd1:2379,etcd2:2379
                builder = Client.builder().target(target);
                etcdServer = target;
            } else {
                //e.g http://etcd0:2379,http://etcd1:2379,http://etcd2:2379
                String endpoints = config.get("etcd.endpoints");
                if (endpoints == null) {
                    throw new IllegalArgumentException("missing 'etcd.target' or 'etcd.endpoints'");
                }
                String[] split = endpoints.split(",");
                builder = Client.builder().endpoints(split);
                etcdServer = endpoints;
            }
            String user = config.get("etcd.user");
            String password = config.get("etcd.password");
            String namespace = config.get("etcd.namespace");
            String authority = config.get("etcd.authority");
            if (user != null) {
                builder.user(ByteSequence.from(user, StandardCharsets.UTF_8));
            }
            if (password != null) {
                builder.password(ByteSequence.from(password, StandardCharsets.UTF_8));
            }
            if (namespace != null) {
                builder.namespace(ByteSequence.from(namespace, StandardCharsets.UTF_8));
            }
            if (authority != null) {
                builder.authority(authority);
            }
            for (Map.Entry<String, String> entry : config.entrySet()) {
                String prefix = "etcd.header.";
                if (entry.getKey().startsWith(prefix)) {
                    String header = entry.getKey().substring(prefix.length());
                    builder.authHeader(header, entry.getValue());
                }
            }
            client = builder.build();
            this.client = client;
            String keyPrefix = config.get("etcd.config.key.prefix");
            if (keyPrefix == null) {
                throw new IllegalArgumentException("missing 'etcd.config.key.prefix'");
            }
            configKeyPrefix = keyPrefix;
            init();
            logger.info("EtcdConfigService init success, etcdServer = {}, configKeyPrefix = {}", etcdServer, configKeyPrefix);
        } catch (Exception e) {
            logger.error("EtcdConfigService init error, etcdServer = {}, configKeyPrefix = {}", etcdServer, configKeyPrefix, e);
            throw new IllegalArgumentException(e);
        }
    }

    private void init() throws Exception {
        CompletableFuture<GetResponse> future = client.getKVClient().get(ByteSequence.from(configKeyPrefix, StandardCharsets.UTF_8), GetOption.builder().isPrefix(true).build());
        GetResponse response = future.get();
        List<KeyValue> kvs = response.getKvs();
        for (KeyValue kv : kvs) {
            ByteSequence key = kv.getKey();
            if (key.toString().length() < configKeyPrefix.length() + 1) {
                continue;
            }
            String schema = key.toString().substring(configKeyPrefix.length() + 1);
            initSchema(schema);
        }
    }

    @Override
    public boolean reload() {
        try {
            for (Map.Entry<String, SchemaConfig> entry : schemaConfigMap.entrySet()) {
                String schema = entry.getKey();
                try {
                    reload0(schema);
                } catch (Exception e) {
                    logger.error("reload error, schema = {}", schema, e);
                }
            }
            return true;
        } catch (Exception e) {
            logger.error("reload from etcd error, configKeyPrefix = {}", configKeyPrefix, e);
            return false;
        }
    }

    @Override
    public SchemaConfig getSchemaConfig(String schema) {
        SchemaConfig schemaConfig = schemaConfigMap.get(schema);
        if (schemaConfig != null) {
            return schemaConfig;
        }
        initSchema(schema);
        return schemaConfigMap.get(schema);
    }

    private void initSchema(String schema) {
        lock.lock();
        try {
            SchemaConfig schemaConfig = schemaConfigMap.get(schema);
            if (schemaConfig != null) {
                return;
            }
            //
            client.getWatchClient().watch(configKey(schema), watchResponse -> reloadExecutor.submit(() -> {
                try {
                    reload0(schema);
                } catch (Exception e) {
                    logger.error("reload error, schema = {}", schema, e);
                }
            }));
            //
            reload0(schema);
            //
            schemaConfig = schemaConfigMap.get(schema);
            logger.info("schema init success, schema = {}, schemaConfig = {}", schema, JSONObject.toJSONString(schemaConfig));
        } catch (Exception e) {
            logger.error("init schema config error, schema = {}", schema, e);
            throw new IllegalArgumentException("init schema config error, schema = " + schema);
        } finally {
            lock.unlock();
        }
    }

    private ByteSequence configKey(String schema) {
        return ByteSequence.from((configKeyPrefix + "/" + schema).getBytes(StandardCharsets.UTF_8));
    }

    private void reload0(String schema) {
        try {
            ByteSequence configKey = configKey(schema);
            CompletableFuture<GetResponse> future = client.getKVClient().get(configKey);
            GetResponse response = future.get();
            List<KeyValue> kvs = response.getKvs();
            if (kvs.isEmpty()) {
                throw new IllegalArgumentException("config not found");
            }
            KeyValue value = kvs.getFirst();
            SchemaConfig schemaConfig = ConfigUtils.parse(value.getValue().toString());
            if (!Objects.equals(schemaConfig.getSchema(), schema)) {
                throw new IllegalArgumentException("illegal schema config, schema = " + schema);
            }
            schemaConfigMap.put(schema, schemaConfig);
        } catch (Exception e) {
            throw new IllegalArgumentException("init schema config error, schema = " + schema);
        }
    }

    @Override
    public Map<String, SchemaConfig> getConfigMap() {
        return new HashMap<>(schemaConfigMap);
    }
}
