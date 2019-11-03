FROM arjones/graalvm:1.0.0-rc1
ADD target/scala-2.12/mongo-elastic-connector-assembly-1.0.jar app.jar
ENV JAVA_OPTS="-Dconfig.resource=application-docker.conf"
ENTRYPOINT exec java $JAVA_OPTS  -jar /app.jar