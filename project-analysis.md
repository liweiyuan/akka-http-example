# Akka HTTP 缓存系统项目分析

## 项目概述

这是一个基于Akka HTTP和Akka Actor的缓存系统示例项目，实现了两级缓存架构（L1+L2）。

## 核心组件

### HTTP层

1. **HttpServer**：应用入口，启动Actor系统和HTTP服务器
2. **HttpRoutes**：HTTP路由定义，使用RouteRegistry组织路由
3. **RouteRegistry**：路由注册中心，统一管理所有路由定义
4. 路由定义：
   - HelloRouteDefinition：简单的Hello World示例
   - CacheRouteDefinition：缓存操作路由（GET/PUT）
   - PostRouteDefinition：POST请求处理示例

### Actor层（缓存系统核心）

1. **SupervisorActor**：监督Actor，负责创建和监督CacheActor和RedisActor
2. **CacheActor**：一级缓存（L1），使用内存HashMap存储数据
3. **RedisActor**：二级缓存（L2），使用Redis作为持久化存储

## 缓存架构特点

这是一个两级缓存系统，采用了Cache-Aside（旁路缓存）策略：

### 读取策略

- 优先从L1缓存（CacheActor的HashMap）读取
- L1未命中时查询L2缓存（Redis）
- L2命中则将数据回填到L1缓存

### 写入策略

- 同时写入L1和L2缓存，保证数据一致性

### 容错机制

- 使用Akka的监督策略处理故障
- RedisActor故障时会自动重启
- 提供故障模拟功能用于测试

## 消息传递机制

系统完全基于消息传递：
- HTTP请求通过Ask模式与Actor系统交互
- Actor之间通过Tell模式异步通信
- 使用消息适配器处理异步响应

## 技术栈

- Akka HTTP：Web服务器框架
- Akka Actor：并发模型和状态管理
- Jedis：Redis客户端
- Maven：项目构建工具

该项目展示了如何使用Akka构建一个具有容错能力的分布式缓存系统，是一个很好的Akka实践示例。