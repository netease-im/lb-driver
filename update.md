
# 1.0.1（2026/08/xx）
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
