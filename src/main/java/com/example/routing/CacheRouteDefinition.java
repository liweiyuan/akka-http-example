package com.example.routing;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.Route;
import com.example.CacheActor;
import com.example.SupervisorActor;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

public class CacheRouteDefinition extends AllDirectives implements RouteDefinition {

    private final ActorRef<SupervisorActor.Command> supervisor;
    private final ActorSystem<?> system;
    private final Duration timeout;

    public CacheRouteDefinition(ActorSystem<?> system, ActorRef<SupervisorActor.Command> supervisor) {
        this.system = system;
        this.supervisor = supervisor;
        this.timeout = Duration.ofSeconds(5);
    }

    @Override
    public Route createRoute() {
        return pathPrefix("cache", () ->
                concat(
                        get(() ->
                                path(PathMatchers.segment(), key ->
                                        onSuccess(askCacheActorForValue(key), value -> {
                                            if (value == null) {
                                                return complete(HttpResponse.create()
                                                        .withStatus(404)
                                                        .withEntity(ContentTypes.TEXT_PLAIN_UTF8, "未找到键: " + key));
                                            } else {
                                                return complete(HttpResponse.create()
                                                        .withStatus(200)
                                                        .withEntity(ContentTypes.TEXT_PLAIN_UTF8, value));
                                            }
                                        })
                                )
                        ),
                        put(() ->
                                pathPrefix(PathMatchers.segment(), key ->
                                        path(PathMatchers.remaining(), value -> {
                                            supervisor.tell(new SupervisorActor.ForwardToCache(new CacheActor.Put(key, value)));
                                            return complete(HttpResponse.create()
                                                    .withStatus(200)
                                                    .withEntity(ContentTypes.TEXT_PLAIN_UTF8, "缓存已设置: " + key + " = " + value));
                                        })
                                )
                        ),
                        post(() ->
                                path("failure", () -> {
                                    supervisor.tell(new SupervisorActor.ForwardToCache(new CacheActor.SimulateFailure()));
                                    return complete(HttpResponse.create()
                                            .withStatus(200)
                                            .withEntity(ContentTypes.TEXT_PLAIN_UTF8, "已发送故障模拟命令"));
                                })
                        )
                )
        );
    }

    private CompletionStage<String> askCacheActorForValue(String key) {
        CompletionStage<ActorRef<CacheActor.Command>> cacheActorFuture = AskPattern.ask(
                supervisor,
                replyTo -> new SupervisorActor.GetCacheActorRef(replyTo),
                timeout,
                system.scheduler()
        );

        return cacheActorFuture.thenCompose(cacheActor ->
                AskPattern.<CacheActor.Command, CacheActor.Value>ask(
                        cacheActor,
                        replyTo -> new CacheActor.Get(key, replyTo),
                        timeout,
                        system.scheduler()
                )
        ).thenApply(cacheValue -> cacheValue.value);
    }
}
