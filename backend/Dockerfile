FROM maven:3.9.16-eclipse-temurin-25-noble AS builder
WORKDIR /app
COPY src/ src
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B package

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=builder /app/target/app.jar .
COPY docker-entrypoint.sh docker-entrypoint.sh
RUN chmod +x docker-entrypoint.sh
EXPOSE 8080


ENTRYPOINT ["./docker-entrypoint.sh"]