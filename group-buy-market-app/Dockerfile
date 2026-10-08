FROM eclipse-temurin:8-jre

WORKDIR /app
COPY target/group-buy-market-app.jar /app/group-buy-market-app.jar

ENV JAVA_OPTS="" PARAMS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/group-buy-market-app.jar $PARAMS"]
