FROM gradle:4.1 as builder
WORKDIR /tmp
USER root

FROM maven:3.6.3-jdk-8
LABEL maintainer="Anadi Jaggia, Joshua Zenn"

ENV JAVA_OPTS="-Xmx1G -Xms1G"
ENV NUM_THREADS 4

ENTRYPOINT ["/usr/bin/mvn compile exec:java -Dexec.mainClass=org.cox.map.MetricsAggregator -Dexec.args=\"--inputFile=pom.xml --output=map\" -Pdirect-runner"]