package tech.ydb.topics.jpoint2025.frameworkscomparison;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.ProcessorSupplier;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public class KafkaStreamsTest {
    public static final String STATE_STORE_NAME = "state-store";
    public static final String TARGET_TOPIC = "eos-tests-streams-target";

    public static void main(String[] args) {
        // launch first time to populate changelog topic
        // erase data in topic
        // launch second time to measure startup latency on changelog restoration
        StreamsBuilder builder = new StreamsBuilder();

        builder.stream(LoadGenerator.SOURCE_TOPIC, Consumed.with(new Serdes.StringSerde(), new Serdes.StringSerde()))
                .process(new AddTsAndStoreProcessorSupplier(), STATE_STORE_NAME)
                .to(TARGET_TOPIC);

        Topology topology = builder.build();

        Map<String, String> props = new HashMap<>();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "my-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.EXACTLY_ONCE_V2);
        props.put(StreamsConfig.STATE_DIR_CONFIG, "streams-store");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class.getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.StringSerde.class.getName());
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, "100");
        try (KafkaStreams streams = new KafkaStreams(topology, new StreamsConfig(props))) {

            var latch = new CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread("shutdown-hook") {
                @Override
                public void run() {
                    streams.close();
                    latch.countDown();
                }
            });

            streams.setUncaughtExceptionHandler((ex) -> {
                ex.printStackTrace();
                latch.countDown();
                return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.SHUTDOWN_APPLICATION;
            });

            streams.start();
            latch.await();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static class AddTsAndStoreProcessor implements Processor<String, String, String, String> {
        private KeyValueStore<String, String> stateStore;
        private ProcessorContext<String, String> context;

        @Override
        public void init(ProcessorContext<String, String> context) {
            stateStore = context.getStateStore(STATE_STORE_NAME);
            this.context = context;
        }

        @Override
        public void process(Record<String, String> record) {
            stateStore.get(record.key()); // ignore, we need this just to enable waiting for a store restoration
            long currentTsMs = System.currentTimeMillis();
            stateStore.put(record.key(), currentTsMs + record.value());
            context.forward(new Record<>(record.key(), currentTsMs + record.value(), currentTsMs));
        }
    }

    private static class AddTsAndStoreProcessorSupplier implements ProcessorSupplier<String, String, String, String> {
        @Override
        public Processor<String, String, String, String> get() {
            return new AddTsAndStoreProcessor();
        }

        @Override
        public Set<StoreBuilder<?>> stores() {
            return Set.of(Stores.keyValueStoreBuilder(Stores.persistentKeyValueStore(STATE_STORE_NAME), Serdes.String(), Serdes.String()));
        }
    }
}
