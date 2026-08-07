# lb-driver Fatal 异常处理与连接池兼容性说明

> 本文说明 lb-driver 在 **1.0.0** 与 **1.0.1**（提交 `f0b451c`）两版中 fatal 异常处理的差异，以及它对 Druid / HikariCP / DBCP / c3p0 / tomcat-jdbc 这几款连接池的影响，并给出每个池子应该使用哪个版本的建议。

---

## 0. TL;DR（结论先行）

| 连接池 | 1.0.0 风险 | 建议 |
|---|---|---|
| **HikariCP** | 高（OB 专属 fatal 分类不一致 → 哨兵不换 → `commit()` 静默 no-op 可达） | **必须升级 1.0.1** |
| **DBCP** | 高（使用期透传 → `commit()` 静默 no-op 直接可达） | **必须升级 1.0.1** |
| **c3p0** | 高（使用期透传 → `commit()` 静默 no-op 直接可达） | **必须升级 1.0.1** |
| **tomcat-jdbc** | 高（无异常分类器、纯透传 → `commit()` 静默 no-op 直接可达） | **必须升级 1.0.1** |
| **Druid** | 中（自带 sorter 与 lb-driver 高度一致 → 通常能挡住；但有 `close()` 泄漏 + 边缘不一致） | **建议升级 1.0.1**；1.0.0 仅在确信异常分类一致、且无法立即升级时可临时停留 |

> 除 Druid 外，**建议所有池子统一升级到 1.0.1**。1.0.1 同时包含两项修复：fatal 后 `commit()` 不再静默 no-op（核心）、以及 `isValid()` 不再恒返回 false（详见第 6 节）。
>
> ⚠️ **若仍停留在 1.0.0**：`isValid()` 恒返回 false，连接池**必须**改用 `validationQuery = SELECT 1`（HikariCP 用 `connectionTestQuery`、c3p0 用 `preferredTestQuery`），否则池子无法正常工作。

---

## 1. 背景与定位

lb-driver 是基于 MySQL 协议封装的、带负载均衡能力的 JDBC Driver，用于连接 OceanBase / TiDB 这类前面挂了 proxy 的数据库，在多个 proxy 节点间做负载均衡。

它实现的是 `java.sql.Connection`（逻辑连接 `LBConnection`）。**上层连接池会把 `LBConnection` 当作"物理连接"来包装。** 调用链：

```
应用代码 → 连接池(DruidPooledConnection / HikariProxyConnection / ...)
         → LBConnection (lb-driver 逻辑连接)
         → ConnectionManager → RealConnection (某个 proxy 节点的物理连接)
         → proxy → OceanBase / TiDB
```

所以 `LBConnection` 处于"连接池的物理连接"这一层。fatal 异常发生时，**lb-driver 和连接池各自都会做 fatal 处理**，两边是否配合，决定了是否安全。

---

## 2. lb-driver 在 fatal 时做了什么（1.0.0 与 1.0.1 通用）

入口是 `LBConnection.errorWrapper()`（`LBConnection.java:87`）：

1. 用 lb-driver 自带的 `MySqlExceptionSorter.isExceptionFatal()` 判定是否 fatal。
2. 一旦判定 fatal：
   - `realConnection.markUnhealthy()` —— 把物理连接标记为不健康（归还时会被 lb-driver 内部淘汰）。
   - `closeStatements()` —— 关闭该逻辑连接上追踪的所有 Statement。
   - `connectionManager.returnConnection(realConnection)` —— 归还物理连接。
   - `realConnection = null` —— **清空逻辑连接持有的物理引用**。
   - （1.0.1 新增）`isBroken = true; fatalException = sqlEx;`
3. 把原始 `SQLException` 原样上抛给上层（连接池）。

**关键：无论 1.0.0 还是 1.0.1，fatal 时 lb-driver 都会立刻释放物理连接、并把逻辑连接的 `realConnection` 置空。两个版本的区别只在于——fatal 之后，如果还有代码继续调用这个"已损坏的逻辑连接"的方法，会发生什么。**

