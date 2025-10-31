package com.example;

import akka.actor.typed.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.unmarshalling.Unmarshaller;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3, time = 10)
@Threads(4)
public class CacheBenchmark {
    
    private ActorSystem<SupervisorActor.Command> system;
    private Http http;
    private ServerBinding binding;
    private String baseUrl;
    
    @Setup(Level.Trial)
    public void setup() throws InterruptedException {
        // 创建 Actor 系统
        system = ActorSystem.create(SupervisorActor.create(), "CacheBenchmark");
        http = Http.get(system);
        
        // 创建路由
        SimpleHttpRoutes routes = new SimpleHttpRoutes(system, system);
        Route route = routes.createRoute();
        
        // 启动服务器
        CompletableFuture<ServerBinding> bound = http.newServerAt("localhost", 0)
                .bind(route)
                .toCompletableFuture();
        
        binding = bound.join();
        baseUrl = "http://localhost:" + binding.localAddress().getPort();
        
        // 确保服务器已启动
        Thread.sleep(1000);
    }
    
    @TearDown(Level.Trial)
    public void tearDown() {
        if (binding != null) {
            binding.unbind().toCompletableFuture().join();
        }
        if (system != null) {
            system.terminate();
            system.getWhenTerminated().toCompletableFuture().join();
        }
    }
    
    @Benchmark
    public String testHelloEndpoint() {
        HttpRequest request = HttpRequest.GET(baseUrl + "/hello");
        CompletionStage<HttpResponse> response = http.singleRequest(request);
        HttpResponse httpResponse = response.toCompletableFuture().join();
        assert httpResponse.status() == StatusCodes.OK;
        return Unmarshaller.entityToString().unmarshal(httpResponse.entity(), system)
                .toCompletableFuture().join();
    }
    
    @Benchmark
    public String testCachePutEndpoint() {
        HttpRequest request = HttpRequest.PUT(baseUrl + "/cache/test-key/test-value");
        CompletionStage<HttpResponse> response = http.singleRequest(request);
        HttpResponse httpResponse = response.toCompletableFuture().join();
        assert httpResponse.status() == StatusCodes.OK;
        return Unmarshaller.entityToString().unmarshal(httpResponse.entity(), system)
                .toCompletableFuture().join();
    }
    
    @Benchmark
    public String testCacheGetEndpoint() {
        // 先放入一个值
        HttpRequest putRequest = HttpRequest.PUT(baseUrl + "/cache/benchmark-key/benchmark-value");
        http.singleRequest(putRequest).toCompletableFuture().join();
        
        // 再获取这个值
        HttpRequest getRequest = HttpRequest.GET(baseUrl + "/cache/benchmark-key");
        CompletionStage<HttpResponse> response = http.singleRequest(getRequest);
        HttpResponse httpResponse = response.toCompletableFuture().join();
        assert httpResponse.status() == StatusCodes.OK;
        return Unmarshaller.entityToString().unmarshal(httpResponse.entity(), system)
                .toCompletableFuture().join();
    }
    
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(CacheBenchmark.class.getSimpleName())
                .forks(1)
                .build();

        new Runner(opt).run();
    }
}