FROM openjdk:17-jdk
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport"
ENV JDK_JAVA_OPTIONS="--add-modules java.se --add-exports java.base/jdk.internal.ref=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.management/sun.management=ALL-UNNAMED --add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED --add-opens jdk.management/com.ibm.lang.management.internal=ALL-UNNAMED --add-opens java.base/jdk.internal.misc=ALL-UNNAMED"

COPY ./backend/target/*-runner.jar /app.jar
EXPOSE 8080

CMD ["java", "-jar", "/app.jar"]
