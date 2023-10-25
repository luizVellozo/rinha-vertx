package rinha.api;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.StreamSupport;

public class MainVerticle extends AbstractVerticle {



  @Override
  public void start(Promise<Void> startPromise) throws Exception {

    var router = Router.router(vertx);
    router.route().handler(BodyHandler.create());

    PgConnectOptions pgOptions = new PgConnectOptions()
      .setPort(Integer.parseInt(getConfigOrDefault("DB_PORT", "5432")))
      .setHost(getConfigOrDefault("DB_HOST", "db"))
      .setDatabase(getConfigOrDefault("DB", "rinhadb"))
      .setUser(getConfigOrDefault("DB_USER", "root"))
      .setPassword(getConfigOrDefault("DB_PASS", "1234"));

    PoolOptions poolOptions = new PoolOptions()
      .setMaxSize(Integer.parseInt(getConfigOrDefault("POOL_SIZE", "25")));
    Pool db = PgPool.pool(vertx, pgOptions, poolOptions);

    Redis redisClient = Redis.createClient(
      vertx,
      new RedisOptions()
        .setConnectionString("redis://redis:6379")
        .setMaxPoolSize(200)
        .setMaxWaitingHandlers(200));

    RedisAPI redis = RedisAPI.api(redisClient);

    router.get("/pessoas/:id").respond(ctx -> {
      String id = ctx.pathParam("id");

      if (id == null || id.isBlank() || id.length() != 36) {
        HttpServerResponse response = ctx.response();
        response.setStatusCode(400);
        response.end();
        return Future.succeededFuture();
      }

      return redis.get(id).compose(valueInCache -> {

        if (valueInCache != null) {
          JsonObject value = new JsonObject(valueInCache.toBuffer());
          return Future.succeededFuture(value);
        }

        return db.preparedQuery("SELECT id, apelido, nome, nascimento, stack FROM pessoa WHERE id = $1")
          .execute(Tuple.of(id))
          .map(query -> {

            if (!query.iterator().hasNext()) {
              HttpServerResponse response = ctx.response();
              response.setStatusCode(404);
              response.end();
              return null;
            }

            Row personRow = query.iterator().next();
            return buildPersonResponse(personRow);
          }).onSuccess(jsonPerson -> {
            if (jsonPerson != null) redis.mset(List.of(id, jsonPerson.encode()));
          })
          .onFailure(throwable -> System.out.println("GET FAIL: "+throwable.getMessage()));
      });
    }).failureHandler(throwable -> System.out.println("GET FAIL"));

    router.get("/pessoas")
      .respond(ctx -> {
        String t = ctx.queryParams().get("t");
        if (t == null || t.isEmpty()) {
          HttpServerResponse response = ctx.response();
          response.setStatusCode(400);
          response.end();
          return Future.succeededFuture();
        }
        return redis.get("t:"+t.toLowerCase()).compose(valueInCache -> {
          if (valueInCache != null) {
            JsonArray value = new JsonArray(valueInCache.toBuffer());
            return Future.succeededFuture(value);
          }

          return db.preparedQuery("SELECT id, apelido, nome, nascimento, stack FROM pessoa WHERE BUSCA_TRGM LIKE $1 LIMIT 50")
            .execute(Tuple.of("%" + t.toLowerCase() + "%"))
            .map(query -> {

              if (!query.iterator().hasNext()) {
                return new JsonArray();
              }

              return new JsonArray(StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(query.iterator(), Spliterator.ORDERED),
                false
              ).map(MainVerticle::buildPersonResponse).toList());
            }).onSuccess(personArray -> redis.mset(List.of("t:"+t.toLowerCase(), personArray.encode())));

        });
      }).failureHandler(throwable -> System.out.println("GET T FAIL"));

    router.get("/contagem-pessoas")
      .respond(ctx -> db.preparedQuery("SELECT COUNT(1) FROM pessoa")
        .execute()
        .map(query -> query.iterator().next().getLong(0)));

