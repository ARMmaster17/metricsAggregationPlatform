package org.cox.map;

import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.values.KV;
import org.joda.time.Instant;

public class MapInfluxDBOutputFn extends DoFn<KV<String, Long>, String> {
    private String measurement;
    private String field;

    public MapInfluxDBOutputFn(String measurement, String field) {
        this.measurement = measurement;
        this.field = field;
    }

    @ProcessElement
    public void processElement(@Element KV<String, Long> input, @Timestamp Instant timestamp, OutputReceiver<String> out) {
        // Remove trailing comma, if it exists in the key.
        String tags = input.getKey().replaceAll(",$", "");
        Long data = input.getValue();
        String result = measurement + "," + tags + " " + field + "=" + Long.toString(data) + " " + Long.toString(timestamp.getMillis() * 1000L);
        out.output(result);
    }
}
