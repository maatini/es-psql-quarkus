FROM eclipse-temurin:21-jre-alpine

ARG QUARKUS_VERSION=1.1.0-SNAPSHOT
ENV JAVA_OPTS="-Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV QUARKUS_HTTP_HOST=0.0.0.0

WORKDIR /app

COPY target/quarkus-app/lib/ /app/lib/
COPY target/quarkus-app/*.jar /app/
COPY target/quarkus-app/app/ /app/app/
COPY target/quarkus-app/quarkus/ /app/quarkus/

EXPOSE 8080

USER 1001

ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
