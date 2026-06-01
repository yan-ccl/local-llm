FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre

ENV JAVA_OPTS="" \
    SERVER_PORT=8000

WORKDIR /app
COPY --from=build /src/target/correction-app-java-0.1.0.jar /app/app.jar

EXPOSE 8000
CMD ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
