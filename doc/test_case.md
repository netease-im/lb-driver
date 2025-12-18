
## 测试用例

|                  用例                   | 测试类型 |                                                          预期结果                                                          | 测试结果 |
|:-------------------------------------:|:----:|:----------------------------------------------------------------------------------------------------------------------:|:----:|
|     常规select/delete/update/insert     |  功能  |                                                        sql正确执行                                                         |      | 
|               多条sql的事务                |  功能  |                                                     sql正确执行，事务正确提交                                                     |      |
|          多条sql的事务，执行过程中出现异常           |  功能  |                                                    事务正确回滚，且连接可以被复用                                                     |      |
| sql-proxy返回异常，如ip白名单移除后导致的access-deny |  功能  |                                                     请求报错，且连接可以被复用                                                      |      |
|      sql-proxy扩缩容（操作etcd/nacos等）      |  功能  |                            sql正确执行 <br>扩容后，连接逐步均衡到所有sql-proxy <br>缩容后，连接逐步均衡到剩余所有sql-proxy                             |      |
|         sql-proxy宕机1台，10分钟后拉起         |  异常  |                               业务短暂报错后恢复正常，故障sql-proxy被标记为不可达，重新拉起后，重新被标记为可达，且流量重新均衡到该节点                                |      |
|             sql-proxy断电1台             |  异常  | 短暂报错后自行恢复 <br> 断电情况下，tcp的fin包不会发到客户端，此时lb-driver不应该被hang住 <br> 请求需要触发底层tcp的sockettimeout异常，并且能被正确的标记为sql-proxy不可达 <br> |      |
|         某台sql-proxy网卡延时1s、20s         |  异常  |                          设置socketTimeout为10s，则延时1s的会导致请求变慢，延时20s的会请求超时，请求超时的节点应该被判定为不可达而自动排除掉                          |      |
|     某台sql-proxy进程hang住（kill -19）      |  异常  |                                                业务短暂报错后恢复正常，因为该节点被判定为不可达                                                |      |
|         访问某台sql-proxy网卡丢包100%         |  异常  |                                                业务短暂报错后恢复正常，因为该节点被判定为不可达                                                |      |
|       所有sql-proxy节点宕机，5分钟后逐台拉起        |  异常  |                                                宕机后业务异常，重新拉起后业务自行恢复且流量均衡                                                |      |
|           config-server宕机1台           |  异常  |                                           有短暂报错，随后自行恢复，但是业务流量无影响 <br>业务重启无影响                                           |      |
|           config-server宕机全部           |  异常  |                                              有持续报错，但是业务流量无影响 <br>业务重启会失败                                               |      |
|   config-server一个节点hang住（kill -19）    |  异常  |                                           有短暂报错，随后自行恢复，但是业务流量无影响 <br>业务重启无影响                                           |      |
|        config-server一个节点丢包100%        |  异常  |                                           有短暂报错，随后自行恢复，但是业务流量无影响 <br>业务重启无影响                                           |      |
|               etcd宕机1台                |  异常  |                         config-server有短暂报错，随后自行恢复，但是业务流量无影响 <br>业务重启无影响 <br>config-server重启无影响                         |      |
|               etcd宕机全部                |  异常  |                             config-server有持续报错，但是业务流量无影响 <br>业务重启无影响 <br>config-server重启失败                             |      |
|        etcd一个节点hang住（kill -19）        |  异常  |                         config-server有短暂报错，随后自行恢复，但是业务流量无影响 <br>业务重启无影响 <br>config-server重启无影响                         |      |
|            etcd一个节点丢包100%             |  异常  |                         config-server有短暂报错，随后自行恢复，但是业务流量无影响 <br>业务重启无影响 <br>config-server重启无影响                         |      |
|           etcd错误配置，json格式错误           |  异常  |                              config-server报错，但是业务流量无影响 <br>业务重启无影响 <br>config-server重启失败                               |      |  
|     etcd错误配置，json格式正确，里面的节点有一个错误      |  异常  |                              config-server报警，但是业务流量无影响 <br>业务重启无影响 <br>config-server重启无影响                              |      |
|      etcd错误配置，json格式正确，里面的节点全部错误      |  异常  |                           config-server报警，但是业务流量无影响 <br>业务重启失败 <br>config-server重启成功，但是实际不可用                           |      |


### 备注

* lb-driver定时检查连接数是否在各个sql-proxy中平衡，如果不平衡，则会尝试rebalance
* lb-driver定时检查sql-proxy是否可达（通过建立一个jdbc连接来判断），如果不可达，则不再分配
* lb-driver定时从config-server获取sql-proxy节点列表，和本地配置进行比对，diff后，先增加，后减少
* lb-driver新增sql-proxy时，会判断是否可达，如果不可达，则虽然加入列表但是不对外分配，在定时检查任务中如果发现可达，则会对外分配
* lb-driver减少sql-proxy时，会判断剩余sql-proxy是否为空或者已无可达节点，如果是，则跳过本次操作
* config-server从etcd/nacos获取配置时，会校验格式，如果校验失败，则跳过，如果校验成功，则更新本地配置
* config-server会校验etcd/nacos获取到的sql-proxy节点是否telnet能通，如果不能通，则会打印日志/报警，但是不影响下发lb-driver