    router.post("/pessoas").handler(ctx -> {
      JsonObject jsonPerson = ctx.body().asJsonObject();

      Person person = build(jsonPerson);

      redis.get(person.apelido).onComplete(valueInCache -> {

        if (valueInCache != null) {
          var value = valueInCache.result();
          if (value != null) {
            HttpServerResponse response = ctx.response();
            response.setStatusCode(201);
            response.putHeader("Location", "/pessoas/"+ value);
            response.end();
            return;
          }
        }

        if (!person.isValid()) {
          HttpServerResponse response = ctx.response();
          response.setStatusCode(400);
          response.end();
          return;
        }

        final String id = person.getId();
        jsonPerson.put("id", id);
        final String json = jsonPerson.encode();

        db.preparedQuery("INSERT INTO pessoa (id, apelido, nome, nascimento, stack) VALUES ($1, $2, $3, $4, $5)")
          .execute(Tuple.of(id, person.apelido, person.nome, person.nascimento, person.getStackInString()))
          .onFailure(throwable -> System.out.println("INSERT FAIL: "+throwable.getMessage()));
        redis.mset(List.of(id, json, person.apelido, id));

        HttpServerResponse response = ctx.response();
        response.setStatusCode(201);
        response.putHeader("Location", "/pessoas/" + id);
        response.end();
      });
    }).failureHandler(throwable -> System.out.println("POST FAIL"));


    var options = new HttpServerOptions();
    options
      .setPort(80)
      .setCompressionSupported(true)
      .setHandle100ContinueAutomatically(true)
      .setTcpFastOpen(true)
      .setTcpNoDelay(true)
      .setTcpQuickAck(true);

    var server = vertx.createHttpServer(options);
    server.requestHandler(router)
      .exceptionHandler(throwable -> System.out.println("SERVER FAIL: "+throwable.getMessage()))
      .listen( http -> {
        if (http.succeeded()) {
          startPromise.complete();
          System.out.println("HTTP server started on port 80");

          System.out.println("POOL_SIZE: " + getConfigOrDefault("POOL_SIZE", "25"));
          System.out.println("JAVA_OPTS: " + System.getenv("JAVA_OPTS"));
        } else {
          startPromise.fail(http.cause());
        }
      });
  }

  private static String getConfigOrDefault(String config, String defaultValue) {
    var value = System.getenv(config);
    return value != null ? value : defaultValue;
  }

  public static JsonObject buildPersonResponse(Row personRow) {

    return buildPersonResponse(personRow.getString("id"), personRow.getString("apelido"),
      personRow.getString("nome"), personRow.getString("nascimento"), personRow.getString("stack"));
  }

  public static JsonObject buildPersonResponse(String id, String apelido, String name, String nascimento, String stacks) {

    var json = new JsonObject()
      .put("id", id)
      .put("apelido", apelido)
      .put("nome", name)
      .put("nascimento", nascimento)
      .putNull("stack");

    if (stacks != null && !stacks.isBlank()) {
      json.put("stack", JsonArray.of(stacks.split(",")));
    }

    return json;
  }

  public static Person build(JsonObject payload) {

    var person = new Person();

    if (payload == null) {
      return person;
    }

    person.apelido = payload.getString("apelido");
    person.nome = payload.getString("nome");
    person.nascimento = payload.getString("nascimento");

    try {
      // TODO: use VERT.X schema validation for better serialization/decoding handling
      JsonArray stack = payload.getJsonArray("stack");
      person.setStack(stack);
    } catch (Exception ignored) {}
    return person;
  }
}

/*
  //Model

  POST /pessoas – para criar um recurso pessoa.
  GET /pessoas/[:id] – para consultar um recurso criado com a requisição anterior.
  GET /pessoas?t=[:termo da busca] – para fazer uma busca por pessoas.
  GET /contagem-pessoas – endpoint especial para contagem de pessoas cadastradas.

  {
    "apelido" : "josé",
    "nome" : "José Roberto",
    "nascimento" : "2000-10-01",
    "stack" : ["C#", "Node", "Oracle"]
  }
 */
