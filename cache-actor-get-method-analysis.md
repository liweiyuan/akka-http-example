# CacheActor 中 onGet 方法的 Get 参数构建与调用分析

## 概述

本文详细分析了 Akka HTTP 缓存系统中 [CacheActor](file:///home/wade/Workspace/Java/fond/akka-example/akka-http-example/src/main/java/com/example/CacheActor.java#L12-L126) 类的 [onGet](file:///home/wade/Workspace/Java/fond/akka-example/akka-http-example/src/main/java/com/example/CacheActor.java#L84-L95) 方法中 [Get](file:///home/wade/Workspace/Java/fond/akka-example/akka-http-example/src/main/java/com/example/CacheActor.java#L18-L26) 参数的构建和调用过程。

## Get 参数的定义

[Get](file:///home/wade/Workspace/Java/fond/akka-example/akka-http-example/src/main/java/com/example/CacheActor.java#L18-L26) 类是 [CacheActor](file:///home/wade/Workspace/Java/fond/akka-example/akka-http-example/src/main/java/com/example/CacheActor.java#L12-L126) 内部定义的一个静态类，用于封装获取缓存值的请求：

```java
// 获取缓存值的命令
public static class Get implements Command {
    public final String key;
    public final ActorRef<Value> replyTo;
    
    public Get(String key, ActorRef<Value> replyTo) {
        this.key = key;
        this.replyTo = replyTo;
    }
}
```

其中：
- `key`: 要获取的缓存键
- `replyTo`: 回复 Actor 的引用，用于返回查询结果

## Get 参数的构建过程

[Get](file:///home/wade/Workspace/Java/fond/akka-example/akka-http-example/src/main/java/com/example/CacheActor.java#L18-L26) 参数的构建发生在 [CacheRouteDefinition.java](file:///home/wade/Workspace/Java/fond/akka-example/akka-http-example/src/main/java/com/example/routing/CacheRouteDefinition.java#L15-L86) 文件中的 [askCacheActorForValue](file:///home/wade/Workspace/Java/fond/akka-example/akka-http-example/src/main/java/com/example/routing/CacheRouteDefinition.java#L75-L85) 方法中：

```java
private CompletionStage<String> askCacheActorForValue(String key) {
    // ... 第一次ask获取cacheActor引用 ...
    
    return cacheActorFuture.thenCompose(cacheActor ->
            AskPattern.<CacheActor.Command, CacheActor.Value>ask(
                    cacheActor,
                    replyTo -> new CacheActor.Get(key, replyTo),  // <-- 这里构建Get参数
                    timeout,
                    system.scheduler()
            )
    ).thenApply(cacheValue -> cacheValue.value);
}
```

这里使用了 `AskPattern.ask` 方法，其中第二个参数是一个函数：`replyTo -> new CacheActor.Get(key, replyTo)`。这个函数的参数 [replyTo](file:///home/wade/Workspace/Java/fond/akka-example/akka-http-example/src/main/java/com/example/CacheActor.java#L20-L20) 是 Akka 自动生成的一个临时 Actor 引用，用于接收响应。

## Get 参数的调用过程

完整的调用链如下：

1. **HTTP 请求到达**：
   - 用户发起一个 `GET /cache/{key}` 请求
   - 被 [CacheRouteDefinition](file:///home/wade/Workspace/Java/fond/akka-example/akka-http-example/src/main/java/com/example/routing/CacheRouteDefinition.java#L15-L86) 中的路由规则捕获

2. **调用 askCacheActorForValue 方法**：
   - 路由处理器调用 [askCacheActorForValue(key)](file:///home/wade/Workspace/Java/fond/akka-example/akka-http-example/src/main/java/com/example/routing/CacheRouteDefinition.java#L75-L85) 方法

3. **两次 Ask 模式交互**：
   - 第一次 Ask：向 [SupervisorActor](file:///home/wade/Workspace/Java/fond/akka-example/akka-http-example/src/main/java/com/example/SupervisorActor.java#L12-L74) 请求获取 [CacheActor](file:///home/wade/Workspace/Java/fond/akka-example/akka-http-example/src/main/java/com/example/CacheActor.java#L12-L126) 的引用
   - 第二次 Ask：使用获取到的 [CacheActor](file:///home/wade/Workspace/Java/fond/akka-example/akka-http-example/src/main/java/com/example/CacheActor.java#L12-L126) 引用，向其发送 [Get](file:///home/wade/Workspace/Java/fond/akka-example/akka-http-example/src/main/java/com/example/CacheActor.java#L18-L26) 消息

4. **CacheActor 接收并处理 Get 消息**：
   - [CacheActor](file:///home/wade/Workspace/Java/fond/akka-example/akka-http-example/src/main/java/com/example/CacheActor.java#L12-L126) 的 `createReceive()` 方法定义了如何处理不同类型的消息
   - 当接收到 [Get](file:///home/wade/Workspace/Java/fond/akka-example/akka-http-example/src/main/java/com/example/CacheActor.java#L18-L26) 消息时，会调用 [onGet](file:///home/wade/Workspace/Java/fond/akka-example/akka-http-example/src/main/java/com/example/CacheActor.java#L84-L95) 方法

## onGet 方法处理流程

```java
private Behavior<Command> onGet(Get command) {
    // 1. 先在本地缓存中查找
    String value = cache.get(command.key);
    if (value != null) {
        // 2a. 如果找到，直接通过replyTo返回结果
        command.replyTo.tell(new Value(value));
        return this;
    } else {
        // 2b. 如果未找到，创建消息适配器并向RedisActor查询
        ActorRef<Value> responseAdapter = context.messageAdapter(Value.class,
                valueFromRedis -> new RedisResponse(valueFromRedis, command.key, command.replyTo));
        redisActor.tell(new Get(command.key, responseAdapter));
        return this;
    }
}
```

当 [CacheActor](file:///home/wade/Workspace/Java/fond/akka-example/akka-http-example/src/main/java/com/example/CacheActor.java#L12-L126) 收到 [Get](file:///home/wade/Workspace/Java/fond/akka-example/akka-http-example/src/main/java/com/example/CacheActor.java#L18-L26) 消息后，会执行以下操作：
1. 首先在本地 HashMap 缓存中查找指定的 key
2. 如果找到，则直接通过 [replyTo](file:///home/wade/Workspace/Java/fond/akka-example/akka-http-example/src/main/java/com/example/CacheActor.java#L20-L20) 引用将结果发送回调用方
3. 如果未找到，则创建一个消息适配器，并向 [RedisActor](file:///home/wade/Workspace/Java/fond/akka-example/akka-http-example/src/main/java/com/example/RedisActor.java#L11-L75) 发送一个新的 [Get](file:///home/wade/Workspace/Java/fond/akka-example/akka-http-example/src/main/java/com/example/CacheActor.java#L18-L26) 请求

整个过程是完全异步的，体现了 Actor 模型的消息驱动特性。通过使用 `AskPattern`，可以将基于回调的异步操作转换为更易处理的 `CompletionStage` 形式。