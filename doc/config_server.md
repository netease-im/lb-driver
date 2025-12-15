
## config-server使用方式

* config-server是一个spring-boot的web工程，最低要求java21，配置文件包括 `application.yml` 和 `logback.xml`
* 通过配置 `application.yml` 可以选择不同的配置中心，当前支持 `etcd`、`nacos`、`local`，你也可以自定义（需要java开发能力）

### config-server的部署

* 下载jar包，地址：xxx；或者自行编译 `lb-driver-config-server-bootstrap`
* 参考 [application.yml](../lb-driver-config-server-bootstrap/src/main/resources/application.yml) 修改成适合你自己的 `application.yml`
* 参考 [logback-example.xml](../lb-driver-config-server-bootstrap/src/main/resources/logback-example.xml) 修改成适合你自己的 `logback.xml`
* 参考 [startup.sh](startup.sh) 修改：java_home、jar包路径、application路径、logback.xml路径等，随后启动即可

### 使用etcd作为数据源

```yaml
lbd-driver-config-server:
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
lbd-driver-config-server:
  config-type: nacos
  config:
    "nacos.serverAddr": "127.0.0.1:8848"
    "nacos.group": "yunxin"
```
* 其中 `dataId={schema}` 表示某个schema的配置


### 使用本地配置文件作为数据源

```yaml
lbd-driver-config-server:
  config-type: local
  config:
    "local.config.file": "config.json"
    #"local.config.file.path": "/xxx/xx/config.json"
```
* `config.json` 是一个json数组，每个元素表示一个schema
* 可以配置文件名（classpath下），也可以配置文件绝对路径（优先级更高）


### 配置示例

* config-server 从数据源获取配置，配置要求为json结构，如下：
* `etcd` 或者 `nacos` 更新配置后，config-server会自动监听配置变更，lbd-driver也会在几秒内获取到最新的sql-proxy节点列表
* `local` 更新配置后，需要调用 `/reload` 接口才会更新配置，随后，lbd-driver也会在几秒内获取到最新的sql-proxy节点列表

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
* `auth.enable` 表示lbd-driver请求config-server时是否鉴权，默认false
* `api.keys` 表示鉴权的api-key，支持多个
* `proxy` 表示配置的sql-proxy节点列表

### 接口文档

```
## 获取配置，lb-driver调用的就是这个接口
curl -H "Authorization: Bearer xxxxx" "http://127.0.0.1:8080/fetch_sql_proxy_list?schema=xxxx"
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
