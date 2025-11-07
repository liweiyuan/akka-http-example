package com.example;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import com.example.routing.HelloRouteDefinition;
import com.example.routing.CacheRouteDefinition;
import com.example.routing.PostRouteDefinition;

import java.time.Duration;

public class SimpleHttpRoutes extends AllDirectives {

  private final ActorRef<SupervisorActor.Command> supervisor;
  private final ActorSystem<SupervisorActor.Command> system;
  private final Duration timeout;

  public SimpleHttpRoutes(
      ActorSystem<SupervisorActor.Command> system, ActorRef<SupervisorActor.Command> supervisor) {
    this.system = system;
    this.supervisor = supervisor;
    this.timeout = Duration.ofSeconds(5);
  }

  public Route createRoute() {
    return concat(
        new HelloRouteDefinition().createRoute(),
        new CacheRouteDefinition(system, supervisor).createRoute(),
        new PostRouteDefinition(system).createRoute());
  }
}
