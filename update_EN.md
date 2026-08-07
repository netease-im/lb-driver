
# 1.0.1 (2026/08/xx)
### Fixed
* Fixed logical connections allowing subsequent operations after fatal SQL exceptions on the underlying physical connection
* Fixed connection close cleanup to always unregister logical connections and prevent unhealthy physical connections from returning to the pool
* Implemented `isValid()` (previously a stub that always returned false) so connection pools can validate logical connections correctly: closed or broken connections return false, idle connections return true, and held connections delegate to the underlying physical connection's `isValid()`
* Hardened the fatal-error path: mark the logical connection broken before releasing the physical connection to avoid a transient misjudgment by concurrent `isValid()`; made `realConnection` volatile; close-Statement exceptions no longer mask the original fatal exception
* `isClosed()` now returns true when the connection is closed or broken by a fatal error (previously a broken connection still returned false, inconsistent with its unusable state)

> See [doc/fatal_error.md](doc/fatal_error.md) for details on the fatal-error handling changes, compatibility with connection pools (Druid / HikariCP / DBCP / c3p0 / tomcat-jdbc), and version recommendations.

### Security
* Restricted `/health/online` and `/health/offline` to loopback addresses by default; set `-DconfigServerOnlineOfflineLoopbackOnly=false` to disable the restriction

# 1.0.0 (2026/01/12)
### Added
* Initial release
