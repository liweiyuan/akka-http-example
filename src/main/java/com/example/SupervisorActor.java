package com.example;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class SupervisorActor extends AbstractBehavior<SupervisorActor.Command> {

  // 定义命令接口
  public interface Command {}

  // 转发给CacheActor的命令
  public static class ForwardToCache implements Command {
    public final CacheActor.Command cacheCommand;

    public ForwardToCache(CacheActor.Command cacheCommand) {
      this.cacheCommand = cacheCommand;
    }
  }

  // 获取CacheActor引用的命令
  public static class GetCacheActorRef implements Command {
    public final ActorRef<ActorRef<CacheActor.Command>> replyTo;

    public GetCacheActorRef(ActorRef<ActorRef<CacheActor.Command>> replyTo) {
      this.replyTo = replyTo;
    }
  }

  private final ActorContext<Command> context;
  private ActorRef<CacheActor.Command> cacheActor;
  private ActorRef<CacheActor.Command> redisActor;

  private SupervisorActor(ActorContext<Command> context) {
    super(context);
    this.context = context;
    // 必须先创建并监管RedisActor
    this.redisActor =
        context.spawn(
            Behaviors.supervise(RedisActor.create()).onFailure(SupervisorStrategy.restart()),
            "redis-actor");
    // 再创建CacheActor子Actor,并传入redisActor的引用
    this.cacheActor = context.spawn(CacheActor.create(redisActor), "cache-actor");
  }

  public static Behavior<Command> create() {
    return Behaviors.supervise(Behaviors.setup(SupervisorActor::new))
        .onFailure(SupervisorStrategy.restart());
  }

  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder()
        .onMessage(ForwardToCache.class, this::onForwardToCache)
        .onMessage(GetCacheActorRef.class, this::onGetCacheActorRef)
        .build();
  }

  private Behavior<Command> onForwardToCache(ForwardToCache command) {
    cacheActor.tell(command.cacheCommand);
    return this;
  }

  private Behavior<Command> onGetCacheActorRef(GetCacheActorRef command) {
    command.replyTo.tell(cacheActor);
    return this;
  }
}
