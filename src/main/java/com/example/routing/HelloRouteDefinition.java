package com.example.routing;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;

public class HelloRouteDefinition extends AllDirectives implements RouteDefinition {

    @Override
    public Route createRoute() {
        return path("hello", () ->
                get(() ->
                        complete(HttpResponse.create()
                                .withStatus(200)
                                .withEntity(ContentTypes.TEXT_PLAIN_UTF8, "Hello World from Akka HTTP!"))
                )
        );
    }
}
