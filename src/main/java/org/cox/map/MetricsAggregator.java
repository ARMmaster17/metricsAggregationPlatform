package org.cox.map;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.kafka.KafkaIO;
import org.apache.beam.sdk.io.kafka.KafkaRecord;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.options.ValueProvider;
import org.apache.beam.sdk.transforms.Count;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.Sum;
import org.apache.beam.sdk.transforms.WithTimestamps;
import org.apache.beam.sdk.transforms.windowing.FixedWindows;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.yaml.snakeyaml.Yaml;
import org.apache.beam.sdk.io.influxdb.InfluxDbIO;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;

public class MetricsAggregator {
    public static void main(String[] args) {
        // Load configuration.
        Yaml yaml = new Yaml();
        Map<String, Object> appConfig = null;
        // TODO: Change back to /etc/jaggia/...
        try (InputStream in = new FileInputStream("etc/ymlConfig.yml")) {
            appConfig = (Map<String, Object>)yaml.load(in);
        }
        catch (IOException e)
        {
            System.out.println("ymlConfig.yml not found...");
        }
        // Build pipeline.
        PipelineOptions pipelineOptions = PipelineOptionsFactory.create();
        Pipeline pipeline = Pipeline.create(pipelineOptions);
        Map<String, Object> kafkaConfig = (Map<String, Object>) appConfig.get("kafka");
        Map<String, Object> kafkaInConfig = (Map<String, Object>) kafkaConfig.get("input");

        // 1. Data flows in from Kafka.
        PCollection<KafkaRecord<String, String>> input = pipeline.apply(KafkaIO.<String, String>read()
            .withBootstrapServers((String)kafkaInConfig.get("bootstrap_servers"))
            .withTopic((String)kafkaInConfig.get("input_topic"))
            .withKeyDeserializer(StringDeserializer.class)
            .withValueDeserializer(StringDeserializer.class)
            .withConsumerConfigUpdates(ImmutableMap.of("auto.offset.reset", "earliest"))
            .withConsumerConfigUpdates(ImmutableMap.of("group.id", "snafu"))
            .withConsumerConfigUpdates(ImmutableMap.of("enable.auto.commit", "true"))
            .commitOffsetsInFinalize());

        // 2. Create the aggregation pipelines. Start by tagging the keys.
        ArrayList<LinkedHashMap> aggregations = (ArrayList<LinkedHashMap>)appConfig.get("agg");
        // TODO: These can be statically allocated for better startup performance.
        Map<String, PCollection> taggedPreAggregations = new HashMap<>();

        for (LinkedHashMap agg : aggregations) {
            String aggName = (String)agg.get("name");
            String actionField = (String)agg.get("action_field");
            PCollection<KafkaRecord<String, String>> timestampedInput = input.apply(WithTimestamps.of((KafkaRecord<String, String> entry) -> {
                JsonParser jsonParser = new JsonParser();
                JsonObject jsonValue = jsonParser.parse(entry.getKV().getValue()).getAsJsonObject();
                Instant timestamp = new Instant(jsonValue.get("@timestamp").getAsString());
                return timestamp;
            }).withAllowedTimestampSkew(Duration.standardDays(365)));
            PCollection<KV<String, Long>> taggedKeys = timestampedInput.apply(ParDo.of(new MapKeyTagsFn(aggName, actionField, (ArrayList<String>)agg.get("tags"))));
            taggedPreAggregations.put(aggName, taggedKeys);
        }

        // 3. Window the result aggregations.
        Map<String, PCollection> windowedAggregations = new HashMap<>();
        for (Map.Entry<String, PCollection> taggedAggregation : taggedPreAggregations.entrySet())
        {
            PCollection<KV<String, Long>> windowedAggregation = (PCollection<KV<String, Long>>)taggedAggregation.getValue().apply(Window.into(FixedWindows.of(Duration.standardMinutes(1))));
            windowedAggregations.put(taggedAggregation.getKey(), windowedAggregation);
        }

        // 4. Perform final aggregation (sum, count, or average).
        Map<String, PCollection> finalAggregations = new HashMap<>();
        for (LinkedHashMap agg : aggregations) {
            PCollection<KV<String, Long>> result;
            String MAIN_ACTION = (String)agg.get("action");
            String aggName = (String)agg.get("name");
            switch (MAIN_ACTION) {
                case "count":
                    result = (PCollection<KV<String, Long>>)windowedAggregations.get(aggName).apply(Count.perKey());
                    break;
                case "sum":
                    result = (PCollection<KV<String, Long>>)windowedAggregations.get(aggName).apply(Sum.longsPerKey());
                    break;
                case "mean":
                    // TODO: Implement mean aggregation function.
                    return;
                default:
                    // TODO: Throw an error about a malformed YAML file.
                    return;
            }
            finalAggregations.put(aggName, result);
        }
        // 5. Send to InfluxDB.
        Map<String, Object> influxDbConfig = (Map<String, Object>) appConfig.get("influxdb");
        Map<String, PCollection> outputMappedAggregations = new HashMap<>();
        for (Map.Entry<String, PCollection> finalAggregation : finalAggregations.entrySet()) {
            PCollection<String> aggregation = (PCollection<String>)finalAggregation.getValue().apply(ParDo.of(new MapInfluxDBOutputFn((String)influxDbConfig.get("rp_name"), finalAggregation.getKey())));
            outputMappedAggregations.put(finalAggregation.getKey(), aggregation);
        }
        for (Map.Entry<String, PCollection> outputMappedAggregation : outputMappedAggregations.entrySet()) {
            PCollection<String> aggregation = outputMappedAggregation.getValue();
            aggregation.apply(InfluxDbIO.write()
                .withDataSourceConfiguration(InfluxDbIO.DataSourceConfiguration.create(
                        ValueProvider.StaticValueProvider.of((String)influxDbConfig.get("connection")),
                        ValueProvider.StaticValueProvider.of("username"),
                        ValueProvider.StaticValueProvider.of("password")
                        )).withDatabase((String)influxDbConfig.get("db_name")));
        }
        pipeline.run().waitUntilFinish();
    }
}
