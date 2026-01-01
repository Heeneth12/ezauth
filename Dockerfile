# 1️⃣ BUILD STAGE (Java 21)
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

# Copy pom.xml first (cache)
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source
COPY src ./src

# Build
RUN mvn clean package -DskipTests


# 2️⃣ RUN STAGE (Java 21)
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy jar
COPY --from=build /app/target/*.jar app.jar

# Render dynamic port
EXPOSE 8080

# Run app
ENTRYPOINT ["java","-jar","app.jar"]
