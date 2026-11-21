FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system campusops && mkdir -p /app/data && chown campusops /app/data
COPY --from=build /src/target/campusops-0.0.1-SNAPSHOT.jar app.jar
USER campusops
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
