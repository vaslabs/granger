FROM hseeberger/scala-sbt

ADD . /root
WORKDIR /root

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "target/scala-2.12/granger_2.12-1.0.jar"]