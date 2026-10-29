FROM eclipse-temurin:17-jdk AS build

WORKDIR /workspace

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY app app
COPY common common
COPY account account
COPY transfer transfer
COPY ledger ledger
COPY notification notification

RUN chmod +x gradlew
RUN ./gradlew :app:bootJar --no-daemon

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /workspace/app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
