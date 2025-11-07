package com.example.routing;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.typed.ActorSystem;
import akka.http.javadsl.model.*;
import akka.http.javadsl.testkit.JUnitRouteTest;
import akka.http.javadsl.testkit.TestRoute;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

public class PostRouteDefinitionTest extends JUnitRouteTest {

  private static final ActorTestKit testKit = ActorTestKit.create();
  private TestRoute appRoute;

  @Before
  public void setup() {
    ActorSystem<Void> system = testKit.system();
    PostRouteDefinition routeDefinition = new PostRouteDefinition(system);
    appRoute = testRoute(routeDefinition.createRoute());
  }

  @AfterClass
  public static void teardown() {
    testKit.shutdownTestKit();
  }

  @Test
  public void testPostRouteWithValidPayload() {
    String jsonPayload = "{ \"field1\": \"testValue\", \"field2\": 123 }";

    appRoute
        .run(
            HttpRequest.POST("/post/testParam")
                .withEntity(ContentTypes.APPLICATION_JSON, jsonPayload))
        .assertStatusCode(StatusCodes.OK)
        .assertEntity("OK");
  }

  @Test
  public void testPostRouteWithInvalidPath() {
    String jsonPayload = "{ \"field1\": \"testValue\", \"field2\": 123 }";

    appRoute
        .run(HttpRequest.POST("/post").withEntity(ContentTypes.APPLICATION_JSON, jsonPayload))
        .assertStatusCode(StatusCodes.NOT_FOUND);
  }

  @Test
  public void testPostRouteWithInvalidMethod() {
    appRoute
        .run(HttpRequest.GET("/post/testParam"))
        .assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
  }

  @Test
  public void testPostRouteWithInvalidJson() {
    String invalidJsonPayload = "{ invalid json }";

    appRoute
        .run(
            HttpRequest.POST("/post/testParam")
                .withEntity(ContentTypes.APPLICATION_JSON, invalidJsonPayload))
        .assertStatusCode(StatusCodes.BAD_REQUEST);
  }
}
