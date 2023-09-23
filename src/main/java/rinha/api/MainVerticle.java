package rinha.api;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

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

    Pool selectDb = PgPool.pool(vertx, pgOptions, poolOptions);

    router.get("/pessoas/:id").respond(ctx -> {
      String id = ctx.pathParam("id");

      if (id == null || id.isBlank() || id.length() != 36) {
        HttpServerResponse response = ctx.response();
        response.setStatusCode(400);
        response.end();
        return Future.succeededFuture();
      }

      return selectDb.preparedQuery("SELECT id, apelido, nome, nascimento, stack FROM pessoa WHERE id = $1")
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
        });
    });

    router.get("/pessoas")
      .respond(ctx -> {
        String t = ctx.queryParams().get("t");
        if (t == null || t.isEmpty()) {
          HttpServerResponse response = ctx.response();
          response.setStatusCode(400);
          response.end();
          return Future.succeededFuture();
        }

        return selectDb.preparedQuery("SELECT id, apelido, nome, nascimento, stack FROM pessoa WHERE BUSCA_TRGM LIKE $1 LIMIT 50")
          .execute(Tuple.of("%" + t + "%"))
          .map(query -> {

            if (!query.iterator().hasNext()) {
              return new JsonArray();
            }

            return new JsonArray(StreamSupport.stream(
              Spliterators.spliteratorUnknownSize(query.iterator(), Spliterator.ORDERED),
              false
            ).map(MainVerticle::buildPersonResponse).toList());
          });
      });

    router.get("/contagem-pessoas")
      .respond(ctx -> selectDb.preparedQuery("SELECT COUNT(1) FROM pessoa")
        .execute()
        .map(query -> query.iterator().next().getLong(0)));

    router.post("/pessoas").handler(ctx -> {
      JsonObject jsonPerson = ctx.body().asJsonObject();

      Person person = build(jsonPerson);
      if (!person.isValid()) {
        HttpServerResponse response = ctx.response();
        response.setStatusCode(400);
        response.end();
        return;
      }

      String id = person.getId();
      selectDb.preparedQuery("INSERT INTO pessoa (id, apelido, nome, nascimento, stack) VALUES ($1, $2, $3, $4, $5)")
        .execute(Tuple.of(id, person.apelido, person.nome, person.nascimento, person.getStackInString()));

      HttpServerResponse response = ctx.response();
      response.setStatusCode(201);
      response.putHeader("Location", "/pessoas/" + id);
      response.end();
    });

    var server = vertx.createHttpServer();
    server.requestHandler(router)
      .listen(8080, http -> {
        if (http.succeeded()) {
          startPromise.complete();
          System.out.println("HTTP server started on port 8080");
          System.out.println("POOL_SIZE: " +getConfigOrDefault("POOL_SIZE", "25"));
          System.out.println("JAVA_OPTS: " +System.getenv("JAVA_OPTS"));
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
      .put("stack", null);

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
