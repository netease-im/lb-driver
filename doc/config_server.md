
## config-server使用方式

* config-server是一个spring-boot的web工程，最低要求java21，配置文件包括 `application.yml` 和 `logback.xml`
* 通过配置 `application.yml` 可以选择不同的配置中心，当前支持 `etcd`、`nacos`、`local`，你也可以自定义（需要java开发能力）

### config-server的部署

* 下载jar包，地址：[lb-driver-config-server-bootstrap-1.0.1.jar](https://github.com/netease-im/lb-driver/releases/download/v1.0.1/lb-driver-config-server-bootstrap-1.0.1.jar)；或者自行编译 `lb-driver-config-server-bootstrap`
* 参考 [application.yml](../lb-driver-config-server-bootstrap/src/main/resources/application.yml) 修改成适合你自己的 `application.yml`
* 参考 [logback-example.xml](../lb-driver-config-server-bootstrap/src/main/resources/logback-example.xml) 修改成适合你自己的 `logback.xml`
* 参考 [startup.sh](startup.sh) 修改：java_home、jar包路径、application路径、logback.xml路径等，随后启动即可

### 使用etcd作为数据源

```yaml
lb-driver-config-server:
  config-type: etcd
  config:
    "etcd.target": "ip:///etcd0:2379,etcd1:2379,etcd2:2379"
#    "etcd.endpoints": "http://etcd0:2379,http://etcd1:2379,http://etcd2:2379" #etcd.target和etcd.endpoints二选一，优先使用etcd.target
#    "etcd.user": "xx"
#    "etcd.password": "xx"
#    "etcd.namespace": "xx"
#    "etcd.authority": "xx"
    "etcd.config.key.prefix": "/obproxy/yunxin"
```
* 其中 `/obproxy/yunxin/{schema}` 表示某个schema的配置


### 使用nacos作为数据源

```yaml
lb-driver-config-server:
  config-type: nacos
  config:
    "nacos.serverAddr": "127.0.0.1:8848"
    "nacos.group": "xxx"
    "nacos.username": "xxx"
    "nacos.password": "xxx"
    "nacos.init.schema.list": "xxx,yyy,zzz" #逗号分隔
```
* 其中 `dataId={schema}` 表示某个schema的配置
* 其中 `nacos.init.schema.list` 表示初始化时加载的schema列表


### 使用本地配置文件作为数据源

```yaml
lb-driver-config-server:
  config-type: local
  config:
    "local.config.file": "config.json"
#    "local.config.file.path": "/xxx/xx/config.json"
```
* `config.json` 是一个json数组，每个元素表示一个schema
* 可以配置文件名（classpath下），也可以配置文件绝对路径（优先级更高）


### 配置示例

* config-server 从数据源获取配置，配置要求为json结构，如下：
* `etcd` 或者 `nacos` 更新配置后，config-server会自动监听配置变更，lb-driver也会在几秒内获取到最新的sql-proxy节点列表
* `local` 更新配置后，需要调用 `/reload` 接口才会更新配置，随后，lb-driver也会在几秒内获取到最新的sql-proxy节点列表

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

* `schema` 表示sql-proxy归属的schema
* `auth.enable` 表示lb-driver请求config-server时是否鉴权，默认false
* `api.keys` 表示鉴权的api-key，支持多个
* `proxy` 表示配置的sql-proxy节点列表

### 多可用区（AZ）下发

* config-server支持按客户端所在可用区下发sql-proxy集合，让业务机器优先访问同可用区的sql-proxy，降低跨可用区延迟
* 该能力默认关闭：`proxy.policy` 缺省为 `default`，此时所有客户端拿到的都是默认集合，行为与引入多可用区之前完全一致
* 只有具备「本可用区集合整体不可达时降级到默认集合」能力的客户端（`clientFeatureLevel >= 2`，即 1.1.0 及以上的 lb-driver）才会拿到可用区集合；老版本客户端拿到的仍是默认集合，不会静默失去跨可用区兜底能力
* 只影响下发的sql-proxy集合，不改变config-server的其他行为（鉴权、reload、监控、健康检查均不变）

可用区定义（`az.list`）可以配置在config-server的 `application.yml` 里作为全局配置，`etcd`、`nacos`、`local` 三种数据源都支持（都在 `lb-driver-config-server.config` 下），配置了全局则各schema内可以缺省；schema内也配置了则以schema内为准：

```yaml
lb-driver-config-server:
  config-type: etcd
  config:
    "az.list": '[{"name":"az1","cidr":["10.1.0.0/16"]},{"name":"az2","cidr":["10.2.0.0/16"]}]'
    "etcd.target": "ip:///etcd0:2379,etcd1:2379,etcd2:2379"
    "etcd.config.key.prefix": "/obproxy/yunxin"
```

schema配置新增三个字段，历史配置（只有 `proxy`）完全兼容：

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

* `proxy.policy` 下发策略，`default` 或 `az`，可选，默认 `default`；`az` 表示按客户端可用区下发
* `az.list` 可用区定义，可选（可只配置在config-server的全局配置里）。`name` 是运维约定的可用区名，config-server不校验、不硬编码；`cidr` 是该可用区所属网段，支持配置多个，只支持ipv4
* `az.proxy` 可用区到sql-proxy节点列表的映射，可选。key必须是生效 `az.list` 里的可用区名；未配置节点的可用区，其客户端会拿到默认集合
* `proxy` 默认（兜底）集合，可选；schema内缺省时，取所有 `az.proxy` 节点的并集

配置校验（配置不合法时该schema加载/更新失败，保留上一份配置，并在日志中打出具体原因）：

* `proxy.policy` 非法值拒绝加载
* `az.proxy` 的 key必须在生效 `az.list` 里
* 同一sql-proxy节点不得归属多个可用区（否则「本可用区集合」失去意义）
* 可用区之间网段不得重叠（否则同一个ip会命中两个可用区，匹配结果取决于顺序）
* `proxy.policy=az` 时，生效 `az.list` 与 `az.proxy` 都不能为空（避免「配置了AZ亲和却静默不生效」）
* `proxy.policy` 还是 `default` 而配置了 `az.proxy` 时允许加载（便于先铺配置再切策略），但会打warn日志提示该配置不生效

可用区匹配优先级（`proxy.policy=az` 且客户端能力等级足够时，命中即返回）：

1. 请求带 `policy=default`：下发默认集合（客户端降级时用它拿全量集合兜底）
2. 请求带 `az` 且该可用区在生效 `az.list` 里：下发该可用区的集合（显式覆盖，正常用法不传）
3. 请求带 `ip`：按 `ip` 反查可用区
4. 按请求来源ip反查可用区，依次取 `X-From-IP`、`X-Real-IP`、`X-Forwarded-For` 首值、`RemoteAddr`
5. 未命中可用区、命中的可用区未配置节点、或客户端能力等级不足：下发默认集合

来源ip 的候选顺序把接入层覆盖写入、客户端不可伪造的 `X-From-IP`（`$remote_addr`）与 `X-Real-IP` 放在前面：`X-Forwarded-For` 若按 `$proxy_add_x_forwarded_for` 追加，首值是调用方自己传进来的，可被任意构造（下面的示例配置即为此写法）。该顺序只用于推断可用区、属性能优化，不作为安全边界，授权始终由 `apiKey` 把关。

响应新增 `policy` 字段，表示本次实际依据的下发策略（`az` 或 `default`）。客户端把「非空且不等于 `default`」的 `policy` 视为亲和能力声明并据此启用降级兜底，因此后续新增下发模式时客户端判据无需改动；未被声明亲和能力（返回 `default` 或不返回该字段）时客户端不做任何降级行为。`md5` 摘要包含 `policy` 与命中的可用区，因此不同可用区、不同策略天然得到不同的摘要，避免共享 `md5` 导致客户端因 `304` 拿到过期的策略认知（服务端不缓存摘要，直接比对本次算出的结果）。

### 接口文档

```
## 获取配置，lb-driver调用的就是这个接口
curl -H "Authorization: Bearer xxxxx" "http://127.0.0.1:8080/fetch_sql_proxy_list?schema=xxxx"
## 多可用区场景下可带的可选参数：az（本机可用区，显式覆盖自动识别）、ip（本机IP，用于反查可用区）、policy（客户端显式声明的策略，降级时传default）、clientFeatureLevel（客户端能力等级，缺失按1处理）、clientVersion（仅用于日志观测）
curl -H "Authorization: Bearer xxxxx" "http://127.0.0.1:8080/fetch_sql_proxy_list?schema=xxxx&ip=10.1.2.3&clientFeatureLevel=2&clientVersion=1.1.0"
```

```
## 强制reload，local则重新读取本地配置文件，nacos/etcd则重新去远程拉取一次配置
curl "http://127.0.0.1:8080/reload"
```

```
## 监控数据
curl "http://127.0.0.1:8080/monitor"
```

```
## 健康检查接口
curl "http://127.0.0.1:8080/health/status"
## 上线接口，调用后，/health/status返回200
curl "http://127.0.0.1:8080/health/online"
## 下线接口，调用后，/health/status返回500
curl "http://127.0.0.1:8080/health/offline"
```

### nginx配置示例

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

* 客户端未上报 `ip` 时，config-server按请求来源ip反查可用区，因此nginx/nlb需要透传 `X-From-IP` 与 `X-Forwarded-For`（上面的示例已配置）；`X-From-IP` 用 `$remote_addr` 覆盖写入，客户端无法伪造，是首选依据
