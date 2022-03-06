package com.jz.demo.vertx.core;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.springframework.core.annotation.AnnotatedElementUtils;

/**
 * @Auther jd
 */
public abstract class AbstractVertxController extends AbstractVerticle implements Handler<RoutingContext> {

  private static final Map<Class<?>, RateLimitHandler> rateLimitHandlerCache = new HashMap<>();

  private final Router router;

  public AbstractVertxController(Router router) {
    this.router = router;
  }

  @Override
  public void start() throws Exception {
    VertxController vertxController = AnnotatedElementUtils
        .getMergedAnnotation(this.getClass(), VertxController.class);
    HttpServer httpServer = vertx.createHttpServer();
    Route route = router.route(vertxController.path());
    for (VertxHttpMethod httpMethod : vertxController.method()) {
      route.method(httpMethod.getMethod());
    }
    configureRateLimitHandler(route, vertxController)
        .handler(BodyHandler.create())
        .handler(this);
    httpServer.requestHandler(router)
        .listen(SpringUtil.resolvePlaceholdersToInteger(vertxController.port()));
  }

  private Route configureRateLimitHandler(Route route, VertxController vertxController) {
    Double permitsPerSecond = SpringUtil.resolvePlaceholdersToDouble(vertxController.permitsPerSecond());
    if (Objects.nonNull(permitsPerSecond)) {
      Long acquirePermitTimeoutNanos = SpringUtil.resolvePlaceholdersToLong(vertxController.acquirePermitsTimeoutNanos());
      synchronized (rateLimitHandlerCache) {
        RateLimitHandler handler = rateLimitHandlerCache.computeIfAbsent(this.getClass(),
            key -> new RateLimitHandler(permitsPerSecond, acquirePermitTimeoutNanos,
                TimeUnit.NANOSECONDS));
        route.handler(handler);
      }
    }
    return route;
  }

}
