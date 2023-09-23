FROM azul/zulu-openjdk-alpine:17-jre-latest

COPY target/rinha-backend-fat.jar .

CMD ["java", "-jar", "rinha-backend-fat.jar", "-verbose:gc -Xmx768M -Xms768M -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:+AlwaysPreTouch -XX:+ExitOnOutOfMemoryError"]
