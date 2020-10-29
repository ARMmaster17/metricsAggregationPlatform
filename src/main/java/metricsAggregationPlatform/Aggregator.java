package metricsAggregationPlatform;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.WindowStore;

import java.time.Duration;

class Aggregator {

    // Size of aggregation windows in minutes.
    private final static int TIME_WINDOW_SIZE = 1;
    // How long to maintain a time window.
    private final static long WINDOW_MAINTAIN_DURATION = 24 * 60 * 60 * 1000L;

    /**
     * Count aggregation function
     * @param kStream The kStream to perform the aggregation upon
     * @param AGG_NAME the name field under agg in the YAML file.
     *                 This is also the topic name & the string that differentiates the keys
     * @return the final aggregated kTable
     */
    public KTable<Windowed<String>, Long> count(KStream<String, String> kStream,
                                                        String AGG_NAME) {
        String starterCheck = AGG_NAME.split("-")[0];
        //Group the Stream based on the key
        return kStream.groupBy((key, value) -> key.startsWith(starterCheck) ? key : null)
                .windowedBy(TimeWindows.of(Duration.ofMinutes(TIME_WINDOW_SIZE)).until(WINDOW_MAINTAIN_DURATION))
                .count();
        // TODO: Add Suppress() filter to not release window until it is complete. Note that this
        // filter will cause Serdes issues at runtime.
    }

    /**
     * Sum aggregation function
     * @param kStream The kStream to perform the aggregation upon
     * @param AGG_NAME the name field under agg in the YAML file.
     *                 This is also the topic name & the string that differentiates the keys
     * @param MAIN_ACTION_FIELD the field which we want to add up
     * @return the final aggregated kTable
     */
    public KTable<Windowed<String>, Long> sum(KStream<String, String> kStream,
                                                      String AGG_NAME,
                                                      String MAIN_ACTION_FIELD) {
        String starterCheck = AGG_NAME.split("-")[0];
        return kStream.groupBy((key, value) -> key.startsWith(starterCheck) ? key : null, Grouped.with(Serdes.String(), Serdes.String()))
                .windowedBy(TimeWindows.of(Duration.ofMinutes(TIME_WINDOW_SIZE)).until(WINDOW_MAINTAIN_DURATION))
                .aggregate(
                    new Initializer<Long>() {
                        @Override
                        public Long apply() {
                            return 0L;
                        }
                    },
                    new org.apache.kafka.streams.kstream.Aggregator<String, String, Long>() {
                        @Override
                        public Long apply(String key, String value, Long aggregate) {
                            JsonObject jsonObject = new JsonParser().parse(value).getAsJsonObject();
                            if (jsonObject.has(MAIN_ACTION_FIELD)) {
                                System.out.println(value);
                                try {
                                    return aggregate + jsonObject.get(MAIN_ACTION_FIELD).getAsLong();
                                } catch (ClassCastException e) {
                                    // TODO: Log in some way that we received a malformed value in the logs.
                                    return aggregate;
                                }
                            } else {
                                return aggregate;
                            }
                        }
                    },
                    Materialized.<String, Long, WindowStore<Bytes, byte[]>>as(AGG_NAME).withKeySerde(Serdes.String()).withValueSerde(Serdes.Long()));
        // TODO: Add Suppress() filter to not release window until it is complete. Note that this
        // filter will cause Serdes issues at runtime.
    }

    /**
     * Average aggregation function
     * @param kStream The kStream to perform the aggregation upon
     * @param AGG_NAME the name field under agg in the YAML file.
     *                 This is also the topic name & the string that differentiates the keys
     * @param MAIN_ACTION_FIELD the field which we want to average up
     * @return the final aggregated kTable
     */
    KTable<Windowed<String>, Double> mean(KStream<String, String> kStream,
                                          String AGG_NAME,
                                          String MAIN_ACTION_FIELD) {
        KTable<Windowed<String>, Long> sumTable
                = sum(kStream, AGG_NAME+"-sum", MAIN_ACTION_FIELD);
        KTable<Windowed<String>, Long> countTable
                = count(kStream, AGG_NAME+"-count");
        return sumTable.leftJoin(countTable,
                (Long sum, Long count) -> ((sum != null) && (count != null)) ? (sum.doubleValue() / count.doubleValue()) : null);
    }

}
