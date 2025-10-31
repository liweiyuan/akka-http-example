package com.example.routing;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.typed.ActorSystem;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.testkit.JUnitRouteTest;
import akka.http.javadsl.testkit.TestRoute;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HelloRouteDefinitionTest extends JUnitRouteTest {

    private static final ActorTestKit testKit = ActorTestKit.create();
    private TestRoute appRoute;

    @Before
    public void setup() {
        ActorSystem<Void> system = testKit.system();
        HelloRouteDefinition routeDefinition = new HelloRouteDefinition();
        appRoute = testRoute(routeDefinition.createRoute());
    }

    @AfterClass
    public static void teardown() {
        testKit.shutdownTestKit();
    }

    @Test
    public void testHelloRoute() {
        appRoute.run(HttpRequest.GET("/hello"))
                .assertStatusCode(StatusCodes.OK)
                .assertContentType(ContentTypes.TEXT_PLAIN_UTF8)
                .assertEntity("Hello World from Akka HTTP!");
    }

    @Test
    public void testNonHelloRouteReturns404() {
        appRoute.run(HttpRequest.GET("/nonexistent"))
                .assertStatusCode(StatusCodes.NOT_FOUND);
    }
}