> lb-driver 的 `MySqlExceptionSorter` 是专门为 MySQL / OceanBase / TiDB proxy 调优的，判 fatal 的条件包括：`SQLRecoverableException`、SQL state 以 `08` 开头、一组 MySQL 通信/认证/资源错误码、**OceanBase 错误码 `-8000 ~ -9000`**、`CommunicationsException` 类、消息匹配 `COMMUNICATIONS LINK FAILURE` / `COULD NOT CREATE CONNECTION` / **`NO DATASOURCE` / `NO ALIVE DATASOURCE`**（OB proxy 典型错误）、以及因链里的 `SocketTimeoutException`。

---

## 3. 1.0.0 的行为（有缺陷）

fatal 之后，`LBConnection` 处于 `realConnection == null` 且 `isClosed == false` 的状态。此时所有方法仍只走 `checkClosed()`（只看 `isClosed`，而 fatal 不会置位 `isClosed`），于是：

| 调用 | 1.0.0 的实际行为 | 危害 |
|---|---|---|
| `commit()` | `checkClosed` 通过 → `if (realConnection != null)` 跳过 → **静默返回成功，什么都没做** | **应用以为事务提交成功，实际事务已随物理连接断开被 DB 回滚 → 静默数据丢失/不一致** |
| `rollback()` | 同上，静默 no-op | 应用以为回滚成功（DB 侧确实回滚了，危害相对小，但契约被破坏） |
| `setAutoCommit(...)` | 静默 no-op | 状态与实际不符 |
| `createStatement()` / `prepareStatement()` | `checkClosed` 通过 → `initCurrentConnection()` → **静默重新借一条新物理连接**继续 | 在已损坏的事务上下文里"换了一条线"继续跑，语义错乱 |
| `close()` | 非健壮：`closeStatements()` 抛异常时，后面的 `unregisterLogicalConnection` / `isClosed=true` 被跳过 | **资源泄漏**：逻辑连接未注销、引用未清空 |

**1.0.0 最致命的是 `commit()` 的静默 no-op**：上层（应用代码 / 事务管理器）catch 到 fatal 后若再调 `commit()`，得到的是"成功"，而事务其实已经失败——这是静默的数据正确性问题，且**没有任何异常可观测**。

---

## 4. 1.0.1 的修复（提交 `f0b451c`）

1. **新增 `isBroken` / `fatalException` 标记**，fatal 时在 `errorWrapper` 里置位。
2. **新增 `checkStatus()`**：在 `checkClosed()` 基础上，若 `isBroken` 则抛 `SQLException("No operations allowed after connection broken by fatal error. error = ...", 原SQLState)`。
3. **把下列方法的前置校验从 `checkClosed()` 改成 `checkStatus()`**：
   - `commit()`
   - `setAutoCommit()`
   - 所有 `createStatement(...)` 重载
   - 所有 `prepareStatement(...)` 重载
   - `LBStatement.checkOpen()`（即已创建的 Statement 上继续执行也会被拒）
4. **重写 `close()`**：用 `try/finally` 保证 `unregisterLogicalConnection`、`realConnection=null`、`isClosed=true` 必定执行；清理失败时 `markUnhealthy`。
5. **修复 `isValid()`**：1.0.0 里 `isValid()` 是恒返回 `false` 的 stub；1.0.1 改为 `已关闭/已损坏 → false`、`懒加载空闲态 → true`、`持有物理连接 → 委托 physical.isValid()`。这让用 `isValid` 校验的池子能正常工作并识别坏连接（详见第 6 节）。

> ⚠️ 精确说明：`rollback()` 在 1.0.1 中**仍走 `checkClosed()`，仍是 no-op**。这是有意为之且可接受——rollback-after-fatal 在语义上是无害的（DB 侧事务已随连接断开回滚，应用"以为回滚成功"与实际一致）。**真正必须修复的是 `commit()` 的静默 no-op**，1.0.1 已经修掉。

**升级到 1.0.1 后，fatal 之上再调 `commit()` / 创建并执行 Statement，都会明确抛出 "No operations allowed after connection broken by fatal error"，不再静默；`close()` 也不再因清理异常而泄漏。**

