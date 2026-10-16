
# 1.1.0-SNAPSHOT（2026/10/15）
### 新增
* remote 模式支持多可用区（AZ）感知：新增 URL 参数 `az`（本机可用区，可选，不配置即由服务端按上报的 `ip` 自动识别，属正常用法）、`ip`（本机 IP，可选，显式覆盖自动解析结果）、`azRefreshIntervalSeconds`（默认30s）、`recoverCheckCount`（默认2）
* driver 始终上报本机 `ip`（显式配置优先，否则取连接 config-server 的 socket 本地地址解析一次并缓存，解析失败则该次不发送并由服务端按请求来源 IP 兜底，解析按 `azRefreshIntervalSeconds` 退避重试），不把「是否上报 `ip`」与「是否配置 `az`」绑定：`az` 缺省正是自动识别可用区的场景，此时靠请求来源 IP 推断在 nginx/nlb 之后不可靠
* `az` 与 `ip` 支持用系统属性 `-Dlbd.az` / `-Dlbd.ip` 覆盖，优先级高于 url 上的同名参数：这两个值属于「部署环境」属性，同一份 url 常被一批机器共用（容器镜像、共享配置），只有 `-D` 才能按机器覆盖。取值留空等价于「不配置」（可在共享 url 已配置时给个别机器清掉）；与 url 参数一样拒绝含控制字符的取值，避免伪造日志行
* 请求config-server时恒定携带客户端能力等级 `clientFeatureLevel`（当前 `2`）与 `clientVersion`（仅用于config-server侧日志观测）。等级为单调递增整数，服务端只做整数比较，避免版本串比较踩坑；driver 侧用 `ClientFeatureLevel` 枚举把等级映射成日志可读名（`1` → `legacy`、`2` → `az-fallback`）
* 支持config-server响应新增的 `policy` 字段（本次响应实际依据的下发策略）：命中可用区为 `az`，未命中或未启用为 `default`。driver 只把「非空且不等于 `default`」的 `policy` 视为亲和能力声明并据此启用降级兜底，因此config-server未启用 AZ（返回 `default` 或不返回该字段）时 driver 不产生任何降级行为与噪音日志，新增下发模式时也无需再改 driver 判据；`304` 响应沿用上一次该变体的列表与 `policy`
* 本可用区集合全部不可达时自动降级：立即用 `policy=default` 请求一次默认（兜底）集合并用它承接新连接。降级采用边沿触发（状态位），`syncCreating` 这类高频路径不会重复发起兜底请求；运行期该次兜底拉取交给刷新线程异步执行，触发降级的业务线程不会被 config-server 的 connect/read 超时拖慢。该异步拉取按「同一时刻只保留一次在飞」去重：兜底集合生效前 az 可达性抖动会连续产生降级边沿，不去重会在单线程刷新器上排队多次拉取，各自占住锁到超时
* `SqlProxyProvider.onReachabilityCheck` 增加 `startup` 参数，把「启动轮允许阻塞、运行轮不得阻塞」写进 SPI 契约：`startup=true` 时由启动线程调用，provider 可直接同步收敛（启动成功与否取决于该轮回调返回时是否有可用节点）；`startup=false` 时可能跑在业务线程（`getConnection` → `syncCreating`）上，只能异步收敛并在下一轮回调下发
* 本可用区节点连续 `recoverCheckCount` 次健康检查通过后自动切回本可用区；兜底集合尚未生效时（拉取失败或服务端没有默认集合）没有可切换的集合，滞回上限压到 2 次，既扛住 az 单轮抖动又不会在 az 真恢复后拖着降级态不放；单层部署行为不变
* driver 只维护「当前需要用的集合」：非降级期间只有本可用区集合，降级期间为「兜底集合 + 本可用区集合」（本可用区集合留在池中继续被探测，恢复判定依赖池内节点的存活快照）。切回本可用区后兜底集合**从池中摘掉**，不再长期占着休眠节点与休眠连接（这些连接既不计入均衡、也永远轮不到回收），代价是下次故障重新加入时要重新探测与建链——故障是低频事件，这个代价可接受
* 降级期间兜底集合按5s刷新，本可用区集合按 `azRefreshIntervalSeconds` 刷新；两个请求变体各自维护独立的 `md5`。恢复后兜底集合已不在池中，仅按 `azRefreshIntervalSeconds` 慢速刷新缓存，降低下次降级的拉取代价（服务端返回空集合视为异常响应，跳过更新，不会把兜底集合清空）
* 池由「生效」转「非生效」时（被标记不参与负载均衡、被标记不可达、以不可用状态加入池）顺手回收池内已有的空闲连接，使用中的连接在归还时关闭；`close` 放在锁外，坏连接的关闭可能阻塞。这类池已不在选点队列与均衡队列里，滞留的空闲连接不会再被任何路径回收，只会让 `totalCount` 虚高：`rebalance` 会据此误判「物理连接富余」，转而从活跃池回收空闲连接，表现为兜底节点反复建连、断连
* 启动路径适配：初始化时本可用区无可达节点则直接进入降级流程（启动轮同步拉取兜底集合后再判定；两个集合都不可用时仍抛既有 `There is no reachable sql proxy`）
* az 相关逻辑全部收敛在 `RemoteSqlProxyProvider` 内部：`ConnectionManager` 只感知「节点集合」与「节点是否参与负载均衡」，新增 `setUsable` 回调与每轮可达性存活快照上报，连接池、负载均衡与选点路径不做任何 az 相关改动；`stats` 的 `SqlProxyStats` 增加 `usable` 字段（`online`/`reachable` 均为 `true` 却无流量时可据此判断是「在池但不参与负载均衡」），统计日志一并打印
* URL 校验更严格：`url` 含换行/回车/tab 时直接抛错、不再继续解析。`url` 与其参数既会被拼进请求参数也会打进日志，控制字符可以伪造出独立的日志行；jdbc url 本就应写成单行，带控制字符一定是配置错误
* 仅影响 remote 模式；`local` 模式与 `lb-driver-druid` 无改动

