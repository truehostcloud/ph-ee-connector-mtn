
FROM openjdk:11 AS build


RUN apt-get update -qq && apt-get install -y wget
COPY  . ph-ee-connector-mtn

WORKDIR /ph-ee-connector-mtn
RUN ./gradlew clean build

FROM openjdk:11

EXPOSE 5000

RUN mkdir /app

COPY --from=build /ph-ee-connector-mtn/build/libs/*.jar /app/

ENTRYPOINT ["java", "-jar" ,"/app/ph-ee-connector-mtn.jar"]