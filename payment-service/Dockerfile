FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY flight-service/pom.xml flight-service/pom.xml
COPY event-processing-service/pom.xml event-processing-service/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml
RUN mvn -q -pl payment-service -am dependency:go-offline
COPY payment-service/src payment-service/src
RUN mvn -q -pl payment-service -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/payment-service/target/payment-service-*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
