package org.cox.map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.beam.sdk.io.kafka.KafkaRecord;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.values.KV;

public class MapGlobalTablesFn extends DoFn<KafkaRecord<String, String>, KV<String, String>> {

    private String groupKey;
    private String groupValue;

    public MapGlobalTablesFn(String groupKey, String groupValue) {
        super();
        this.groupKey = groupKey;
        this.groupValue = groupValue;
    }

    @ProcessElement
    public void processElement(@Element KafkaRecord<String, String> input, OutputReceiver<KV<String, String>> out) {
        KV<String, String> rawInput = input.getKV();
        JsonParser parser = new JsonParser();
        JsonObject obj = parser.parse(rawInput.getValue()).getAsJsonObject();
        out.output(KV.of(obj.get(groupKey).getAsString(), obj.get(groupValue).getAsString()));
    }
}
