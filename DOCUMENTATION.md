# Akka HTTP 示例项目文档

本文档旨在详细解释当前Akka HTTP示例项目的架构和实现细节，特别是关于Actor模型的应用。

## 1. 整体架构

本应用是一个基于Akka HTTP的服务器，提供了一个简单的键值（key-value）缓存API。它利用Akka Actor来并发、安全地管理缓存状态，并实现了故障恢复功能。

主要组件如下：

- **`HttpServer`**: 应用的主入口。它负责启动Akka Actor系统和HTTP服务器。
- **`HttpRoutes`**: 定义了所有的HTTP路由和请求处理逻辑。它通过与Actor系统交互来执行缓存操作。
- **`SupervisorActor`**: Actor系统中的根Actor。它创建并监督`CacheActor`，其核心职责是处理`CacheActor`可能出现的故障。
- **`CacheActor`**: `SupervisorActor`的子Actor。它内部持有一个`HashMap`作为内存缓存，并负责处理所有缓存操作（如读取、写入）。

## 2. Actor层级与监督机制

本应用的Actor层级非常清晰，共分为两层：

```
         HttpServer (ActorSystem)
               |
        /supervisor (SupervisorActor)
               |
        /cache-actor (CacheActor)
```

- **`SupervisorActor`**: 这是由`ActorSystem`创建的顶级Actor。顾名思义，它是一个“监督者”。我们为它配置了`restart`（重启）监督策略。这意味着，如果`CacheActor`因任何原因（例如，抛出异常）而失败，`SupervisorActor`会自动重启一个新的`CacheActor`实例来取代它。这是Akka提供的核心故障恢复能力。

    *代码来源: `SupervisorActor.java`*
    ```java
    public static Behavior<Command> create() {
        return Behaviors.supervise(
            Behaviors.setup(SupervisorActor::new)
        ).onFailure(
            SupervisorStrategy.restart()
        );
    }
    ```

- **`CacheActor`**: 这个Actor由`SupervisorActor`创建并管理。它是一个有状态的Actor，负责维护缓存数据。由于它被监督，一旦发生故障并被重启，它的内部状态（`HashMap`）将被重置为初始的空状态。

    *代码来源: `SupervisorActor.java`*
    ```java
    private SupervisorActor(ActorContext<Command> context) {
        super(context);
        this.context = context;
        // 创建CacheActor子Actor
        this.cacheActor = context.spawn(CacheActor.create(), "cache-actor");
    }
    ```

## 3. 消息驱动的通信

与Actor的所有交互都严格通过异步消息传递完成。每个Actor都定义了它能理解和处理的一组消息（即它的“协议”）。

### `CacheActor` 的消息协议

- **`Get(String key, ActorRef<Value> replyTo)`**: 一条用于从缓存中获取值的消息。它包含一个`replyTo`引用，以便将结果发送回去。
- **`Put(String key, String value)`**: 一条用于在缓存中存入键值对的消息。这是一个“发后即忘”类型的消息。
- **`SimulateFailure()`**: 一条用于在Actor内部手动触发异常的消息，目的是为了演示监督者的重启策略。
- **`Value(String value)`**: `Get`请求的响应消息，包含了从缓存中获取到的值。

### `SupervisorActor` 的消息协议

- **`ForwardToCache(CacheActor.Command cacheCommand)`**: 将`CacheActor`的命令进行转发的消息。这层封装使得`HttpRoutes`无需直接与`CacheActor`耦合。
- **`GetCacheActorRef(ActorRef<ActorRef<CacheActor.Command>> replyTo)`**: 一条用于获取`CacheActor`的`ActorRef`（Actor引用）的消息。`HttpRoutes`使用它来与`CacheActor`进行交互。

## 4. HTTP请求流程与Actor交互

让我们来追踪一个HTTP请求是如何与Actor系统进行交互的。

### 示例: `GET /cache/{key}`

1.  HTTP请求到达Akka HTTP服务器，并被`HttpRoutes`中定义的路由规则匹配。
2.  `path`指令从URL中提取出`key`。
3.  `onSuccess`指令调用`askCacheActorForValue(key)`方法。所有与Actor的交互都在这个方法中发生。
4.  **`ask`模式**: `askCacheActorForValue`方法使用`AskPattern.ask`来与Actor通信。`ask`模式是一种向Actor发送消息并获得一个`CompletionStage`（可以理解为Future）作为响应的便捷方式。

    a.  **第一次`ask` (发往`SupervisorActor`)**: 首先，它向`supervisor` Actor发送一条`GetCacheActorRef`消息，目的是为了获取`cacheActor`的引用。这样做是因为`HttpRoutes`类本身并没有`cacheActor`的直接引用。

        *代码来源: `HttpRoutes.java`*
        ```java
        CompletionStage<ActorRef<CacheActor.Command>> cacheActorFuture = AskPattern.ask(
            supervisor,
            replyTo -> new SupervisorActor.GetCacheActorRef(replyTo),
            timeout,
            system.scheduler()
        );
        ```

    b.  **第二次`ask` (发往`CacheActor`)**: 当`cacheActorFuture`完成时，我们便得到了`cacheActor`的`ActorRef`。然后，我们使用`thenCompose`来链接另一个`ask`调用，这次是向`cacheActor`本身发送消息。我们发送一条`Get`消息，其中包含了用于回复的`ActorRef`。

        *代码来源: `HttpRoutes.java`*
        ```java
        return cacheActorFuture.thenCompose(cacheActor ->
            AskPattern.<CacheActor.Command, CacheActor.Value>ask(
                cacheActor,
                replyTo -> new CacheActor.Get(key, replyTo),
                timeout,
                system.scheduler()
            )
        ).thenApply(cacheValue -> cacheValue.value);
        ```
        请注意，这里我们使用了`AskPattern.<CacheActor.Command, CacheActor.Value>ask`来显式地为`ask`方法提供泛型参数，这有助于Java编译器进行正确的类型推断。

5.  `CacheActor`接收到`Get`消息，从其内部的`HashMap`中查找值，然后将结果包装在一个`Value`消息中，发送给`Get`消息中指定的`replyTo` Actor。
6.  `ask`模式返回的`CompletionStage`因收到`Value`消息而完成。
7.  `thenApply(cacheValue -> cacheValue.value)`这部分代码从`Value`对象中提取出最终的`String`类型的值。
8.  最后，路由中的`onSuccess`指令接收到这个`String`值，并用它来完成HTTP请求——返回一个`200 OK`响应，响应体即为获取到的值。如果值为`null`，则返回`404 Not Found`。

整个流程是完全无阻塞的。`ask`模式和`CompletionStage`使得服务器在等待Actor处理消息的同时，可以继续接收和处理其他请求，大大提高了系统的吞吐量和响应能力。
