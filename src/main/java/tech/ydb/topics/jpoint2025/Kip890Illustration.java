package tech.ydb.topics.jpoint2025;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.IsolationLevel;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.StreamSupport;

public class Kip890Illustration {

    public static final String TOPIC = "kip890-test-topic";

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        // launch producer
        // send first message to begin transaction
        // send second message and pause thread right before sending message to the network (we are emulating network delay here)
        // wait for transaction timeout, keep thread paused
        // send same two messages from another thread (we are emulating retry)
        // unpause first thread
        // commit in a second thread
        // we should see duplicates in our topic

        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        config.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "my-local-producer-1");
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, IsolationLevel.READ_COMMITTED.name().toLowerCase());

        CountDownLatch beforeCommitLatch = new CountDownLatch(1);
        CountDownLatch beforeSecondAttemptLatch = new CountDownLatch(1);
        CountDownLatch beforeDuplicatesSendLatch = new CountDownLatch(1);

        ExecutorService executorService = Executors.newCachedThreadPool();

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(config);) {
            producer.initTransactions();

            Future<?> firstAttemptFuture = executorService.submit(() -> {
                try {
                    producer.beginTransaction();
                    System.out.println("First attempt: began transaction");

                    producer.send(new ProducerRecord<>(TOPIC, "message1")).get();
                    System.out.println("First attempt: wrote first message");
                    System.out.println("First attempt: sleeping");
                    Thread.sleep(61_000); // default tx timeout = 1 min
                    System.out.println("First attempt: awakened from sleep");
                    beforeSecondAttemptLatch.countDown();
                    beforeDuplicatesSendLatch.await();
                    System.out.println("First attempt: sending duplicate");
                    producer.send(new ProducerRecord<>(TOPIC, "message2")).get();
                    System.out.println("First attempt: wrote second message");
                    beforeCommitLatch.countDown();
                } catch (Exception e) {
                    System.out.println("Error on first attempt");
                    e.printStackTrace();
                }
            });
            Future<?> secondAttemptFuture = executorService.submit(() -> {
                try {
                    beforeSecondAttemptLatch.await();
                    System.out.println("Second attempt: awakened, aborting transaction");
                    producer.abortTransaction();
                    System.out.println("Second attempt: aborted transaction");
                    producer.beginTransaction();
                    System.out.println("Second attempt: began transaction");

                    producer.send(new ProducerRecord<>(TOPIC, "message1")).get();
                    System.out.println("Second attempt: wrote second message");
                    producer.send(new ProducerRecord<>(TOPIC, "message2")).get();
                    System.out.println("Second attempt: wrote second message");
                    beforeDuplicatesSendLatch.countDown();
                    beforeCommitLatch.await();
                    System.out.println("Second attempt: awakened, starting commit");

                    producer.commitTransaction();
                    System.out.println("Second attempt: committed transaction");
                } catch (Exception e) {
                    System.out.println("Error on second attempt");
                    e.printStackTrace();
                }
            });

            firstAttemptFuture.get();
            secondAttemptFuture.get();
        }

        // should print duplicates
        printLastRecords(config, 4);
        executorService.shutdownNow();
    }

    private static void printLastRecords(Map<String, Object> config, int printCount) {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config);) {
            Collection<TopicPartition> partitions = consumer.partitionsFor(TOPIC).stream()
                    .map(part -> new TopicPartition(TOPIC, part.partition()))
                    .toList();
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            StreamSupport.stream(consumer.poll(Duration.ofMillis(1000)).records(TOPIC).spliterator(), false)
                    .sorted(Comparator.comparingLong(rec -> ((ConsumerRecord<String, String>) rec).offset()).reversed())
                    .limit(printCount)
                    .forEach(record -> System.out.println(record.value()));
        }
    }
}