---

## 5. 关键交互：lb-driver 与连接池的"异常分类是否一致"

fatal 发生后，原始异常会继续往上抛到连接池。**连接池自己的 fatal 分类器也会对这个异常做判定**：

- 若连接池**也**判定为 fatal 并"挡住后续操作"（Druid 的 disable、HikariCP 的换哨兵）→ 后续调用被连接池挡在它的那一层，**打不到 LBConnection**，1.0.0 的 no-op 就不会被触发。
- 若连接池**没**判定为 fatal，或连接池本身"使用期透传"（DBCP / c3p0 / tomcat-jdbc）→ 后续调用会打到已损坏的 LBConnection → **1.0.0 的 no-op 就会被触发**。

各连接池自带分类器与 lb-driver 的一致性：

| 连接池 | 自带 fatal 分类 | 与 lb-driver 一致性 |
|---|---|---|
| **Druid** | 按方言的 `MySqlExceptionSorter`（同样覆盖 `08*`、MySQL 错误码、`CommunicationsException`） | **高**（两者几乎同源，多数场景一致） |
| **HikariCP** | 通用：`08*` + 少量固定 SQL state / error code，**无 MySQL/OB 专属判定** | **低**：`08S01` 类通信错误能对上；但 OB 的 `-8000~-9000`、`NO ALIVE DATASOURCE` 等会被 lb-driver 判 fatal、HikariCP 不认 → 不一致 |
| **DBCP** | 通用：`08*` + 少量断连码 | 低（同 HikariCP） |
| **c3p0** | 异常时跑一次 `isValid`/test query 实测（1.0.1 起 `isValid` 已正常工作） | 取决于校验方式 |
| **tomcat-jdbc** | **无异常分类器** | 无（永远不分类） |

> 注意：DBCP / c3p0 / tomcat-jdbc 在**使用期间是透传的**——无论分类是否一致，后续调用都会打到 LBConnection。

---

## 6. `LBConnection.isValid()` 的行为（1.0.0 有缺陷，1.0.1 已修复）

**历史问题**：1.0.0 里 `isValid()` 是未实现的 stub，**恒返回 `false`**，导致任何用 `isValid` 校验的池子（HikariCP 借出、DBCP `testOnBorrow`、tomcat-jdbc `testOnBorrow`/`testWhileIdle`、c3p0）会把**每一条** LBConnection 都判成无效，连接池根本无法正常工作。

**1.0.1 已修复**（与 checkStatus 修复同期），现在 `isValid()` 的判定逻辑（`LBConnection.java:549`）：

```java
public boolean isValid(int timeout) throws SQLException {
    if (isClosed) return false;                       // ① 已关闭 → 无效
    if (isBroken) return false;                       // ② ★ fatal 过 → 无效（关键：让池子能识别坏连接）
    if (realConnection == null) return true;          // ③ 懒加载未获取 / 空闲已回收 → 有效
    return realConnection.getPhysicalConnection().isValid(timeout);  // ④ 持有物理连接 → 委托实测
}
```

含义与影响：

- **坏连接（fatal 过）现在返回 false** → 用 `isValid` 校验的池子能在借出/校验时识别并淘汰坏连接。这是 1.0.1 相对 1.0.0 的又一重要改进。
- **`realConnection == null` 时返回 true**：这是"懒加载未获取物理连接"或"空闲态已归还物理连接"的状态。返回 true 是合理的——下一次真实操作会通过 `initCurrentConnection()` 现取一条新的物理连接。**副作用**：这种状态下 `isValid` 不会真正探测后端，真正的连通性验证延后到首次使用时发生（连不上即 fail-fast）。
- **持有物理连接时**委托给 `physical.isValid()`（MySQL driver 下即 COM_PING，很轻）。
- 这对 DB 重启场景反而是**好事**：被回收的空闲连接（`realConnection == null`）不持有死物理连接，`isValid` 返回 true，借出后首次操作现取新物理连接 → 自愈；而持有死物理连接的连接，`isValid` 委托实测返回 false → 被淘汰。

