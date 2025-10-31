package com.example;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CacheActorTest {

    private static final ActorTestKit testKit = ActorTestKit.create();

    @BeforeClass
    public static void setup() {
    }

    @AfterClass
    public static void teardown() {
        testKit.shutdownTestKit();
    }

    @Test
    public void testPutAndGet() {
        // 创建一个模拟的 RedisActor 来处理消息
        TestProbe<CacheActor.Command> redisActorProbe = testKit.createTestProbe();
        ActorRef<CacheActor.Command> redisActor = redisActorProbe.getRef();
        
        // 创建 CacheActor
        ActorRef<CacheActor.Command> cacheActor = testKit.spawn(CacheActor.create(redisActor));

        // 创建测试探针来接收回复
        TestProbe<CacheActor.Value> replyProbe = testKit.createTestProbe();

        // 测试 Put 操作
        cacheActor.tell(new CacheActor.Put("key1", "value1"));
        // 验证 Put 操作被转发给了 RedisActor
        redisActorProbe.expectMessageClass(CacheActor.Put.class);

        // 测试 Get 操作（应该直接从本地缓存获取）
        cacheActor.tell(new CacheActor.Get("key1", replyProbe.getRef()));
        CacheActor.Value response = replyProbe.receiveMessage();
        assertEquals("value1", response.value);

        // 验证没有额外向 RedisActor 发送消息
        redisActorProbe.expectNoMessage();
    }

    @Test
    public void testGetNonExistentKey() {
        // 创建一个模拟的 RedisActor 来处理消息
        TestProbe<CacheActor.Command> redisActorProbe = testKit.createTestProbe();
        ActorRef<CacheActor.Command> redisActor = redisActorProbe.getRef();

        // 创建 CacheActor
        ActorRef<CacheActor.Command> cacheActor = testKit.spawn(CacheActor.create(redisActor));

        // 创建测试探针来接收回复
        TestProbe<CacheActor.Value> replyProbe = testKit.createTestProbe();

        // 测试获取不存在的键（应该查询 RedisActor）
        cacheActor.tell(new CacheActor.Get("nonexistent", replyProbe.getRef()));
        
        // 验证向 RedisActor 发送了 Get 消息
        CacheActor.Get redisGetMsg = redisActorProbe.expectMessageClass(CacheActor.Get.class);
        assertEquals("nonexistent", redisGetMsg.key);
        
        // 模拟 RedisActor 返回空值
        redisGetMsg.replyTo.tell(new CacheActor.Value(null));
        
        // 验证 CacheActor 返回了正确的响应
        CacheActor.Value response = replyProbe.receiveMessage();
        assertNull(response.value);
    }

    @Test
    public void testCacheUpdatedFromRedis() {
        // 创建一个模拟的 RedisActor 来处理消息
        TestProbe<CacheActor.Command> redisActorProbe = testKit.createTestProbe();
        ActorRef<CacheActor.Command> redisActor = redisActorProbe.getRef();

        // 创建 CacheActor
        ActorRef<CacheActor.Command> cacheActor = testKit.spawn(CacheActor.create(redisActor));

        // 创建测试探针来接收回复
        TestProbe<CacheActor.Value> replyProbe = testKit.createTestProbe();

        // 测试获取不存在的键（应该查询 RedisActor）
        cacheActor.tell(new CacheActor.Get("redisKey", replyProbe.getRef()));

        // 验证向 RedisActor 发送了 Get 消息
        CacheActor.Get redisGetMsg = redisActorProbe.expectMessageClass(CacheActor.Get.class);
        assertEquals("redisKey", redisGetMsg.key);

        // 模拟 RedisActor 返回一个值
        redisGetMsg.replyTo.tell(new CacheActor.Value("redisValue"));

        // 验证 CacheActor 返回了正确的响应
        CacheActor.Value response = replyProbe.receiveMessage();
        assertEquals("redisValue", response.value);

        // 再次请求相同的键，这次应该从本地缓存获取
        cacheActor.tell(new CacheActor.Get("redisKey", replyProbe.getRef()));
        CacheActor.Value cachedResponse = replyProbe.receiveMessage();
        assertEquals("redisValue", cachedResponse.value);

        // 验证没有再次向 RedisActor 发送消息
        redisActorProbe.expectNoMessage();
    }
}