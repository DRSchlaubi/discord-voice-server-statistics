FROM eclipse-temurin:23-jre-alpine

WORKDIR /usr/app
COPY build/install/collector .

ENTRYPOINT ["/usr/app/bin/collector"]
