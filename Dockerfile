# syntax=docker/dockerfile:1

# ---- build: compile with the Gradle wrapper and split the boot jar into layers ----
# Runs on the build machine's own platform: the jar is the same for every target, so a multi-arch
# build (--platform linux/amd64,linux/arm64) doesn't run Gradle under emulation.
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS build
WORKDIR /src

# Dependencies first, so code changes don't re-download them.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null

COPY src src
RUN ./gradlew --no-daemon bootJar \
	&& java -Djarmode=tools -jar build/libs/givenergy-0.0.1-SNAPSHOT.jar extract --layers --launcher --destination /layers

# ---- run: JRE only, non-root ----
# No RUN steps here, so other architectures build without QEMU.
FROM eclipse-temurin:21-jre
# Logs in local time; day boundaries and the midnight jobs use givenergy.timezone either way.
ENV TZ=Europe/London
WORKDIR /app

# Least- to most-often changed, so rebuilds reuse the dependency layers.
COPY --from=build /layers/dependencies/ ./
COPY --from=build /layers/spring-boot-loader/ ./
COPY --from=build /layers/snapshot-dependencies/ ./
COPY --from=build /layers/application/ ./

# A numeric non-root user; it needs no passwd entry, so no useradd step.
USER 10001:10001
EXPOSE 8082
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
