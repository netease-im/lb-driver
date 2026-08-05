
## Config-Server Usage

* config-server is a Spring Boot web project. It requires Java 21 or later. Its configuration files include `application.yml` and `logback.xml`.
* You can select different configuration centers through `application.yml`. Currently, `etcd`, `nacos`, and `local` are supported. You can also customize one if you have Java development capability.

### Config-Server Deployment

* Download the JAR package from [lb-driver-config-server-bootstrap-1.0.0.jar](https://github.com/netease-im/lb-driver/releases/download/v1.0.0/lb-driver-config-server-bootstrap-1.0.0.jar), or build `lb-driver-config-server-bootstrap` yourself.
* Refer to [application.yml](../lb-driver-config-server-bootstrap/src/main/resources/application.yml) and modify it into your own `application.yml`.
* Refer to [logback-example.xml](../lb-driver-config-server-bootstrap/src/main/resources/logback-example.xml) and modify it into your own `logback.xml`.
* Refer to [startup.sh](startup.sh) and modify `java_home`, the JAR path, the application path, the `logback.xml` path, and other settings, then start the service.

### Using etcd as the Data Source

```yaml
lb-driver-config-server:
  config-type: etcd
  config:
    "etcd.target": "ip:///etcd0:2379,etcd1:2379,etcd2:2379"
#    "etcd.endpoints": "http://etcd0:2379,http://etcd1:2379,http://etcd2:2379" # Choose either etcd.target or etcd.endpoints. etcd.target takes precedence.
#    "etcd.user": "xx"
#    "etcd.password": "xx"
#    "etcd.namespace": "xx"
#    "etcd.authority": "xx"
    "etcd.config.key.prefix": "/obproxy/yunxin"
```
* `/obproxy/yunxin/{schema}` represents the configuration of a specific schema.


### Using nacos as the Data Source

```yaml
lb-driver-config-server:
  config-type: nacos
  config:
    "nacos.serverAddr": "127.0.0.1:8848"
    "nacos.group": "xxx"
    "nacos.username": "xxx"
    "nacos.password": "xxx"
    "nacos.init.schema.list": "xxx,yyy,zzz" # Comma-separated
```
* `dataId={schema}` represents the configuration of a specific schema.
* `nacos.init.schema.list` represents the list of schemas loaded during initialization.


### Using a Local Configuration File as the Data Source

```yaml
lb-driver-config-server:
  config-type: local
  config:
    "local.config.file": "config.json"
#    "local.config.file.path": "/xxx/xx/config.json"
```
* `config.json` is a JSON array. Each element represents one schema.
* You can configure either the file name under the classpath or the absolute file path. The absolute file path has higher priority.


### Configuration Example

* config-server obtains configuration from the data source. The configuration must be a JSON structure, as shown below:
* After `etcd` or `nacos` updates its configuration, config-server automatically listens for configuration changes, and lb-driver obtains the latest sql-proxy node list within a few seconds.
* After `local` updates its configuration, the `/reload` API must be called to refresh the configuration. Then lb-driver obtains the latest sql-proxy node list within a few seconds.

```json
{
  "schema": "im_user",
  "auth.enable": true,
  "api.keys":
  [
    "aaaa",
    "bbbb",
    "cccc"
  ],
  "proxy":
  [
    "10.0.0.1:3306",
    "10.0.0.2:3306"
  ]
}
```

* `schema` represents the schema to which the sql-proxy nodes belong.
* `auth.enable` specifies whether config-server authenticates lb-driver requests. The default is false.
* `api.keys` specifies the API keys used for authentication. Multiple keys are supported.
* `proxy` represents the configured sql-proxy node list.

### API Documentation

```
## Fetch configuration. This is the API called by lb-driver.
curl -H "Authorization: Bearer xxxxx" "http://127.0.0.1:8080/fetch_sql_proxy_list?schema=xxxx"
```

```
## Force reload. For local mode, this rereads the local configuration file. For nacos/etcd, it pulls the configuration from the remote source again.
curl "http://127.0.0.1:8080/reload"
```

```
## Monitoring data
curl "http://127.0.0.1:8080/monitor"
```

```
## Health-check API
curl "http://127.0.0.1:8080/health/status"
## Online API. After calling this API, /health/status returns 200.
curl "http://127.0.0.1:8080/health/online"
## Offline API. After calling this API, /health/status returns 500.
curl "http://127.0.0.1:8080/health/offline"
```

### Nginx Configuration Example

```
server {
    server_name xxx-lbd-config-server.xxx.com;
    proxy_http_version 1.1;
    proxy_set_header Connection "";
    proxy_set_header Host $host;
    proxy_set_header X-From-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    location /fetch_sql_proxy_list {
        proxy_pass http://xxx-lbd-config-server;
    }
}

upstream xxx-lbd-config-server {
    server 10.0.0.1:8080 max_fails=0;
    server 10.0.0.2:8080 max_fails=0;
    server 10.0.0.3:8080 max_fails=0;
    check interval=3000 rise=3 fall=3 timeout=3000 type=http;
    check_http_send "GET /health/status HTTP/1.0\r\n\r\n";
    check_http_expect_alive http_2xx http_3xx;
    keepalive 64;
}
```
