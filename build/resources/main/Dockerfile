FROM gradle:9-jdk25 AS build
WORKDIR /home/gradle/src

# Copy Gradle wrapper + build scripts first for better layer caching
COPY --chown=gradle:gradle gradlew settings.gradle build.gradle /home/gradle/src/
COPY --chown=gradle:gradle gradle /home/gradle/src/gradle
RUN ./gradlew --no-daemon dependencies

# Now copy sources and build the Spring Boot executable JAR
COPY --chown=gradle:gradle . /home/gradle/src
RUN ./gradlew --no-daemon clean bootJar

LABEL org.name="MPro3788"
#
# Package stage
#
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
COPY --from=build /home/gradle/src/build/libs/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]