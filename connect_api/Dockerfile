FROM confluentinc/cp-kafka:6.0.0

USER root

RUN yum update -y && \
    yum install -y curl ca-certificates

RUN mkdir -p /opt/confluent-hub-client && \
    curl "http://client.hub.confluent.io/confluent-hub-client-latest.tar.gz" \
    | tar -xvz -C /opt/confluent-hub-client

ENV PATH="/opt/confluent-hub-client/bin:${PATH}"

RUN confluent-hub install --component-dir /usr/share/java/kafka/ --no-prompt confluentinc/kafka-connect-influxdb:1.2.1