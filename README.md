# Akka HTTP 示例项目

这是一个基于 Akka HTTP 和 Akka Actor 的示例项目，演示了如何构建一个具有缓存功能的 HTTP 服务。

## 项目概述

本项目展示了如何使用 Akka 技术栈构建一个简单的 HTTP 服务，包含以下特性：

- 基于 Akka HTTP 的 REST API
- 使用 Akka Actor 模型处理并发请求
- 实现了本地缓存和 Redis 缓存的两级缓存机制
- 包含错误处理和 Actor 监督策略
- 提供了性能基准测试

## 技术栈

- Java 11
- Akka 2.6.19
- Akka HTTP 10.2.9
- Redis (通过 Jedis 客户端)
- Maven 构建工具
- JUnit 4 测试框架
- JMH 性能测试框架

## 项目结构

```
src/
├── main/
│   ├── java/com/example/
│   │   ├── model/              # 数据模型
│   │   ├── routing/            # HTTP 路由定义
│   │   ├── CacheActor.java     # 缓存 Actor 实现
│   │   ├── RedisActor.java     # Redis Actor 实现
│   │   ├── SupervisorActor.java # 监督 Actor
│   │   ├── HttpServer.java     # HTTP 服务器入口
│   │   └── SimpleHttpRoutes.java # 路由组合
│   └── resources/
└── test/
    └── java/com/example/       # 单元测试
```

## API 接口

### Hello 接口
- `GET /hello` - 返回 "Hello World from Akka HTTP!"

### 缓存接口
- `GET /cache/{key}` - 获取指定键的缓存值
- `PUT /cache/{key}/{value}` - 设置指定键的缓存值
- `POST /cache/failure` - 模拟缓存故障

### POST 接口
- `POST /post/{param}` - 接收 JSON 数据并记录日志

## 快速开始

### 环境要求

- Java 11 或更高版本
- Maven 3.6 或更高版本
- Redis 服务器 (可选，用于缓存功能)

### 构建项目

```bash
mvn compile
```

### 运行测试

```bash
mvn test
```

### 运行服务器

```bash
mvn exec:java
```

服务器将在 `http://localhost:8080` 启动。

### 性能测试

```bash
mvn exec:java@default-cli -Dexec.mainClass="com.example.CacheBenchmark"
```

## 使用示例

启动服务器后，可以使用以下命令测试 API：

```bash
# 测试 Hello 接口
curl http://localhost:8080/hello

# 设置缓存
curl -X PUT http://localhost:8080/cache/mykey/myvalue

# 获取缓存
curl http://localhost:8080/cache/mykey

# 测试 POST 接口
curl -X POST http://localhost:8080/post/testparam \
  -H "Content-Type: application/json" \
  -d '{"field1":"value1","field2":123}'
```

## 架构说明

项目采用 Actor 模型进行设计：

1. `SupervisorActor` - 根监督 Actor，管理其他 Actor
2. `CacheActor` - 本地缓存 Actor，提供两级缓存机制
3. `RedisActor` - Redis 连接 Actor，处理与 Redis 的交互
4. 各种路由定义类处理 HTTP 请求

## 许可证

本项目仅供学习和参考使用。