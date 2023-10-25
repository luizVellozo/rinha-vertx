FROM azul/zulu-openjdk-alpine:17-latest

COPY target/rinha-vertx-fat.jar .

ENTRYPOINT java ${JAVA_OPTS} -jar rinha-vertx-fat.jar
