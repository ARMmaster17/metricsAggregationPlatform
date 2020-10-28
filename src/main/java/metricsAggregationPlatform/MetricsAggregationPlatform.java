package metricsAggregationPlatform;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import static org.apache.kafka.streams.StreamsConfig.APPLICATION_ID_CONFIG;
import static org.apache.kafka.streams.StreamsConfig.BOOTSTRAP_SERVERS_CONFIG;
import static org.apache.kafka.streams.StreamsConfig.CLIENT_ID_CONFIG;
import static org.apache.kafka.streams.StreamsConfig.COMMIT_INTERVAL_MS_CONFIG;
import static org.apache.kafka.streams.StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG;
import static org.apache.kafka.streams.StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG;

public class MetricsAggregationPlatform {
    final static String LOCAL_MODE = "LOCAL_MODE";

    /**
     * Entry point for application.
     * @param args Arguments passed from command line.
     */
    public static void main(String[] args) throws FileNotFoundException {
        final Properties streamsConfiguration = loadConfig();
        //final Topology topology = generateTopology(streamsConfiguration);
        final StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> source = builder.stream("streams-plaintext-input");
        KStream<String, String> filteredSource = source.filter(((key, value) -> {
            return value.contains("pssc");
        }));
        final Topology topology = builder.build();
        final KafkaStreams streams = new KafkaStreams(topology, streamsConfiguration);

        final CountDownLatch latch = new CountDownLatch(1);

        // attach shutdown handler to catch control-c
        Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
            @Override
            public void run() {
                streams.close();
                latch.countDown();
            }
        });

        try {
            streams.start();
            latch.await();
        } catch (Throwable e) {
            System.exit(1);
        }
        System.exit(0);
    }

    /**
     * Generates a topology of streams to be processed through Kafka.
     * @param properties Active runtime configuration.
     * @return Generated topology.
     */
    private static Topology generateTopology(Properties properties) {
        final StreamsBuilder builder = new StreamsBuilder();

        builder.stream("input", Consumed.with(Serdes.String(), Serdes.String()))
                .map((k, v) -> new KeyValue<>((String)k, (String)v))
                .to("output", Produced.with(Serdes.String(), Serdes.String()));

        return builder.build();
    }

    /**
     * Loads all runtime configuration from environment and config files.
     * @return Packaged active configuration settings.
     */
    private static Properties loadConfig() {
        final Yaml yamlParser = new Yaml();
        final Properties properties = new Properties();
        InputStream inputFile = null;
        try {
            inputFile = new FileInputStream("/etc/jaggia/ymlConfig.yml");
        } catch (FileNotFoundException e) {
            System.out.println("ERROR: Configuration file not found.");
            return properties;
        }
        final Map<String, Object> globalConfig = (Map<String, Object>) yamlParser.load(inputFile);
        final Map<String, Map<String, String>> kafka_inputs = (Map<String, Map<String, String>>) globalConfig.get("kafka");
        properties.put(APPLICATION_ID_CONFIG, kafka_inputs.get("input").get("app_id"));
        properties.put(BOOTSTRAP_SERVERS_CONFIG, kafka_inputs.get("input").get("bootstrap_servers"));
        properties.put(DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.Integer().getClass().getName());
        properties.put(DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.Integer().getClass().getName());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(LOCAL_MODE, globalConfig.get("local_mode"));
        // Remove for production.
        properties.put(COMMIT_INTERVAL_MS_CONFIG, 10 * 1000);

        return properties;
    }

    enum AggregationMode {
        SUM,
        COUNT
    }
}
