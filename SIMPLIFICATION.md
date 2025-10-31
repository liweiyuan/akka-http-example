# 项目简化方案

## 简化目标

在不影响核心业务逻辑的前提下，简化项目的路由管理部分。

## 简化内容

### 1. 路由管理简化

原项目使用了复杂的路由注册表模式：
- RouteDefinition接口
- RouteRegistry类
- 各个路由定义类（HelloRouteDefinition、CacheRouteDefinition、PostRouteDefinition）

我们创建了一个更简单的路由管理方式：

### 2. 新增SimpleHttpRoutes类

该类直接管理所有路由，避免了不必要的抽象层：

```java
public class SimpleHttpRoutes extends AllDirectives {
    // 直接使用concat组合各路由
    public Route createRoute() {
        return concat(
                new HelloRouteDefinition().createRoute(),
                new CacheRouteDefinition(system, supervisor).createRoute(),
                new PostRouteDefinition(system).createRoute()
        );
    }
}
```

### 3. 修改HttpServer类

更新HttpServer以使用新的SimpleHttpRoutes：

```java
// 使用新的简化路由类
SimpleHttpRoutes routes = new SimpleHttpRoutes(system, system);
Route route = routes.createRoute();
```

## 简化效果

1. 减少了RouteRegistry和RouteDefinition的抽象层
2. 保持了所有核心功能不变
3. 代码更直接易懂
4. 编译和运行正常

## 保持不变的部分

1. 两级缓存架构（CacheActor + RedisActor）
2. Actor监督策略
3. 所有HTTP端点功能
4. 与Actor系统的交互方式

这种简化方式去除了过度设计的部分，同时保持了项目的完整功能。