package org.cox.map;

import com.google.gson.JsonObject;
import org.apache.beam.sdk.io.kafka.KafkaRecord;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.values.KV;
import com.google.gson.JsonParser;
import org.joda.time.Instant;

import java.util.List;

public class MapKeyTagsFn extends DoFn<KafkaRecord<String, String>, KV<String, Long>> {
    private String aggName;
    private List<String> tags;
    private String actionField;

    public MapKeyTagsFn(String aggName, String actionField, List<String> tags)
    {
        super();
        this.aggName = aggName;
        this.tags = tags;
        this.actionField = actionField;;
    }

    @ProcessElement
    public void processElement(@DoFn.Element KafkaRecord<String, String> input, @Timestamp Instant timestamp, OutputReceiver<KV<String, Long>> out) {
        JsonParser jsonParser = new JsonParser();
        String value = input.getKV().getValue();
        JsonObject jsonValue = jsonParser.parse(value).getAsJsonObject();
        StringBuilder newKey = new StringBuilder(/*aggName*/);
        for (String keyName : tags) {
            if (jsonValue.has(keyName) && jsonValue.has(actionField)) {
                newKey/*.append(",")*/.append(keyName).append("=").append(jsonValue.get(keyName)).append(",");
            } else {
                // Entry does not have the tags we are looking for. Remove it.
                return;
            }
        }
        // TODO: May need to write custom timestamp serializer.
        String actionValue = jsonValue.get(actionField).getAsString();

        out.outputWithTimestamp(KV.of(newKey.toString(), Long.parseLong(actionValue)), timestamp);
    }
}