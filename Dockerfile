# Base Image
FROM eclipse-temurin:21-jre-jammy

# Arbeitsverzeichnis
WORKDIR /app

# JAR kopieren
COPY build/libs/*.jar app.jar

# Optional: Java Memory + Spring Profile
ENV JAVA_OPTS="-Xmx512m"
ENV SPRING_PROFILES_ACTIVE=prod

# Startbefehl
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]