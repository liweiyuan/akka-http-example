# Akka HTTP 项目性能基准测试

## 概述

本文档介绍了如何为 Akka HTTP 项目添加性能基准测试，使用 JMH (Java Microbenchmark Harness) 进行性能测试。

## 添加的依赖

在 pom.xml 中添加了 JMH 相关依赖：

```xml
<!-- JMH 性能测试依赖 -->
<dependency>
    <groupId>org.openjdk.jmh</groupId>
    <artifactId>jmh-core</artifactId>
    <version>1.36</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.openjdk.jmh</groupId>
    <artifactId>jmh-generator-annprocess</artifactId>
    <version>1.36</version>
    <scope>test</scope>
</dependency>
```

## 创建的测试类

创建了 [CacheBenchmark.java](file:///home/wade/Workspace/Java/fond/akka-example/akka-http-example/src/test/java/com/example/CacheBenchmark.java) 测试类，包含以下测试方法：

1. `testHelloEndpoint()` - 测试 /hello 端点
2. `testCachePutEndpoint()` - 测试缓存 PUT 操作
3. `testCacheGetEndpoint()` - 测试缓存 GET 操作

## 运行基准测试

可以使用以下命令运行基准测试：

```bash
mvn clean compile test-compile exec:java -Dexec.mainClass="com.example.CacheBenchmark"
```

## 测试配置

测试类使用了以下 JMH 注解进行配置：

- `@State(Scope.Benchmark)` - 基准测试状态
- `@BenchmarkMode(Mode.Throughput)` - 吞吐量测试模式
- `@OutputTimeUnit(TimeUnit.SECONDS)` - 输出时间单位为秒
- `@Warmup(iterations = 2, time = 5)` - 预热 2 次，每次 5 秒
- `@Measurement(iterations = 3, time = 10)` - 测量 3 次，每次 10 秒
- `@Threads(4)` - 使用 4 个线程

## 测试结果说明

基准测试将输出每个端点的吞吐量（每秒操作数），可以用于评估系统性能和进行优化。

## 注意事项

1. 测试过程中会自动启动和停止 HTTP 服务器
2. 每个测试方法都是独立的，互不影响
3. 测试结果会显示操作的平均吞吐量