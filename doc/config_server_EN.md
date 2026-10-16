
## Config-Server Usage

* config-server is a Spring Boot web project. It requires Java 21 or later. Its configuration files include `application.yml` and `logback.xml`.
* You can select different configuration centers through `application.yml`. Currently, `etcd`, `nacos`, and `local` are supported. You can also customize one if you have Java development capability.

### Config-Server Deployment

* Download the JAR package from [lb-driver-config-server-bootstrap-1.0.1.jar](https://github.com/netease-im/lb-driver/releases/download/v1.0.1/lb-driver-config-server-bootstrap-1.0.1.jar), or build `lb-driver-config-server-bootstrap` yourself.
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

### Multi-AZ (Availability Zone) Delivery

* config-server can deliver the sql-proxy list according to the availability zone of the client, so that application servers prefer the sql-proxy in the same AZ and cross-AZ latency is reduced.
* This feature is disabled by default: `proxy.policy` defaults to `default`, in which case every client gets the default list and the behavior is exactly the same as before multi-AZ was introduced.
* Only clients that are able to fall back to the default list when the whole local-AZ list is unreachable (`clientFeatureLevel >= 2`, i.e. lb-driver 1.1.0 and above) receive an AZ list. Older clients still receive the default list, so they never silently lose the cross-AZ fallback capability.
* It only affects the delivered sql-proxy list. Authentication, reload, monitoring and health-check endpoints are unchanged.

The AZ definition (`az.list`) can be configured globally in the `application.yml` of config-server. It works for all three data sources (`etcd`, `nacos`, `local`) because they are all read from `lb-driver-config-server.config`. When configured globally, it can be omitted in each schema; when configured in a schema as well, the schema-level value wins:

```yaml
lb-driver-config-server:
  config-type: etcd
  config:
    "az.list": '[{"name":"az1","cidr":["10.1.0.0/16"]},{"name":"az2","cidr":["10.2.0.0/16"]}]'
    "etcd.target": "ip:///etcd0:2379,etcd1:2379,etcd2:2379"
    "etcd.config.key.prefix": "/obproxy/yunxin"
```

Three new fields are supported in the schema configuration. Legacy configuration (with `proxy` only) is fully compatible:

```json
{
  "schema": "im_user",
  "auth.enable": true,
  "api.keys":
  [
    "aaaa",
    "bbbb"
  ],
  "proxy.policy": "az",
  "az.list":
  [
    {"name": "az1", "cidr": ["10.1.0.0/16"]},
    {"name": "az2", "cidr": ["10.2.0.0/16", "10.3.0.0/16"]}
  ],
  "az.proxy":
  {
    "az1": ["10.0.0.1:3306"],
    "az2": ["10.0.1.1:3306"]
  },
  "proxy":
  [
    "10.0.0.1:3306",
    "10.0.1.1:3306"
  ]
}
```

* `proxy.policy` is the delivery policy, either `default` or `az`. It is optional and defaults to `default`. `az` means delivering by the AZ of the client.
* `az.list` is the AZ definition and is optional (it may exist only in the global configuration of config-server). `name` is the AZ name agreed with your ops team; config-server neither validates nor hardcodes it. `cidr` is the network of the AZ and supports multiple entries. Only IPv4 is supported.
* `az.proxy` maps an AZ name to a sql-proxy node list and is optional. Its keys must exist in the effective `az.list`. For an AZ without configured nodes, its clients receive the default list.
* `proxy` is the default (fallback) list and is optional. When omitted in a schema, it is the union of all nodes in `az.proxy`.

Validation rules (an invalid configuration makes the schema fail to load/update, the previous configuration is kept and the reason is logged):

* An illegal `proxy.policy` is rejected.
* Keys of `az.proxy` must exist in the effective `az.list`.
* The same sql-proxy node must not belong to multiple AZs, otherwise the "local-AZ list" would be meaningless.
* Networks of different AZs must not overlap, otherwise one IP matches two AZs and the result depends on the order.
* When `proxy.policy=az`, both the effective `az.list` and `az.proxy` must not be empty, so that "AZ affinity configured but silently not effective" cannot happen.
* `az.proxy` is still accepted when `proxy.policy` is `default` (so that the configuration can be staged before switching the policy), but a warning is logged that it will not take effect.

AZ matching priority (only when `proxy.policy=az` and the client feature level is high enough; the first match wins):

1. The request carries `policy=default`: the default list is delivered (used by a client to get the full list for fallback).
2. The request carries `az` and that AZ exists in the effective `az.list`: the list of that AZ is delivered (`az` is an explicit override; it is not configured in normal usage).
3. The request carries `ip`: the AZ is resolved from that `ip`.
4. The AZ is resolved from the source IP of the request: `X-From-IP`, then `X-Real-IP`, then the first value of `X-Forwarded-For`, then `RemoteAddr`.
5. No AZ matched, or the matched AZ has no node, or the client feature level is too low: the default list is delivered.

The candidate order puts `X-From-IP` (`$remote_addr`) and `X-Real-IP`, which the access layer overwrites and a client cannot forge, before `X-Forwarded-For`: when it is appended with `$proxy_add_x_forwarded_for`, its first value is supplied by the caller and can be crafted freely (the example configuration below does exactly that). That order only infers the AZ, it is a performance optimization and not a security boundary; authorization is still enforced by `apiKey`.

The response contains a new `policy` field which is the policy actually used for this response (`az` or `default`). A client treats any non-empty `policy` other than `default` as an affinity declaration and enables its fallback capability accordingly, so the client-side check needs no change when new policies are added; when affinity is not declared (`default` or the field is absent) the client performs no fallback action. The `md5` digest covers both `policy` and the matched AZ, so different AZs and different policies naturally get different digests and a shared `md5` can never make a client keep a stale policy after a `304` (the server keeps no digest cache; it compares the digest computed for the current request).

### API Documentation

```
## Fetch configuration. This is the API called by lb-driver.
curl -H "Authorization: Bearer xxxxx" "http://127.0.0.1:8080/fetch_sql_proxy_list?schema=xxxx"
## Optional parameters for the multi-AZ case: az (the AZ of the client, an explicit override), ip (the local IP, used to resolve the AZ), policy (the policy explicitly declared by the client, `default` when it is falling back), clientFeatureLevel (the client feature level; missing means 1), clientVersion (for logging only)
curl -H "Authorization: Bearer xxxxx" "http://127.0.0.1:8080/fetch_sql_proxy_list?schema=xxxx&ip=10.1.2.3&clientFeatureLevel=2&clientVersion=1.1.0"
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

* When a client does not report `ip`, config-server resolves the AZ from the source IP of the request, so nginx/nlb must forward `X-From-IP` and `X-Forwarded-For` (both already configured in the example above); `X-From-IP` is overwritten with `$remote_addr` and cannot be forged by a client, so it is the preferred source.
