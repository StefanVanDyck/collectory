# Use an official Maven runtime as a parent image
FROM gradle:7.6.3-jdk11 as builder

# Set the working directory in the container
WORKDIR /app

# Copy the Gradle build files and settings to the container
COPY .git .
COPY build.gradle .
COPY settings.gradle .
COPY gradle.properties .

# Copy the source code of the Grails application to the container
COPY src/ src/
COPY gradle/ gradle/
COPY grails-app/ grails-app/
COPY db/ db/

# Build the Grails application using Gradle
RUN gradle assemble

FROM livingatlases/java-11-base:1.0.0

# Lint with:
# docker run --rm -i hadolint/hadolint < Dockerfile

# Args
ARG APP_ARTIFACT=collectory
ARG USER=collectory
ARG WAR_FILE=/app/build/libs/collectory-*.war
ENV MYSQL_HOST=collectory-mysql
ARG DOCKERIZE_ARGS="-wait tcp://$MYSQL_HOST:3306 -timeout 120s"

# Env variables
ENV SERVER_PORT="8080"
ENV WAR_FILE_ENV=${WAR_FILE}
ENV JAVA_OPTS="-Djava.awt.headless=true -Xmx256m -Xms256m -XX:+UseConcMarkSweepGC -Dlog4j2.formatMsgNoLookups=true"
ENV DOCKERIZE_OPTS=${DOCKERIZE_ARGS}

# Directories and perms
RUN mkdir -p /data/$APP_ARTIFACT && \
    groupadd -r $USER -g 1000 && useradd -r -g $USER -u 1000 -m $USER && \
    chown -R $USER:$USER /data/$APP_ARTIFACT
WORKDIR /opt/atlas/$APP_ARTIFACT

# war
COPY --from=builder $WAR_FILE_ENV /opt/atlas/$APP_ARTIFACT/app.war
RUN chown -R $USER:$USER /opt/atlas/$APP_ARTIFACT

USER $USER
EXPOSE $SERVER_PORT
# hadolint ignore=DL3025
ENTRYPOINT dockerize $DOCKERIZE_OPTS sh -c "java $JAVA_OPTS -jar app.war"
