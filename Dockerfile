FROM azul/zulu-openjdk-alpine:17-jre-latest

COPY target/rinha-vertx-fat.jar .

CMD ["java", "-jar", "rinha-vertx-fat.jar", "-Xms768M -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:+AlwaysPreTouch -XX:+ExitOnOutOfMemoryError"]
