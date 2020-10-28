package metricsAggregationPlatform;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.WindowStore;

import java.time.Duration;

import static org.apache.kafka.streams.kstream.Suppressed.BufferConfig.unbounded;

class Aggregator {

    /**
     * Count aggregation function
     * @param kStream The kStream to perform the aggregation upon
     * @param AGG_NAME the name field under agg in the YAML file.
     *                 This is also the topic name & the string that differentiates the keys
     * @return the final aggregated kTable
     */
    KTable<Windowed<String>, Long> count(KStream<String, String> kStream,
                                                        String AGG_NAME) {
        String starterCheck = AGG_NAME.split("-")[0];
        //Group the Stream based on the key
        return kStream.groupBy((key, value) -> key.startsWith(starterCheck) ? key : null)
                .windowedBy(TimeWindows.of(60 * 1000L).until(24 * 60 * 60 * 1000L))
                .count();
    }

    /**
     * Sum aggregation function
     * @param kStream The kStream to perform the aggregation upon
     * @param AGG_NAME the name field under agg in the YAML file.
     *                 This is also the topic name & the string that differentiates the keys
     * @param MAIN_ACTION_FIELD the field which we want to add up
     * @return the final aggregated kTable
     */
    KTable<Windowed<String>, Long> sum(KStream<String, String> kStream,
                                                      String AGG_NAME,
                                                      String MAIN_ACTION_FIELD) {
        String starterCheck = AGG_NAME.split("-")[0];
        return kStream.groupBy((key, value) -> key.startsWith(starterCheck) ? key : null, Grouped.with(Serdes.String(), Serdes.String()))
                .windowedBy(TimeWindows.of(60 * 1000L).until(24 * 60 * 60 * 1000L))
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
                                    return aggregate;
                                }
                            } else {
                                return aggregate;
                            }
                        }
                    },
                    Materialized.<String, Long, WindowStore<Bytes, byte[]>>as(AGG_NAME).withKeySerde(Serdes.String()).withValueSerde(Serdes.Long()));
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
