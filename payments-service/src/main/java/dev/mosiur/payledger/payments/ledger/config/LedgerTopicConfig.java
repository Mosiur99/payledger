package dev.mosiur.payledger.payments.ledger.config;

import dev.mosiur.payledger.payments.ledger.model.TransactionPostedEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty(name = "payledger.outbox.relay.enabled", matchIfMissing = true)
public class LedgerTopicConfig {

    @Bean
    NewTopic transactionsTopic() {
        return TopicBuilder.name(TransactionPostedEvent.TOPIC).partitions(3).replicas(1).build();
    }
}
