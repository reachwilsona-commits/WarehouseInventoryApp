package com.company.inventory.messaging.nats;

import com.company.inventory.domain.event.NatsEventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.api.AckPolicy;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Class for NATS JetStream Consumer for the DomainEvent
 * RESERVATIONS stream subscribes to all three subjects via "reservations.*"
 *
 * Delivery semantics:
 * Messages stay in NATS until you confirm them (acknowledge).
 * If processing fails, the message is retried (redelivered).
 * After 5 failed attempts, the message is moved to a dead-letter queue.
 *
 * Idempotency:
 * Keep track of the last 1,000 processed messages in memory to avoid duplicates.
 * If a message is delivered more than once, log a warning.
 * In production, store processed messages in a database instead of memory.
 *
 * Add another consumer (like analytics) without changing this service, just deploy a new durable consumer with a different name.
 */
@Component
@ConditionalOnBean(JetStream.class)
public class NatsInventoryEventConsumer
        implements ApplicationListener<ApplicationReadyEvent>, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(NatsInventoryEventConsumer.class);
    private static final String DURABLE_NAME = "warehouse-audit-consumer";
    private static final int SEEN_CAPACITY   = 1_000;

    private final Connection connection;
    private final JetStream jetStream;
    private final ObjectMapper objectMapper;

    private Dispatcher dispatcher;
    private JetStreamSubscription subscription;

    /**
     * Bounded LRU map used for in-process deduplication.
     * Evicts the oldest entry once the capacity is exceeded.
     */
    private final Map<Long, Boolean> seenSequences = Collections.synchronizedMap(
            new LinkedHashMap<>(SEEN_CAPACITY, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, Boolean> eldest) {
                    return size() > SEEN_CAPACITY;
                }
            });

    public NatsInventoryEventConsumer(Connection connection,
                                      JetStream jetStream,
                                      ObjectMapper objectMapper) {
        this.connection = connection;
        this.jetStream = jetStream;
        this.objectMapper = objectMapper;
    }

    /**
     * On Application Event method to wire up everything to Consume event from the NATS JetStream
     * @param event ApplicationReadyEvent
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            PushSubscribeOptions options = PushSubscribeOptions.builder()
                    .configuration(ConsumerConfiguration.builder()
                            .durable(DURABLE_NAME)
                            .ackPolicy(AckPolicy.Explicit)
                            .deliverPolicy(DeliverPolicy.All)
                            .ackWait(Duration.ofSeconds(30))
                            .maxDeliver(5)
                            .build())
                    .build();

            dispatcher   = connection.createDispatcher();
            subscription = jetStream.subscribe("reservations.*", dispatcher, this::onMessage, false, options);

            log.info("NATS consumer started durable durable={} filter=reservations.*", DURABLE_NAME);
        } catch (Exception e) {
            log.error("NATS consumer start failed durable={}", DURABLE_NAME, e);
        }
    }

    /**
     * Method to consume the Message and process it.
     * @param msg Message
     */
    private void onMessage(Message msg) {
        long seq = msg.metaData().streamSequence();
        //1. Check if the message is already processed.
        if (seenSequences.containsKey(seq)) {
            log.debug("NATS duplicate skipped seq={} subject={}", seq, msg.getSubject());
            msg.ack();
            return;
        }
        //2. Process the message
        try {
            NatsEventEnvelope envelope = objectMapper.readValue(msg.getData(), NatsEventEnvelope.class);
            long deliveries = msg.metaData().deliveredCount();

            if (deliveries > 1) {
                log.warn("NATS Redelivery seq={} deliveries={} type={} reservationId={}",
                        seq, deliveries, envelope.eventType(), envelope.reservationId());
            }

            process(envelope, seq);
            //3. Add the sequence back to seenSequences for successful message processing.
            seenSequences.put(seq, Boolean.TRUE);
            //4. Acknowledge the Message
            msg.ack();

        } catch (Exception e) {
            log.error("NATS processing failed seq={} subject={}", seq, msg.getSubject(), e);
            msg.nak();
        }
    }

    /**
     * Business logic executed for each event. Downstream consumers replace this body;
     */
    private void process(NatsEventEnvelope envelope, long seq) {
        log.info("NATS events consumed type={} reservationId={} orderId={} seq={}",
                envelope.eventType(), envelope.reservationId(), envelope.orderId(), seq);
    }

    @Override
    public void destroy() {
        if (subscription != null) {
            try { subscription.unsubscribe(); } catch (Exception e) { log.warn("NATS unsubscribe error", e); }
        }
        if (dispatcher != null) {
            try { connection.closeDispatcher(dispatcher); } catch (Exception e) { log.warn("NATS dispatcher close error", e); }
        }
    }
}