# Build the React frontend first.
FROM node:22-bookworm-slim AS frontend-build
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# Package the frontend into Spring Boot's static resources.
FROM maven:3.9.11-eclipse-temurin-21 AS app-build
WORKDIR /workspace/backend
COPY backend/pom.xml ./
COPY backend/src ./src
COPY --from=frontend-build /workspace/frontend/dist ./src/main/resources/static
RUN mvn -B -ntp -DskipTests package

# One web service serves both the UI and /api from the same origin.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=app-build /workspace/backend/target/student-management-0.1.0.jar ./app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60.0 -XX:+UseSerialGC -Dfile.encoding=UTF-8"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
