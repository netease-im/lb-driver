
## Test Cases

| Test Case | Test Type | Expected Result | Test Result |
|:--|:--:|:--|:--:|
| Regular select/delete/update/insert | Functional | SQL executes correctly | Passed |
| Transactions with multiple SQL statements | Functional | SQL executes correctly and the transaction is committed correctly | Passed |
| Transactions with multiple SQL statements where an exception occurs during execution | Functional | The transaction is rolled back correctly, and the connection can be reused | Passed |
| sql-proxy returns an exception, simulated by renaming a table | Functional | The request reports an error, and the connection can be reused | Passed |
| sql-proxy scale-out/scale-in by operating etcd/nacos and similar systems | Functional | SQL executes correctly <br>After scale-out, connections are gradually balanced across all sql-proxy nodes <br>After scale-in, connections are gradually balanced across all remaining sql-proxy nodes | Passed |
| One sql-proxy node goes down and is brought back after 10 minutes | Exception | The application recovers after brief errors. The failed sql-proxy is marked unreachable. After it is brought back, it is marked reachable again, and traffic is rebalanced to that node | Passed |
| One sql-proxy node loses power | Exception | The application recovers after brief errors <br>When power is lost, the TCP FIN packet is not sent to the client, and lb-driver should not hang <br>The request must trigger the underlying TCP socketTimeout exception and correctly mark the sql-proxy as unreachable <br> | Passed, simulated by shutting down a cloud VM |
| Network latency of 1s or 20s is added to one sql-proxy NIC | Exception | If socketTimeout is set to 10s, a 1s delay slows requests, while a 20s delay causes request timeout. The timed-out node should be determined unreachable and automatically excluded | Passed |
| One sql-proxy process hangs, simulated by `kill -19` | Exception | The application recovers after brief errors because the node is determined unreachable | Passed |
| 100% packet loss when accessing one sql-proxy NIC | Exception | The application recovers after brief errors because the node is determined unreachable | Passed |
| All sql-proxy nodes go down and are brought back one by one after 5 minutes | Exception | The application fails after the outage, then recovers automatically after the nodes are brought back, with traffic balanced | Passed |
| One config-server node goes down | Exception | There are brief errors and then automatic recovery, but application traffic is not affected <br>Application restart is not affected | Passed |
| All config-server nodes go down | Exception | There are continuous errors, but application traffic is not affected <br>Application restart fails | Passed |
| One config-server node hangs, simulated by `kill -19` | Exception | There are brief errors and then automatic recovery, but application traffic is not affected <br>Application restart is not affected | Passed |
| 100% packet loss on one config-server node | Exception | There are brief errors and then automatic recovery, but application traffic is not affected <br>Application restart is not affected | Passed |
| One etcd node goes down | Exception | config-server has brief errors and then recovers automatically, but application traffic is not affected <br>Application restart is not affected <br>config-server restart is not affected | Passed |
| All etcd nodes go down | Exception | config-server has continuous errors, but application traffic is not affected <br>Application restart is not affected <br>config-server restart fails | Passed |
| One etcd node hangs, simulated by `kill -19` | Exception | config-server has brief errors and then recovers automatically, but application traffic is not affected <br>Application restart is not affected <br>config-server restart is not affected | Passed |
| 100% packet loss on one etcd node | Exception | config-server has brief errors and then recovers automatically, but application traffic is not affected <br>Application restart is not affected <br>config-server restart is not affected | Passed |
| Invalid etcd configuration with incorrect JSON format | Exception | config-server reports errors, but application traffic is not affected <br>Application restart is not affected <br>config-server restart fails | Passed |
| Invalid etcd configuration with correct JSON format and one incorrect node inside | Exception | config-server raises an alert, but application traffic is not affected <br>Application restart is not affected <br>config-server restart is not affected | Passed |
| Invalid etcd configuration with correct JSON format and all nodes inside incorrect | Exception | config-server raises an alert, but application traffic is not affected <br>Application restart fails <br>config-server restart succeeds, but it is actually unavailable | Passed |


### Notes

* lb-driver periodically checks whether the number of connections is balanced across sql-proxy nodes. If not, it attempts to rebalance them.
* lb-driver periodically checks whether sql-proxy is reachable by establishing a JDBC connection. If a node is unreachable, it is no longer assigned traffic.
* lb-driver periodically obtains the sql-proxy node list from config-server and compares it with the local configuration. After diffing, it adds nodes first and then removes nodes.
* When lb-driver adds a sql-proxy node, it checks whether the node is reachable. If the node is unreachable, it is added to the list but not assigned traffic. If the scheduled check later finds it reachable, it is assigned traffic.
* When lb-driver removes a sql-proxy node, it checks whether the remaining sql-proxy nodes are empty or whether no reachable nodes remain. If so, it skips the operation.
* When config-server obtains configuration from etcd/nacos, it validates the format. If validation fails, it skips the update. If validation succeeds, it updates the local configuration.
* config-server checks whether the sql-proxy nodes obtained from etcd/nacos are reachable by telnet. If not, it prints logs or raises alerts, but this does not affect delivery to lb-driver.
* The tests above are based on the druid connection pool. If the druid connection pool version is 1.2.21 or later, `LbdDruidFilter` must be configured.
