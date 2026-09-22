FROM eclipse-temurin:25-jre
WORKDIR /app
COPY build/libs/property-platform.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]