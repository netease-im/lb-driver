

export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
export JAVA="$JAVA_HOME/bin/java"

export APPLICATION_YML=/xxx/xxx/application.yml
export LOG_FILE=/xxx/xxx/logback.xml

export JAR_FILE=/xxx/xx/lb-driver-config-server-bootstrap-x.x.x-jar

nohup "$JAVA" -XX:+UseG1GC --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED --add-opens java.base/java.math=ALL-UNNAMED --add-opens java.base/java.net=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/java.security=ALL-UNNAMED --add-opens java.base/java.text=ALL-UNNAMED --add-opens java.base/java.time=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/jdk.internal.access=ALL-UNNAMED --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/sun.net.util=ALL-UNNAMED -Xms2g -Xmx2g -Dlogging.config=file:$LOG_FILE -jar -server $JAR_FILE --spring.config.location=file:$APPLICATION_YML  2>&1 &