> **结论：1.0.1 之后，连接池可以用 `isValid` 做校验了**（不再强制要求 `validationQuery`）。`validationQuery` 仍是可选的——当你想用指定 SQL 而非 driver 的 `isValid`/COM_PING 时才需要配。
> Druid 对 MySQL 默认用 `PingConnectionChecker`（COM_PING），不经过 `isValid()`，行为不受影响。
>
> ⚠️ **仍停留在 1.0.0 的用户**：`isValid` 恒 false，**必须**配 `validationQuery = SELECT 1`（HikariCP 用 `connectionTestQuery`、c3p0 用 `preferredTestQuery`），否则连接池无法正常工作。

---

## 7. 各连接池详细分析

### 7.1 HikariCP —— 必须升级 1.0.1

- HikariCP 自带分类器是通用的，**不认 OB 的 `-8000~-9000`、`NO ALIVE DATASOURCE` 等**。
- 当 lb-driver 判 fatal、而 HikariCP **不**判 fatal 时：HikariCP 不会把 delegate 换成 `CLOSED_CONNECTION` 哨兵，连接在 HikariCP 眼里还是"好的"。
- 于是上层后续调 `commit()` 会穿过 HikariCP 打到已损坏的 LBConnection → **1.0.0 下 `commit()` 静默 no-op，应用误以为提交成功**。
- 这类 OB 专属 fatal 在对接 OB/TiDB proxy 时并不罕见，所以 HikariCP + 1.0.0 是**高危**组合。
- 升级到 1.0.1 后，即使 HikariCP 没判 fatal，LBConnection 自己的 `checkStatus()` 也会抛错，正确暴露失败。
- **配置要求**：1.0.1 已修复 `isValid`，**默认借出校验即可正常工作，无需额外配置**；若停留在 1.0.0，则 `isValid` 恒 false，**必须**配 `connectionTestQuery = SELECT 1`。

### 7.2 DBCP —— 必须升级 1.0.1

- DBCP 在使用期**完全透传**：fatal 后它只设一个 `fatalSqlExceptionThrown` 标记（且默认 `fastFailValidation` 只影响下次借出校验），**不阻止当前事务里的后续操作**。
- 所以 `commit()` 会直接打到 LBConnection → 1.0.0 下静默 no-op → 静默数据丢失。
- 升级 1.0.1 后 LBConnection 主动抛错，DBCP 再透传给应用，行为正确。
- **配置要求**：1.0.1 已修复 `isValid`，DBCP 默认 `testOnBorrow` 校验即可；若停留在 1.0.0，需配 `validationQuery = SELECT 1` 规避 `isValid` 恒 false（`validationQuery` 在 1.0.1 下可选）。

### 7.3 c3p0 —— 必须升级 1.0.1

- c3p0 在使用期**完全透传**（异常时只"实测 + 标记 + 发事件"，不 detach、不关连接，归还时才销毁）。
- fatal 后继续调 `commit()` 会打到 LBConnection → 1.0.0 下静默 no-op。
- 升级 1.0.1 后由 LBConnection 主动抛错。
- **配置要求**：1.0.1 已修复 `isValid`，默认 tester 可用；若停留在 1.0.0，c3p0 异常时/借出时走 `isValid` 遇恒 false 会把每条连接判坏，**必须**配 `preferredTestQuery = SELECT 1`（1.0.1 下可选）。

### 7.4 tomcat-jdbc —— 必须升级 1.0.1

- tomcat-jdbc **没有异常分类器**，使用期对异常只做 `InvocationTargetException` 解包后原样抛出，**纯透传**。
- fatal 后 `commit()` 直接打到 LBConnection → 1.0.0 下静默 no-op。
- 升级 1.0.1 后由 LBConnection 主动抛错。
- **配置要求**：1.0.1 已修复 `isValid`，开校验后可不配 `validationQuery`；若停留在 1.0.0，走 `isValid` 遇恒 false 会把每条连接判坏，**必须**配 `validationQuery = SELECT 1`。无论哪个版本，都**务必开 `testOnBorrow=true` 或 `testWhileIdle=true`**（tomcat-jdbc 默认两者皆关，既不分类也不校验，DB 重启后无法自愈）。

