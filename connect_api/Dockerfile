FROM confluentinc/cp-kafka:6.0.0

RUN wget http://client.hub.confluent.io/confluent-hub-client-latest.tar.gz && \
    tar -xvzf confluent-hub-client-latest.tar.gz -C /tmp/
RUN /tmp/confluent-hub install --no-prompt confluentinc/kafka-connect-influxdb:1.2.1