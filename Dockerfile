FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY build/libs/*.jar app.jar
CMD ["sh", "-c", "java -jar app.jar"]