package com.example;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;

public class RedisActor extends AbstractBehavior<CacheActor.Command> {

  private final ActorContext<CacheActor.Command> context;
  private Jedis jedis;

  private RedisActor(ActorContext<CacheActor.Command> context) {
    super(context);
    this.context = context;
    try {
      this.jedis = new Jedis("127.0.0.1", 6379);
      context.getLog().info("成功连接到 Redis");
    } catch (JedisConnectionException e) {
      context.getLog().error("无法连接到 Redis", e);
      // 连接失败时，抛出异常以触发父Actor的监管策略
      throw new RuntimeException("无法连接到 Redis", e);
    }
  }

  public static Behavior<CacheActor.Command> create() {
    return Behaviors.setup(RedisActor::new);
  }

  @Override
  public Receive<CacheActor.Command> createReceive() {
    return newReceiveBuilder()
        .onMessage(CacheActor.Get.class, this::onGet)
        .onMessage(CacheActor.Put.class, this::onPut)
        .onMessage(CacheActor.SimulateFailure.class, this::onSimulateFailure)
        .onSignal(
            akka.actor.typed.PreRestart.class,
            signal -> {
              if (jedis != null) {
                jedis.close();
                context.getLog().info("Redis 连接已关闭");
              }
              return this;
            })
        .build();
  }

  private Behavior<CacheActor.Command> onGet(CacheActor.Get command) {
    try {
      String value = jedis.get(command.key);
      command.replyTo.tell(new CacheActor.Value(value));
    } catch (JedisConnectionException e) {
      context.getLog().error("Redis get 操作失败", e);
      // 通知父Actor失败
      throw new RuntimeException("Redis get 操作失败", e);
    }
    return this;
  }

  private Behavior<CacheActor.Command> onPut(CacheActor.Put command) {
    try {
      jedis.set(command.key, command.value);
      context.getLog().info("Redis 缓存已更新: {} = {}", command.key, command.value);
    } catch (JedisConnectionException e) {
      context.getLog().error("Redis set 操作失败", e);
      // 通知父Actor失败
      throw new RuntimeException("Redis set 操作失败", e);
    }
    return this;
  }

  private Behavior<CacheActor.Command> onSimulateFailure(CacheActor.SimulateFailure command) {
    context.getLog().info("模拟 Redis 故障");
    throw new RuntimeException("模拟 Redis 故障");
  }
}
