FROM maven:3.9-eclipse-temurin-21@sha256:99e61abcff91a9b1333463bd8451fb18495d6eba9250ac66a338b518f8278320 AS maven
FROM jenkins/jenkins:lts-jdk21@sha256:161ec15b027ea80911256552191034bf2aaede1aa39dd06ca1fef60d9b585be5
COPY --from=maven /usr/share/maven /opt/maven
ENV PATH="/opt/maven/bin:${PATH}"
COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt
COPY init.groovy /usr/share/jenkins/ref/init.groovy.d/lab.groovy
USER jenkins
