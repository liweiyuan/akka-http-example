package com.example.routing;

import akka.actor.typed.ActorSystem;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.Route;
import com.example.model.PostRequestPayload;

public class PostRouteDefinition extends AllDirectives implements RouteDefinition {

  private final ActorSystem<?> system;

  public PostRouteDefinition(ActorSystem<?> system) {
    this.system = system;
  }

  @Override
  public Route createRoute() {
    return pathPrefix(
        "post",
        () ->
            path(
                PathMatchers.segment(),
                urlParam ->
                    post(
                        () ->
                            entity(
                                Jackson.unmarshaller(PostRequestPayload.class),
                                payload -> {
                                  system.log().info("Received POST request to /post");
                                  system.log().info("URL Parameter: {}", urlParam);
                                  system.log().info("JSON Body Field 1: {}", payload.field1);
                                  system.log().info("JSON Body Field 2: {}", payload.field2);
                                  return complete("OK");
                                }))));
  }
}
