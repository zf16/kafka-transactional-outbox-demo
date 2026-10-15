FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY flight-service/pom.xml flight-service/pom.xml
COPY event-processing-service/pom.xml event-processing-service/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml
RUN mvn -q -pl flight-service -am dependency:go-offline
COPY flight-service/src flight-service/src
RUN mvn -q -pl flight-service -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/flight-service/target/flight-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
