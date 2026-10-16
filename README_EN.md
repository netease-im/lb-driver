# lb-driver

<img src="/doc/logo.png" width = "300"/>

Load-Balance-Driver, wrapper of mysql-jdbc-driver, suitable for use in databases with proxy architecture, such as OceanBase, TiDB, etc.

![GitHub](https://img.shields.io/badge/license-MIT-blue.svg)
[![GitHub release](https://img.shields.io/github/release/netease-im/lb-driver.svg)](https://github.com/netease-im/lb-driver/releases)
![java_language](https://img.shields.io/badge/java--language-1.8%20%7C%2021-blue.svg)

English | [简体中文](README.md)

## Introduction

* This project references the LBDriver implementation from NetEase's internal DDB sharding middleware, but the code has been rewritten to support more general scenarios.
* It is suitable for scenarios where applications need to connect directly to sql-proxy instead of through a load balancer such as NLB or SLB, moving the load-balancing logic from the NLB/SLB layer down into the driver layer.
* This helps distribute sql-proxy load more evenly, reduces RT by shortening the network path, and prevents load balancers such as NLB or SLB from becoming performance bottlenecks.
* The driver automatically performs health detection, shields failed sql-proxy nodes, and balances the number of connections across sql-proxy nodes.
* When integrated with config-server, sql-proxy nodes can be brought online or offline quickly without restarting the application process.
* It depends only on slf4j-api and mysql-connector-java, with no other dependencies.
* It is suitable for OceanBase, TiDB, and similar databases.

## Architecture

<img src="doc/img.png" width = "500"/>

## Usage

### Dependency

```xml
<dependency>
    <groupId>com.netease.nim</groupId>
    <artifactId>lb-driver</artifactId>
    <version>1.0.1</version>
</dependency>
```
```
driver=com.netease.nim.lbd.LBDriver
```

### Local Mode

* The connection URL contains the sql-proxy node list. lb-driver obtains the complete sql-proxy node list directly from the URL and automatically removes unavailable sql-proxy nodes.

Connection URL example:
```
jdbc:mysql:lb:local://10.189.0.1:6000,10.189.0.2:6000,10.189.0.3:6000/mydatabase?connectTimeout=5000&socketTimeout=10000&logStats=true
```

* `logStats` specifies whether to print statistics logs. Optional. The default is false.
* `checkBalanceIntervalSeconds` specifies the interval of the scheduled load-balancing task. Optional. The default is 10s.
* `checkHealthIntervalSeconds` specifies the interval of the scheduled health-check task. Optional. The default is 5s.
* `unsupportedMethodBehavior` specifies the behavior when an unsupported method is called: `throwException` or `ignoreCall`. Optional. The default is `throwException`.
* `exceptionSorter` specifies which exceptions require connection recreation. Configure the fully qualified class name of a class that implements the `ExceptionSorter` interface. The default is `com.netease.nim.lbd.MySqlExceptionSorter`.


### Config-Server Mode

* The connection URL contains the config-server address. lb-driver dynamically obtains the sql-proxy node list from config-server, so sql-proxy scaling can be performed without restarting the application.
* config-server is a stateless service. Multiple nodes can be deployed behind nginx or NLB to provide high availability.

Connection URL example:
```
jdbc:mysql:lb:remote://config-server.xxx.com:8080/mydatabase?connectTimeout=5000&socketTimeout=10000&configServerApiKey=xxx&configServerSchema=im_user&logStats=true
```

* `config-server.xxx.com:8080` is the config-server address. It must use the host:port format. If the port is omitted, it defaults to 80. Port 443 uses HTTPS; all other ports use HTTP. Required.
* `configServerApiKey` is the authentication key for config-server. Optional, depending on whether config-server authentication is enabled.
* `configServerSchema` is the schema configured in config-server. Required.
* `configServerTimeout` specifies the timeout for accessing config-server, in milliseconds. Optional. The default is 5000.
* `logStats` specifies whether to print statistics logs. Optional. The default is false.
* `checkBalanceIntervalSeconds` specifies the interval of the scheduled load-balancing task. Optional. The default is 10s.
* `checkHealthIntervalSeconds` specifies the interval of the scheduled health-check task. Optional. The default is 5s.
* `unsupportedMethodBehavior` specifies the behavior when an unsupported method is called: `throwException` or `ignoreCall`. Optional. The default is `throwException`.
* `exceptionSorter` specifies which exceptions require connection recreation. Configure the fully qualified class name of a class that implements the `ExceptionSorter` interface. The default is `com.netease.nim.lbd.MySqlExceptionSorter`.

* `az` specifies the availability zone of the local machine. Optional. The value is an operation-defined zone name; the driver neither validates nor hardcodes it. It can also be overridden with `-Dlbd.az=xxx`, which takes precedence over the URL parameter (one URL is often shared by a batch of machines, so only `-D` can override it per machine).
* `ip` specifies the local machine IP. Optional. It is used by config-server to resolve the availability zone and is reported by default: an explicitly configured `ip` wins, otherwise the driver resolves it once from the local address of the socket connected to config-server and caches the result. If resolution fails, the parameter is not sent for that request, config-server falls back to the request source IP, and the driver retries after backing off for `azRefreshIntervalSeconds` (each retry really connects to config-server, so retrying at every refresh would stall the refresh thread). `ip` is reported even when `az` is not configured, which is the normal auto-detection case.
* `-Dlbd.ip=xxx` takes precedence over the `ip` in the URL, so it can be overridden per machine. An empty value for `-Dlbd.az` / `-Dlbd.ip` (e.g. `-Dlbd.az=`) means "not configured", which is handy to clear a value of a URL shared by many machines.
* `azRefreshIntervalSeconds` specifies the interval of refreshing the local-zone sql-proxy list while degraded. Optional. The default is 30s.
* `recoverCheckCount` specifies how many consecutive successful health checks are required for a node to flip back to reachable. Optional. The default is 2.

#### Multi-AZ awareness

* The normal usage relies on auto-detection only: leave `az` unset, the driver reports the local IP, and config-server derives the zone from the network segment. `az` is an explicit override for cases where auto-detection does not apply or is inaccurate.
* The driver always reports the local `ip` to config-server, passes `az` as well when it is configured, and always carries the client feature level `clientFeatureLevel` (currently `2`) together with `clientVersion` (for config-server log observation only).
* When config-server enables `proxy.policy=az`, it returns the sql-proxy list of the client availability zone. If the policy is not enabled, no zone is matched, or the client feature level is insufficient, the default (full) list is returned.
* If the whole local-zone list becomes unreachable, the driver immediately requests the default list once with `policy=default` on the refresh thread (so the triggering business thread is never blocked), uses it for new connections, and logs the degraded state. Once a local-zone node passes `recoverCheckCount` consecutive health checks, the driver switches back automatically (while the default list has not been obtained yet this is capped at 2 checks, so flapping cannot repeatedly fire fallback pulls).
* The driver only maintains the set it currently needs: the local-zone list alone when healthy, and the default list plus the local-zone list while degraded. The local-zone nodes stay in the pool and keep being probed (recovery is decided from them) but take no part in load balancing. After switching back, the default list is detached from the pool and added back on demand at the next failure (re-probing and re-connecting).
* While degraded, the default list is refreshed every 5s and the local-zone list every `azRefreshIntervalSeconds`; the two request variants maintain independent `md5` values.
* This feature only affects which sql-proxy nodes are used. Connection balance, connection pool and health check semantics are unchanged. Behavior is identical to before when `az` is not configured.
* An older driver (without the feature level) still receives the default list even if config-server enables AZ affinity, so it never silently loses cross-zone fallback.

For config-server deployment configuration, see [config_server](doc/config_server_EN.md).


## Test Cases

See [test_case](doc/test_case_EN.md).


## Example Code

See [example](lb-driver-example).

## Changelog

See [changelog](update_EN.md).

## Blog Post

[NetEase open-sources lb-driver: Driver-layer load balancing practice for OceanBase OBProxy](https://open.oceanbase.com/blog/27234102304)

## Related Links

[OceanBase](https://github.com/oceanbase/oceanbase)
[TiDB](https://github.com/pingcap/tidb)
