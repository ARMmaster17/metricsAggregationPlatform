FROM gradle:4.1 as builder
COPY . /
WORKDIR /
USER root

FROM openjdk:9
LABEL maintainer="Anadi Jaggia, Joshua Zenn"

ENV JAVA_OPTS="-Xmx1G -Xms1G"
ENV NUM_THREADS 4
RUN mkdir -p /etc/jaggia

ADD dockerEntryPoint.sh /dockerEntryPoint.sh
ENTRYPOINT ["mvn compile exec:java -Dexec.mainClass=org.cox.map.MetricsAggregator -Dexec.args=\"--inputFile=pom.xml --output=map\" -Pdirect-runner"]