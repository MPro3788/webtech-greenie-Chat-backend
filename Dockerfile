FROM gradle:9-jdk25 AS build
WORKDIR /home/gradle/src

COPY --chown=gradle:gradle gradlew settings.gradle build.gradle /home/gradle/src/
COPY --chown=gradle:gradle gradle /home/gradle/src/gradle
RUN ./gradlew --no-daemon dependencies

COPY --chown=gradle:gradle . /home/gradle/src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
COPY --from=build /home/gradle/src/build/libs/chat-0.0.1-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
