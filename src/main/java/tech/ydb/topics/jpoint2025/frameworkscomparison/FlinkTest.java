package tech.ydb.topics.jpoint2025.frameworkscomparison;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.configuration.CheckpointingOptions;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.ExternalizedCheckpointRetention;
import org.apache.flink.configuration.StateBackendOptions;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.core.execution.CheckpointingMode;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.graph.StreamGraph;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.time.Duration;
import java.util.Properties;

public class FlinkTest {
    public static final String INPUT_TOPIC = "eos-tests-source-topic";
    public static final String TARGET_TOPIC = "eos-tests-flink-target";
    public static final String BOOTSTRAP_SERVERS = "localhost:9092";

    public static void main(String[] args) throws Exception {
        try (StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment()) {
            env.enableCheckpointing(100, CheckpointingMode.EXACTLY_ONCE);

            Configuration config = new Configuration();
            config.set(CheckpointingOptions.CHECKPOINT_STORAGE, "filesystem");
            config.set(CheckpointingOptions.CHECKPOINTS_DIRECTORY, "file:///Users/serebryanskiy/IdeaProjects/kafka-exploration/flink-checkpoints");
            config.set(StateBackendOptions.STATE_BACKEND, "rocksdb");
            config.setString("state.backend.rocksdb.localdir", "/Users/serebryanskiy/IdeaProjects/kafka-exploration/flink-store");
            config.setString("execution.checkpointing.incremental", "true");
            env.configure(config);
            env.setParallelism(1);
            env.getCheckpointConfig().setExternalizedCheckpointRetention(ExternalizedCheckpointRetention.RETAIN_ON_CANCELLATION);

            KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                    .setBootstrapServers(BOOTSTRAP_SERVERS)
                    .setGroupId("flink-demo-consumer")
                    .setTopics(INPUT_TOPIC)
                    .setStartingOffsets(OffsetsInitializer.earliest())
                    .setBounded(OffsetsInitializer.latest())
                    .setValueOnlyDeserializer(new SimpleStringSchema())
                    .build();

            Properties producerProps = new Properties();
            producerProps.put(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, 60_000);
            Sink<String> kafkaSink = KafkaSink.<String>builder()
                    .setBootstrapServers(BOOTSTRAP_SERVERS)
                    .setKafkaProducerConfig(producerProps)
                    .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                            .setTopic(TARGET_TOPIC)
                            .setValueSerializationSchema(new SimpleStringSchema())
                            .setKeySerializationSchema(new SimpleStringSchema())
                            .build())
                    .setRecordSerializer((el, ctx, ts) -> new ProducerRecord<>(TARGET_TOPIC, null, ts, el.getBytes(), el.getBytes(), null))
                    .setDeliveryGuarantee(DeliveryGuarantee.EXACTLY_ONCE)
                    .setTransactionalIdPrefix("flink-tests-tx-producer-")
                    .build();

            WatermarkStrategy<String> watermarkStrategy = WatermarkStrategy.<String>forMonotonousTimestamps().withIdleness(Duration.ofSeconds(1));

            env.fromSource(kafkaSource, watermarkStrategy, "kafka-source")
                    .keyBy(val -> val)
                    .map(new AddTxAndStoreFunction())
                    .sinkTo(kafkaSink);

            env.execute("flink-demo-app");
        }
    }

    private static class AddTxAndStoreFunction extends RichMapFunction<String, String> {
        private transient ValueState<String> store;

        @Override
        public void open(OpenContext openContext) throws Exception {
            store = getRuntimeContext().getState(new ValueStateDescriptor<>(
                    "my-state-store",
                    TypeInformation.of(String.class)
            ));
        }

        @Override
        public String map(String value) throws Exception {
            String newVal = System.currentTimeMillis() + value;
            store.update(newVal);
            return newVal;
        }
    }
}
