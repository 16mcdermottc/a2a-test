FROM gradle:8.4-jdk17 AS builder
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts ./
COPY src ./src
RUN gradle shadowJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/build/libs/*-all.jar app.jar
CMD ["java", "-jar", "app.jar"]
