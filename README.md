# metricsAggregationPlatform

## Branch descriptions
`master` - A complete mess right now. Ignore.

`poc-beam` - An attempt to port the project to use Apache Beam + Apache Flink. Most promising branch at the moment. Nearly complete.

`wip-connect` - An attempt to port the project to use Kafka Connect. Very broken. May require more 3rd party tools (schema registry).

`wip-stash` - An attempt to modify as little as possible of the original project. Currently, has schema issues with Kafka.

## Why Apache Beam+Flink?

Apache Beam+Flink has several advantages:
- Extremely fast implementation. Most of the initial code for `poc-beam` was written in under two days.
- The 2020 big data community reccomends Flink or Storm over raw Kafka approaches to stream data. (Beam+Flink also replaces traditional Apache spark for stream/ML).
- Several sources *claim* an 80-93% performance increase over Kafka Connect API in aggregation tests with bounded datasets (as of April 2020).
- Theoretically infinitely scalable independent of Kafka configuration (also fluid enough to allow for auto-scaling).
- Simple implementation in under 140 LLOC (XX% reduction).
- Good documentation, and easy to understand SDK (DSL written specifically for streaming workloads).
- Minimal overhead from added abstraction layers (Approximately 1 GB per instance running in Direct runner mode).
- Processing is done within the Flink cluster, reducing the number of round-trips between Kafka and the application.
- Unit/Integration testing is much easier with mockable sources/sinks.

Known drawbacks:
- Apache Beam is newer, so it is more difficult to find the documentation/tutorials outside the official docs (which are very well written). The official docs are, however, much better than the Kafka SDK docs.
- Although Flink supports inter-cluster failover, total loss of the Flink cluster will result in all data in-transit being lost. The amount of data that the Flink cluster can ingest and proccess at once increases the amount of data that can be lost in a critical failure. (Carefully re-winding offsets, using global tables, and using Flink runners instead of Direct runners can reduce this risk).
- A few older sources *claim* that the Kafka I/O pipeline for Beam does not fully implement true "only once processing". This appears to be outdated information, and only applies when using the Direct runner.
- Autonomous scaling will require an external orchestration service.

## Summary

This app aggregates all your kafka logs and ships the results to influxDB.
The points are then visualized using grafana.

As long as your logs ship from Kafka in JSON format, this app will aggregate it :)

Create an output topic as well (to match the output topic from the configuration settings below)
The topic name has to be the same as all the name fields in the YAML config file. In the example below,
there will be 4 topics named phn_to_cachegroup,cachegroup_to_cdn, count_pssc, and sum_b.
```
bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 16 --config retention.ms=3600000 --config cleanup.policy=compact --config segment.ms=300000 --topic TOPICNAME
```

cd into the metricsAggregationPlatform directory and run the following commands to initialize the needed
docker containers in this order.

```
docker-compose up -d influx grafana kibana
docker-compose up metricsaggregationplatform
```
Scaling is currently not supported, but can easily be added by switching to the Flink runner instead of the Direct runner.

## Currently working
Pipeline that performs all extractions and transformations.

## Not working
- Docker wrapper
- Flink runner (using Direct runner at the moment)

![ETL Flowchart](https://github.com/ARMmaster17/metricsAggregationPlatform/blob/poc-beam/doc/img/etl.png?raw=true)


Sample YAML config file.
```
kafka:
  input:
    bootstrap_servers: kafka01
    input_topic: myKafkaTopic
    app_id: myID

influxdb:
  connection : myInfluxLink
  db_name : Database
  rp_name : autogen

elastic:
  hostname : elasticHost
  scheme : http
  port : 9200
  index : snafu
  batchSize : 100000

interval:
  query_interval0: 60000 # 60 * 1000 - one minute
  query_interval0_from: 600000 # 10 * 60 * 1000 - ten minutes
  chunk: 3600000 # 60 * 60 * 1000 - one hour
  snapshot_time: 86400000 # 24 * 60 * 60 * 1000 - twenty-four hours
  event_count_interval: 10000 # 10 * 1000 - ten seconds

global_tables:
  -
   name: phn_to_cachegroup
   key: phn
   value: cachegroup
  -
   name: cachegroup_to_cdn
   key: cachegroup
   value: cdn

agg:

  -
   name: count_pssc
   action: count
   action_field: pssc
   tags:
     - pssc
     - phn
     - shn
   group_by:
     - phn_to_cachegroup
     - cachegroup_to_cdn

  -
   name: count_crc
   action: count
   action_field: crc
   tags:
     - crc
     - phn
     - shn
   group_by:
     - phn_to_cachegroup
     - cachegroup_to_cdn
  -
   name: sum_b
   action: sum
   action_field: b
   tags:
     - phn
     - shn
   group_by:
     - phn_to_cachegroup
     - cachegroup_to_cdn
```
A guide to this configuration :

```
kafka:
    input: All the kafka connection configs
    output: All the kafka connection configs

influxdb: All the kafka connection configs
elastic: All the elastic connection configs

interval:
    At specified periods of time(query_interval0 and query_interval1), the app will query the logs.
    The time range it queries is from now to (now - the number of milliseconds as per query_interval0_from and query_interval1_from)
    if running the snapshot_jar, the 'chunk' and 'snapshot_time' fields are important. If for example, you'd like to query
    the last 24 hours, snapshot_time would be 86400000. The chunk field determines how to divide the snapshot_time. if you say
    chunk is 3600000, then the snapshot of the past 24 hours (86400000 ms), would be queried in chunks of
    1 hour (3600000 ms).
    event_count_interval : the period of how often we send the number of messages since the app started to influxDB
  In the example above, every one minute (query_interval0) the app will query logs from 10 minutes ago (query_interval0_from).

global_tables:
    name: the name of this aggregation and the name of the measurement in influxDB. Also serves
            as the topic name for kafka when you create them as per the instructions above.
    key : the key of the global table. this will be taken from the JSON log
    value :  the value of the global table. this will be taken from the JSON log

agg:
  name: the name of this aggregation and the name of the measurement in influxDB. Also serves
  as the topic name for kafka when you create them as per the instructions above.
  action: either sum or count.
  action_field: the JSON key to perform the aggregation on. eg - pssc or b (bytes)
  tags : the JSON keys you want to filter from the raw JSON log (must include action field as well)
  group_by : the names of the global_tables above. This is appended to the results of the filtering of the tags above.
```

All the above configs are required except for influxdb and elastic configs.
You can have either one, both or none. The choice of the DB to write to is up to you.