> 可用区定义（`az.list`）、可用区到 sql-proxy 的映射（`az.proxy`）与下发模式（`proxy.policy`）属于 config-server 侧改动，见下一节。

### config-server：多可用区下发
* 新增 `proxy.policy`（`default` / `az`，默认 `default`）、`az.list`（可用区名 + 所属网段，一个可用区支持多个网段，只支持ipv4）、`az.proxy`（可用区到sql-proxy节点列表的映射）三个可选字段；历史配置（只有 `proxy`）完全兼容，不配置即与引入多可用区之前行为完全一致
* `az.list` 支持在config-server的 `application.yml` 里配置全局值（`etcd`/`nacos`/`local` 三种数据源都支持，都在 `lb-driver-config-server.config` 下），配置了全局则各schema内可以缺省，schema内也配置了则以schema内为准
* schema内缺省 `proxy` 时，默认（兜底）集合取所有 `az.proxy` 节点的并集
* `/fetch_sql_proxy_list` 新增可选参数 `az`、`ip`、`policy`、`clientFeatureLevel`、`clientVersion`；可用区匹配优先级为「请求显式 `policy=default`」→「请求 `az`（需在生效 `az.list` 里）」→「请求 `ip`」→「请求来源ip（`X-From-IP`、`X-Real-IP`、`X-Forwarded-For` 首值、`RemoteAddr`）」→「默认集合」，未命中可用区、命中的可用区未配置节点、客户端能力等级不足时都下发默认集合。来源ip 的候选顺序把接入层覆盖写入、客户端不可伪造的 `X-From-IP` / `X-Real-IP` 放在调用方可自由构造的 `X-Forwarded-For` 之前；该顺序只用于推断可用区（性能优化），授权仍由 apiKey 把关，不作为安全边界
* 响应新增 `policy` 字段（本次实际依据的下发策略）；摘要包含 `policy` 与命中的可用区，因此「不同可用区、不同策略」天然得到不同的 `md5`，避免共享 `md5` 导致客户端因 `304` 拿到过期的策略认知。服务端不再维护 md5 缓存：直接比对本次算出的摘要（配置刚变更时旧实现会给出过期的 `304`），md5 缓存的清理问题随之消失
* `/fetch_sql_proxy_list` 的 `Authorization` 头不是 `Bearer ` 前缀时按鉴权失败返回 `403`（此前 `substring` 会抛越界异常变成 `500`，会被当成服务端故障触发告警）
* 能力门禁：只有携带 `clientFeatureLevel >= 2` 的客户端才会拿到可用区集合，老版本driver（不带该参数，按1处理）拿到的仍是默认集合，因此不会静默失去跨可用区兜底能力
* 配置校验（配置不合法时该schema加载/更新失败并保留上一份配置）：`proxy.policy` 非法值拒绝加载；`az.proxy` 的key必须在生效 `az.list` 里；同一节点不得归属多个可用区；可用区之间网段不得重叠；`proxy.policy=az` 时生效 `az.list` 与 `az.proxy` 都不能为空；`proxy.policy` 还是 `default` 而配置了 `az.proxy` 时允许加载但打warn提示不生效（便于先铺配置再切策略）
* 节点可达性探测改为去重后并发执行：默认集合与各可用区节点合并去重，同一节点只探测一次，`/monitor` 也不再按 2×N 次串行 1s 连接探测，加载与监控响应不再随节点数线性变慢
* 探测线程池改为「有界队列 + `CallerRunsPolicy`」：池满时由提交线程直接执行探测，既保持并发又不会让任务堵在队列里等到超时被误判成「不可达」（无界队列下该策略永远不触发）；确实没在等待预算内回收的节点只打「结论未知」日志，不会被报成 `not reachable`
* `/monitor` 的 `schemaInfo` 增加 `policy`、`azSize` 字段，并新增 `azProxyDetail` 展示各可用区节点的可达性；`reachable` 取值为 `1` 可达、`0` 明确连不通、`-1` 探测未在等待预算内完成（结论未知，不要当成不可达）
* 新增单测覆盖配置解析与校验、可用区匹配优先级与响应摘要（`lb-driver-config-server` 模块 36 个用例）

