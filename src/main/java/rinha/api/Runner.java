package rinha.api;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

public class Runner {

  public static void main(String[] args) {
    VertxOptions vertxOptions = new VertxOptions();
    vertxOptions.setPreferNativeTransport(true);
    // TODO: check if it really improves
    //vertxOptions.setWorkerPoolSize(60);

    final Vertx vertx = Vertx.vertx(vertxOptions);

    var options = new DeploymentOptions();
    options.setInstances(1);

    System.out.println("Deploying verticles");

    vertx.deployVerticle(MainVerticle.class, options, ch -> {
      if (ch.failed()) {
        ch.cause().printStackTrace();
      } else {
        System.out.println("Server verticles deployed.");
      }
    });

    vertx.exceptionHandler(throwable -> System.out.println("VERTX FAIL: "+throwable.getMessage()));
  }
}
