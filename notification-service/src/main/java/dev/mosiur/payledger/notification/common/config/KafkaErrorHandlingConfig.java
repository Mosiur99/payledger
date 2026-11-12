package dev.mosiur.payledger.notification.common.config;

import dev.mosiur.payledger.notification.notification.consumer.InvalidEventException;
import dev.mosiur.payledger.notification.notification.model.TransactionPostedEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration(proxyBeanMethods = false)
public class KafkaErrorHandlingConfig {

    public static final String DEAD_LETTER_TOPIC = TransactionPostedEvent.TOPIC + ".DLT";
    public static final int MAX_RETRIES = 3;

    private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandlingConfig.class);

    /**
     * Retries a failing record with growing pauses (0.5 s, 1 s, 2 s), then parks it on the dead letter topic
     * and moves on, so one bad message never blocks its partition. Invalid messages skip the retries.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaOperations<?, ?> kafkaTemplate) {
        DeadLetterPublishingRecoverer deadLetters = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> new TopicPartition(DEAD_LETTER_TOPIC, record.partition()));
        ConsumerRecordRecoverer recoverer = (record, exception) -> {
            log.warn("Moving {}-{}@{} (key {}) to {}", record.topic(), record.partition(), record.offset(),
                    record.key(), DEAD_LETTER_TOPIC, exception);
            deadLetters.accept(record, exception);
        };
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(MAX_RETRIES);
        backOff.setInitialInterval(500);
        backOff.setMultiplier(2);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(InvalidEventException.class);
        return handler;
    }

    /**
     * Declared here too because whichever service starts first creates it; otherwise the broker would
     * auto-create it with a single partition when this consumer subscribes. Settings match payments-service.
     */
    @Bean
    NewTopic transactionsTopic() {
        return TopicBuilder.name(TransactionPostedEvent.TOPIC).partitions(3).replicas(1).build();
    }

    /** Same partition count as the source topic, so a dead letter keeps its original partition. */
    @Bean
    NewTopic deadLetterTopic() {
        return TopicBuilder.name(DEAD_LETTER_TOPIC).partitions(3).replicas(1).build();
    }
}
