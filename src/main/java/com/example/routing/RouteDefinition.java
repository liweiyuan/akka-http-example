package com.example.routing;

import akka.http.javadsl.server.Route;

public interface RouteDefinition {
    Route createRoute();
}
