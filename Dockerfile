# Maven Wrapper construye el mismo ejecutable que se utiliza fuera de Docker.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline
COPY src src
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN apk add --no-cache curl && addgroup -S kynex && adduser -S -G kynex kynex
WORKDIR /app
COPY --from=build --chown=kynex:kynex /workspace/target/kynex-backend-0.1.0-SNAPSHOT.jar app.jar
USER kynex
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=12 CMD curl --fail --silent http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
