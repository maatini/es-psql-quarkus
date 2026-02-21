# Stage 1: Build the application with Maven
FROM quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-21 AS build
USER root
RUN chown -R quarkus:quarkus /workspace
USER quarkus
WORKDIR /workspace

COPY --chown=quarkus:quarkus pom.xml ./
COPY --chown=quarkus:quarkus mvnw ./
COPY --chown=quarkus:quarkus .mvn .mvn/
# Es werden offline Abhängigkeiten geladen, kann übersprungen werden, falls das Plugin fehlt
# RUN ./mvnw dependency:go-offline || true

COPY --chown=quarkus:quarkus eventsourcing-core eventsourcing-core/
COPY --chown=quarkus:quarkus pom.xml ./

# Default zu JVM Build, kann aber über --build-arg NATIVE=true überschrieben werden
ARG NATIVE=false
RUN if [ "$NATIVE" = "true" ] ; then \
      ./mvnw package -Dnative -DskipTests -f eventsourcing-core/pom.xml ; \
    else \
      ./mvnw package -DskipTests -f eventsourcing-core/pom.xml ; \
    fi

# Stage 2: Create the runtime image
FROM registry.access.redhat.com/ubi9/ubi-minimal:9.4
WORKDIR /work/
RUN chown 1001 /work \
    && chmod "g+rwX" /work \
    && chown 1001:root /work

# Kopiere das Native Binary (falls vorhanden) oder den fast-jar JVM Build
COPY --from=build --chown=1001:root /workspace/eventsourcing-core/target/*-runner /work/application
COPY --from=build --chown=1001:root /workspace/eventsourcing-core/target/quarkus-app/ /work/quarkus-app/

EXPOSE 8080
USER 1001

# Wenn die native application Datei da ist, führe sie aus, sonst das JVM jar
CMD ["sh", "-c", "if [ -f /work/application ]; then /work/application -Dquarkus.http.host=0.0.0.0; else java -Dquarkus.http.host=0.0.0.0 -jar /work/quarkus-app/quarkus-run.jar; fi"]
