# Multi-stage build for Spring Boot app
FROM maven:3.9.8-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -DskipTests clean package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/collab-workspace.jar ./app.jar

EXPOSE 8080
CMD ["java", "-jar", "/app/app.jar"]
