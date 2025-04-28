# Step 1: Build the application using Maven with JDK 21
FROM maven:3.9.6-eclipse-temurin-21 AS build

# Set working directory inside the container
WORKDIR /app

# Copy Maven wrapper and project files
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download Maven dependencies (cache layers!)
RUN ./mvnw dependency:go-offline

# Copy the full source code
COPY src src

# Build the application
RUN ./mvnw clean package -DskipTests

# Step 2: Create a small runtime image using JDK 21
FROM amazoncorretto:21-alpine

# Copy the JAR file from the build stage
COPY --from=build /app/target/*.jar /app.jar

# Expose your app port (8081 according to your properties)
EXPOSE 8081

# Run the JAR file
CMD ["java", "-jar", "/app.jar"]