# 1.0.1（2026/08/13）
### 新增
* remote URL 省略 config-server 端口时默认使用 80

### 修复
* 修复底层物理连接发生致命 SQL 异常后，逻辑连接仍可继续执行后续操作的问题
* 修复连接关闭时清理失败可能导致逻辑连接未注销或异常物理连接回池的问题
* 实现 `isValid()`（此前为恒返回 false 的占位实现），使连接池能正确校验逻辑连接：已关闭或已损坏的连接返回 false，空闲态返回 true，持有物理连接时委托其 `isValid()` 实测
* 健壮化致命异常处理路径：先将逻辑连接标记为损坏再释放物理连接，避免并发 `isValid()` 短暂误判；`realConnection` 改为 `volatile`；关闭 Statement 的异常不再掩盖原始 fatal 异常
* `isClosed()` 现在在连接已关闭或因 fatal 损坏时都返回 true（此前损坏连接仍返回 false，与实际不可用状态不一致）

> 致命异常处理的改动细节、与各连接池（Druid / HikariCP / DBCP / c3p0 / tomcat-jdbc）的兼容性及版本选择建议，详见 [doc/fatal_error.md](doc/fatal_error.md)。

### 安全
* `/health/online` 和 `/health/offline` 默认仅允许本机 loopback 地址调用，可通过 `-DconfigServerOnlineOfflineLoopbackOnly=false` 关闭限制

# 1.0.0（2026/01/12）
### 新增
* 第一次发布
