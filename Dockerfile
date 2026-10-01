FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /src/build/libs/*-SNAPSHOT.jar app.jar
CMD ["sh", "-c", "java -jar app.jar"]
