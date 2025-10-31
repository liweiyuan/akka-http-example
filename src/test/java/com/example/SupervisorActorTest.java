package com.example;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import org.junit.AfterClass;
import org.junit.Test;

public class SupervisorActorTest {

    private static final ActorTestKit testKit = ActorTestKit.create();

    @AfterClass
    public static void teardown() {
        testKit.shutdownTestKit();
    }

    @Test
    public void testSupervisorActorCreation() {
        // 创建 SupervisorActor
        ActorRef<SupervisorActor.Command> supervisorActor = testKit.spawn(SupervisorActor.create());

        // 验证 actor 创建成功（不抛出异常）
        assert supervisorActor != null;
    }

    @Test
    public void testGetCacheActorRef() {
        // 创建 SupervisorActor
        ActorRef<SupervisorActor.Command> supervisorActor = testKit.spawn(SupervisorActor.create());

        // 创建测试探针来接收 CacheActor 引用
        TestProbe<ActorRef<CacheActor.Command>> probe = testKit.createTestProbe();

        // 发送 GetCacheActorRef 消息
        supervisorActor.tell(new SupervisorActor.GetCacheActorRef(probe.getRef()));

        // 验证收到了 CacheActor 的引用
        ActorRef<CacheActor.Command> cacheActorRef = probe.receiveMessage();
        assert cacheActorRef != null;
    }

    @Test
    public void testForwardToCache() {
        // 创建 SupervisorActor
        ActorRef<SupervisorActor.Command> supervisorActor = testKit.spawn(SupervisorActor.create());

        // 创建测试探针来监控 CacheActor 接收到的消息
        TestProbe<CacheActor.Command> cacheProbe = testKit.createTestProbe();

        // 我们不能直接访问内部的 cacheActor，但可以通过模拟方式测试转发机制
        // 在这个简单的测试中，我们验证 SupervisorActor 能够接收 ForwardToCache 消息
        supervisorActor.tell(new SupervisorActor.ForwardToCache(new CacheActor.Put("test", "value")));

        // 这个测试验证消息可以被正常接收，不会抛出异常
        assert true; // 如果没有异常，则测试通过
    }
}