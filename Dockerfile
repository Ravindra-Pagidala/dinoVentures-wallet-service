FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app
COPY pom.xml .
COPY src ./src

RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests -Dmaven.test.skip=true

FROM eclipse-temurin:17-jre

RUN apt-get update && apt-get install -y postgresql-client tini curl && rm -rf /var/lib/apt/lists/*

RUN groupadd -g 1001 appgroup && \
    useradd -u 1001 -G appgroup -s /bin/sh appuser

WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

RUN mkdir -p /app/logs && \
    chown -R appuser:appgroup /app && \
    chmod -R 755 /app

USER appuser
EXPOSE 8080

ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
