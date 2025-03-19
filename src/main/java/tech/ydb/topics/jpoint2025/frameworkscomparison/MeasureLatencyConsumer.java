package tech.ydb.topics.jpoint2025.frameworkscomparison;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MeasureLatencyConsumer {
    private static final int MESSAGES_TO_READ = 10_000_000;
    public static final String TOPIC = FlinkTest.TARGET_TOPIC;


    public static void main(String[] args) {
        // launch MeasureLatencyConsumer before launching streaming app
        // read topic, for each message:
        // 1. extract ts from record value - this is processingStartTs
        // 2. subtract processingStartTs from currentTimeMs - we get processing latency
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "my-test-consumer-for-flink");

        long[] latencies = new long[MESSAGES_TO_READ];

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(Collections.singleton(TOPIC));

            int messagesRead = 0;
            while (messagesRead < MESSAGES_TO_READ) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(100))) {
                    if (messagesRead >= latencies.length) {
                        break;
                    }
                    long now = System.currentTimeMillis();
                    long processingStartTs = Long.parseLong(record.value().substring(0, (now + "").length()));
                    latencies[messagesRead] = now - processingStartTs;
                    messagesRead++;
                }
            }
        }

        printResults(latencies);
    }

    private static void printResults(long[] latencies) {
        long[] percentiles = percentiles(latencies, 0.5, 0.95, 0.99, 0.999);
        System.out.printf("p50 - %d, p95 - %d, p99 - %d, p999 - %d%n",
                percentiles[0],
                percentiles[1],
                percentiles[2],
                percentiles[3]
        );
    }

    private static long[] percentiles(long[] latencies, double... percentiles) {
        Arrays.sort(latencies);
        long[] values = new long[percentiles.length];
        for (int i = 0; i < percentiles.length; i++) {
            int index = (int) (percentiles[i] * latencies.length);
            values[i] = latencies[index];
        }
        return values;
    }
}