### 7.5 Druid —— 建议升级 1.0.1（1.0.0 可临时停留）

- Druid 自带的 `MySqlExceptionSorter` 与 lb-driver 的**高度一致**（同样覆盖 `08*`、MySQL 错误码、`CommunicationsException`）。**多数 fatal 场景两者会同时判 fatal**。
- 当 Druid 也判 fatal 时，它会 disable 连接 + discard 物理连接，**后续 `commit()` 会被 Druid 的 `checkState()` 直接挡掉**（抛 "connection disabled"），打不到 LBConnection → 1.0.0 的 no-op 不会被触发。
- 因此 Druid + 1.0.0 在"分类一致"的前提下相对安全，是**可以临时停留 1.0.0 的唯一一个池子**。
- 但仍**建议升级 1.0.1**，原因：
  1. **`close()` 资源泄漏**：1.0.0 的 `close()` 不健壮，`closeStatements()` 抛异常时会跳过注销/置位 → 逻辑连接泄漏。1.0.1 用 try/finally 修复。这个问题对**所有池子**都成立，不限于 Druid。
  2. **边缘不一致**：若所用 Druid 版本的 sorter 没覆盖 OB 专属 fatal（`-8000~-9000`、`NO ALIVE DATASOURCE` 等），会出现"lb-driver 判 fatal、Druid 不判"的不一致，此时 1.0.0 的 no-op 仍可能被触发。升级 1.0.1 后由 LBConnection 兜底抛错，消除该隐患。
- Druid 默认用 `PingConnectionChecker`（COM_PING），**不受 `isValid()` 恒 false 影响**。

---

## 8. 升级检查清单

无论用哪个池子，升级到 1.0.1 后建议核对：

1. **确认 lb-driver 版本**：依赖已是 1.0.1（`pom.xml`）。
2. **确认连接校验方式**（1.0.1 已修复 `isValid`，默认即可工作；1.0.0 必须配 validationQuery）：
   - **1.0.1**：可用默认的 `isValid` 校验；若想用指定 SQL，再配 `validationQuery` / `connectionTestQuery = SELECT 1`（可选）。
   - **1.0.0**：`isValid` 恒 false，**必须**配 `validationQuery = SELECT 1`（HikariCP 用 `connectionTestQuery`、c3p0 用 `preferredTestQuery`），否则池子会把每条连接都判坏。
   - Druid：默认 COM_PING，不受 `isValid` 影响。
3. **确认连接池开启了校验**（用于 DB 重启等场景自愈）：
   - HikariCP：默认借出校验，已满足。
   - DBCP：`testOnBorrow=true`（默认）或 `testWhileIdle=true`。
   - c3p0：`testConnectionOnCheckout` / `idleConnectionTestPeriod`。
   - tomcat-jdbc：**必须显式开** `testOnBorrow=true` 或 `testWhileIdle=true`（默认全关）。
   - Druid：`testWhileIdle` 默认 true，已满足。
4. **应用 / 事务管理器代码**：catch 到 fatal 后**不要**继续在原连接上操作；应放弃当前事务、归还连接、重新获取后重试整段事务。升级到 1.0.1 后，即便代码忘了这么做，LBConnection 也会用异常明确提示，而不再是静默 no-op。

---

## 9. 附：为什么"静默 no-op 的 commit"比"抛错"危险得多

fatal 发生时，lb-driver 已经把物理连接归还（标记 unhealthy），那条物理连接上的事务会随连接断开被 DB 回滚——**事务在 DB 侧已经注定失败**。

- **1.0.0**：`commit()` 静默成功 → 应用认为提交成功 → 实际数据没落库 → **静默数据丢失**，且极难排查（没有任何异常抛出）。
- **1.0.1**：`commit()` 抛 "No operations allowed after connection broken by fatal error" → 应用知道事务失败 → 触发重试或告警 → 行为正确、可观测。

这正是 1.0.1 修复的核心价值：**把"静默的失败"变成"明确的失败"。**
