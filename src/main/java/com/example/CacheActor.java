package com.example;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.util.HashMap;
import java.util.Map;

public class CacheActor extends AbstractBehavior<CacheActor.Command> {

  // 定义命令接口
  public interface Command {}

  // 获取缓存值的命令
  public static class Get implements Command {
    public final String key;
    public final ActorRef<Value> replyTo;

    public Get(String key, ActorRef<Value> replyTo) {
      this.key = key;
      this.replyTo = replyTo;
    }
  }

  // 设置缓存值的命令
  public static class Put implements Command {
    public final String key;
    public final String value;

    public Put(String key, String value) {
      this.key = key;
      this.value = value;
    }
  }

  // 缓存值的响应
  public static class Value {
    public final String value;

    public Value(String value) {
      this.value = value;
    }
  }

  // 模拟缓存故障的命令
  public static class SimulateFailure implements Command {}

  private static class RedisResponse implements Command {
    public final Value value;
    public final String key;
    public final ActorRef<Value> originalReplyTo;

    public RedisResponse(Value value, String key, ActorRef<Value> originalReplyTo) {
      this.value = value;
      this.key = key;
      this.originalReplyTo = originalReplyTo;
    }
  }

  private final Map<String, String> cache = new HashMap<>();
  private final ActorContext<Command> context;
  private final ActorRef<Command> redisActor;

  private CacheActor(ActorContext<Command> context, ActorRef<Command> redisActor) {
    super(context);
    this.context = context;
    this.redisActor = redisActor;
  }

  public static Behavior<Command> create(ActorRef<Command> redisActor) {
    return Behaviors.setup(context -> new CacheActor(context, redisActor));
  }

  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder()
        .onMessage(Get.class, this::onGet)
        .onMessage(Put.class, this::onPut)
        .onMessage(SimulateFailure.class, this::onSimulateFailure)
        .onMessage(RedisResponse.class, this::onRedisResponse)
        .build();
  }

  private Behavior<Command> onGet(Get command) {
    String value = cache.get(command.key);
    if (value != null) {
      // Found in local cache, reply immediately
      command.replyTo.tell(new Value(value));
      return this;
    } else {
      // Not found in local cache, query Redis
      ActorRef<Value> responseAdapter =
          context.messageAdapter(
              Value.class,
              valueFromRedis -> new RedisResponse(valueFromRedis, command.key, command.replyTo));
      redisActor.tell(new Get(command.key, responseAdapter));
      return this;
    }
  }

  private Behavior<Command> onRedisResponse(RedisResponse response) {
    if (response.value.value != null) {
      // Found in Redis, update local cache and reply
      cache.put(response.key, response.value.value);
      context.getLog().info("本地缓存已从 Redis 更新: {} = {}", response.key, response.value.value);
      response.originalReplyTo.tell(response.value);
    } else {
      // Not found in Redis either, reply with null
      response.originalReplyTo.tell(new Value(null));
    }
    return this;
  }

  private Behavior<Command> onPut(Put command) {
    // Put the value in both RedisActor and local cache
    redisActor.tell(command);
    cache.put(command.key, command.value);
    context.getLog().info("本地缓存已更新: {} = {}", command.key, command.value);
    return this;
  }

  private Behavior<Command> onSimulateFailure(SimulateFailure command) {
    context.getLog().info("模拟缓存故障");
    throw new RuntimeException("模拟缓存故障");
  }
}
