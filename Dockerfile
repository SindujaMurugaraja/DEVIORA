FROM eclipse-temurin:21-jdk

WORKDIR /app

RUN mkdir -p dataset lib web src

COPY DEVIORA.java .
COPY weka.jar lib/weka.jar
COPY DEVIORA_engine_dataset.csv dataset/DEVIORA_engine_dataset.csv
COPY index.html style.css script.js web/

RUN javac --add-modules jdk.httpserver -cp "lib/weka.jar" -d src DEVIORA.java

CMD ["sh", "-c", "java --add-modules jdk.httpserver --add-opens java.base/java.lang=ALL-UNNAMED -Djava.awt.headless=true -cp 'lib/weka.jar:src' DEVIORA"]
