# Stage 1: Build
FROM gradle:8.3-jdk21-jammy AS builder
WORKDIR /app
COPY . .
RUN gradle build --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

ENV JAVA_OPTS="-Xmx512m"
ENV SPRING_PROFILES_ACTIVE=prod

CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]