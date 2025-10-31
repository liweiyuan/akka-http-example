### Akka 缓存系统架构文档

本文档旨在阐明当前 Akka 缓存系统的内部工作流程、消息传递机制以及其设计思想。

#### 1. 概览

本系统实现了一个两级缓存（L1/L2 Cache）。`CacheActor` 作为一级缓存（L1），使用内存（`HashMap`）进行快速存取。`RedisActor` 作为二级缓存（L2），使用外部 Redis 服务进行持久化和分布式共享。

读取操作的策略是 **Cache-Aside（旁路缓存）**，并且是 **本地优先**：

- **读取**：优先读 L1，如果 L1 未命中，则读 L2。如果 L2 命中，则将数据填充回 L1。
- **写入**：同时写入 L1 和 L2，以保证两者的数据一致性。

`SupervisorActor` 充当监管者，负责创建和管理这两个 Actor，并确保在 `RedisActor` 发生故障时能够自动重启恢复。

#### 2. 角色与职责

-   **`SupervisorActor`**:
    -   **创建者**：创建 `CacheActor` 和 `RedisActor`。
    -   **监管者**：为 `RedisActor` 配置了 `restart` 监管策略。当 `RedisActor` 因异常（如 Redis 连接失败）而崩溃时，`SupervisorActor` 会自动创建一个新的 `RedisActor` 实例来替代它，保证了系统的弹性。
    -   **路由器**：作为外部请求的入口，将命令转发给 `CacheActor`。

-   **`CacheActor`**:
    -   **一级缓存 (L1)**：持有一个 `HashMap` 作为内存缓存，提供最快速的访问。
    -   **核心逻辑协调者**：处理所有缓存的读写逻辑。它决定何时从本地缓存读取，何时应查询 `RedisActor`。
    -   **请求入口**：是缓存系统的主要交互对象。

-   **`RedisActor`**:
    -   **二级缓存 (L2)**：封装了所有与 Redis 的直接交互（使用 Jedis 客户端）。
    -   **故障感知**：当无法连接或操作 Redis 时，它会抛出异常，从而触发 `SupervisorActor` 的监管策略。
    -   **资源管理**：通过处理 `PreRestart` 信号，确保在 Actor 重启前能安全地关闭旧的 Jedis 连接。

#### 3. 消息传递详解

这是整个系统最核心的部分。理解消息的流转是理解本设计的关键。

##### **Get (读取) 请求流程**

当一个 `Get` 请求到达时，会发生以下情况：

1.  **请求到达**：外部世界向 `SupervisorActor` 发送一个 `ForwardToCache(new Get(key, replyTo))` 消息。
2.  **转发**：`SupervisorActor` 将 `Get` 命令转发给 `CacheActor`。
3.  **L1 缓存检查**：`CacheActor` 在其内部的 `HashMap` 中查找 `key`。
    -   **情况 A：L1 命中 (Hit)**
        -   `CacheActor` 找到了值。
        -   它直接通过 `replyTo.tell(new Value(value))` 将结果返回给原始请求者。
        -   **流程结束。**
    -   **情况 B：L1 未命中 (Miss)**
        -   `CacheActor` 在本地 `HashMap` 中没有找到值。
        -   **创建消息适配器 (Message Adapter)**：这是实现异步回调的关键。`CacheActor` 创建一个临时的 `ActorRef`，这个 `ActorRef` 的作用是：当它收到一个 `Value` 消息时，会将其包装成一个 `RedisResponse` 命令，然后发送给 `CacheActor` 自己。
        -   **请求 L2**：`CacheActor` 向 `RedisActor` 发送一个 `Get` 消息，但关键在于，它将刚刚创建的 **消息适配器** 作为 `replyTo` 的目标。
4.  **Redis 处理**：`RedisActor` 收到 `Get` 请求，查询 Redis 数据库，然后将查询结果（一个 `Value` 对象，可能包含 `null`）发送给它收到的 `replyTo` 目标，也就是那个 **消息适配器**。
5.  **适配器工作**：消息适配器被触发，它将收到的 `Value` 包装成 `new RedisResponse(value, key, originalReplyTo)` 命令，并发送给 `CacheActor`。
6.  **处理 L2 结果**：`CacheActor` 收到 `RedisResponse` 命令，进入 `onRedisResponse` 方法。
    -   **情况 B.1：L2 命中**
        -   `RedisResponse` 中包含一个非 `null` 的值。
        -   `CacheActor` 将这个值存入自己的 `HashMap` 中（**填充 L1 缓存**）。
        -   它将这个值通过 `originalReplyTo.tell(...)` 返回给原始请求者。
        -   **流程结束。**
    -   **情况 B.2：L2 未命中**
        -   `RedisResponse` 中包含 `null`。
        -   `CacheActor` 知道两级缓存都没有数据，于是将一个包含 `null` 的 `Value` 返回给原始请求者。
        -   **流程结束。**

##### **Put (写入) 请求流程**

写入流程相对简单直接：

1.  **请求到达与转发**：与 `Get` 类似，`Put` 命令最终到达 `CacheActor`。
2.  **双写 (Dual Write)**：`CacheActor` 执行两个操作：
    a.  将键值对存入自己的本地 `HashMap`。
    b.  将同一个 `Put` 命令转发给 `RedisActor`。
3.  **Redis 更新**：`RedisActor` 收到 `Put` 命令后，将数据写入 Redis。

这是一个 "fire-and-forget"（发射后不管）的操作，`CacheActor` 不会等待 `RedisActor` 的写入确认。

#### 4. 潜在问题与改进方向

当前的设计功能完善且可靠，但从生产级标准来看，还有一些可以优化的地方：

1.  **`RedisActor` 中的阻塞操作**：
    -   **问题**：Jedis 是一个阻塞式客户端。如果 Redis 响应缓慢，`RedisActor` 会被阻塞，无法处理其他消息，可能影响整个系统的吞吐量。
    -   **改进**：
        -   **简单方案**：为 `RedisActor` 配置一个专用的、独立的 "pinned-dispatcher"，使其在自己独立的线程上运行，避免影响其他 Actor。
        -   **高级方案**：将 Jedis 替换为一个异步的 Redis 客户端，如 **Lettuce**。这需要对 `RedisActor` 进行重构以处理异步 `Future` 或 `CompletionStage`。

2.  **硬编码的 Redis 地址**：
    -   **问题**：Redis 的主机和端口 (`localhost:6379`) 直接写在 `RedisActor` 的代码里。
    -   **改进**：应该将这些配置移到外部的 `.conf` 文件中（如 `application.conf`），并通过 `ActorSystem` 的设置来加载，以实现更好的灵活性。

3.  **本地缓存没有淘汰策略**：
    -   **问题**：`CacheActor` 中的 `HashMap` 会无限增长，如果键的数量非常大，最终可能导致 `OutOfMemoryError`。
    -   **改进**：使用一个有容量限制和淘汰策略（如 LRU, LFU）的缓存库来代替 `HashMap`。**Caffeine** 是 Akka 社区中非常流行和推荐的选择。
