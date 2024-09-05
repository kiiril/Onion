# Stage 1: Build the Maven project
FROM maven:3.8.3-openjdk-11 AS builder

WORKDIR /usr/src/myapp

# Copy only the POM file to cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy the rest of the source code and build the application
COPY src src
RUN mvn package

# Stage 2: Create a smaller image for runtime
FROM openjdk:11-jre-slim

WORKDIR /usr/src/myapp

# Copy the compiled artifacts and dependencies from the builder stage
COPY --from=builder /usr/src/myapp/target/Onion-1.0-SNAPSHOT.jar .
COPY --from=builder /usr/src/myapp/target/dependency/* ./lib/

# Specify the command to run your application
CMD ["java", "-cp", ".:Onion-1.0-SNAPSHOT.jar:lib/*", "com.github.kiiril.Main"]


