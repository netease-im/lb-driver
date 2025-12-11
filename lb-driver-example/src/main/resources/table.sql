
CREATE TABLE `test_account` (
  `uid` bigint(20) NOT NULL primary key auto_increment comment '自增字段',
  `account` varchar(32) NOT NULL comment '账号名称',
  `validflag` tinyint(4) DEFAULT NULL comment '是否有效',
  `create_time` bigint(20) DEFAULT NULL comment '创建时间',
  `update_time` bigint(20) DEFAULT NULL comment '更新时间',
  unique key `account`
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账号表';

CREATE TABLE `test_uinfo` (
  `uid` bigint(20) NOT NULL primary key comment '自增字段',
  `nick` varchar(32) NOT NULL comment '昵称',
  `email` varchar(128) NOT NULL comment '邮箱',
  `ext` varchar(1024) NOT NULL comment '扩展字段',
  `create_time` bigint(20) DEFAULT NULL comment '创建时间',
  `update_time` bigint(20) DEFAULT NULL comment '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账号信息表';