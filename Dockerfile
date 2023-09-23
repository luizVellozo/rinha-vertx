FROM azul/zulu-openjdk-alpine:17-jre-latest

COPY target/rinha-vertx-fat.jar .

CMD ["java", "-jar", "rinha-vertx-fat.jar", "$JAVA_OPTS"]
