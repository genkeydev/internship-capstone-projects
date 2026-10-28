# Stage 1: Build HealthLogger using Java 21 and Maven
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

COPY pom.xml .
COPY src ./src
COPY config ./config
COPY SECURITY.md .
RUN mvn -B -ntp package dependency:copy-dependencies -DincludeScope=runtime \
    && ls -l target/healthlogger-0.1.0.jar target/dependency
# Stage 2: Run the application using a smaller Java 21 runtime
FROM eclipse-temurin:21-jre

WORKDIR /app

ENV HEALTHLOGGER_SPEC=/app/config/client-spec.json
ENV HEALTHLOGGER_STORE=/app/data/events.jsonl

COPY --from=build /build/target/healthlogger-0.1.0.jar /app/app.jar
COPY --from=build /build/target/dependency/ /app/dependency/
COPY --from=build /build/config/ /app/config/

RUN rm -f /usr/bin/pebble \
    && mkdir -p /app/data \
    && chown -R 10001:10001 /app
USER 10001:10001

EXPOSE 8088

CMD ["java", "-cp", "/app/app.jar:/app/dependency/*", "com.genkey.healthlogger.HealthLoggerCli", "serve"]
