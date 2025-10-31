package com.example;

import akka.actor.typed.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.server.Route;

import java.util.concurrent.CompletionStage;

public class HttpServer {

    public static void main(String[] args) {
        // 创建 Actor 系统, SupervisorActor 作为根 actor
        ActorSystem<SupervisorActor.Command> system = ActorSystem.create(SupervisorActor.create(), "HttpServer");

        // 创建路由
        SimpleHttpRoutes routes = new SimpleHttpRoutes(system, system);
        Route route = routes.createRoute();

        // 启动 HTTP 服务器
        final Http http = Http.get(system);
        final CompletionStage<ServerBinding> binding = http.newServerAt("localhost", 8080).bind(route);

        // 打印服务器启动信息
        binding.thenAccept(serverBinding ->
            System.out.println("服务器现在运行在 http://localhost:" + serverBinding.localAddress() + "/")
        ).exceptionally(failure -> {
            System.err.println("服务器绑定失败: " + failure.getMessage());
            system.terminate();
            return null;
        });

        // 保持应用程序运行
        system.getWhenTerminated().toCompletableFuture().join();
    }
}