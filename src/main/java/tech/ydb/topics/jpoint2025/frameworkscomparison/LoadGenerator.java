package tech.ydb.topics.jpoint2025.frameworkscomparison;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class LoadGenerator {

    public static final String SOURCE_TOPIC = "eos-tests-source-topic";
    private static final int MESSAGE_SIZE_BYTES = 1024; // 1 kb
//    private static final long EXPECTED_BYTES_IN_TOPIC = 10L * 1024;
    private static final long EXPECTED_BYTES_IN_TOPIC = 10L * 1024 * 1024 * 1024; // 10 Gb

    public static void main(String[] args) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);

        KafkaProducer<byte[], byte[]> producer = new KafkaProducer<>(config);

        Random random = new Random();
        for (int i = 0; i < EXPECTED_BYTES_IN_TOPIC / MESSAGE_SIZE_BYTES; i++) {
            byte[] key = createPayload(i, MESSAGE_SIZE_BYTES / 2, random);
            byte[] value = createPayload(i, MESSAGE_SIZE_BYTES / 2, random);
            producer.send(new ProducerRecord<>(SOURCE_TOPIC, key, value));

            if (i % 10_000 == 0) {
                System.out.println("Sent " + i + " records");
            }
        }

        producer.flush();
    }

    private static byte[] createPayload(long prefix, int sizeBytes, Random random) {
        String prefixStr = prefix + "";
        if (prefixStr.getBytes().length > sizeBytes) {
            throw new RuntimeException("Too small message size");
        }

        // generate random payload
        byte[] payload = new byte[sizeBytes];
        System.arraycopy(prefixStr.getBytes(), 0, payload, 0, prefixStr.getBytes().length);

        for (int i = prefixStr.getBytes().length; i < payload.length; i++) {
            payload[i] = (byte) (random.nextInt(26) + 65);
        }

        return payload;
    }
}
