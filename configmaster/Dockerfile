FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /build

COPY pom.xml .
COPY src ./src

RUN mvn -B -DskipTests package dependency:copy-dependencies \
    -DoutputDirectory=target/dependency


FROM eclipse-temurin:21-jre-alpine-3.24

WORKDIR /app

RUN addgroup -S configmaster && \
    adduser -S -u 10001 -G configmaster configmaster

COPY --from=build /build/target/configmaster-0.1.0.jar /app/configmaster.jar
COPY --from=build /build/target/dependency /app/lib
COPY config/client-spec.json /app/config/client-spec.json

USER configmaster

ENTRYPOINT ["java", "-cp", "/app/configmaster.jar:/app/lib/*", "com.genkey.configmaster.ConfigMasterCli"]