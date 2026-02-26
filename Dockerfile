FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY deploy/*.jar app.jar
CMD ["sh", "-c", "java -jar app.jar"]