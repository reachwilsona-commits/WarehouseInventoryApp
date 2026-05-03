package com.company.inventory.messaging.nats;

import com.company.inventory.config.NatsProperties;
import com.company.inventory.domain.entity.ReservationEvents;
import com.company.inventory.domain.event.EventType;
import com.company.inventory.domain.event.NatsEventEnvelope;
import com.company.inventory.domain.event.NatsSubject;
import com.company.inventory.repository.ReservationEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.JetStream;
import io.nats.client.PublishOptions;
import io.nats.client.api.PublishAck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Class to publish reservation_events to NATS JetStream as JSON.
 * Outbox relay: polls reservation_events rows where published_at IS NULL and forwards them
 */
@Component
@ConditionalOnBean(JetStream.class)
public class NatsOutboxJob {

    private static final Logger log = LoggerFactory.getLogger(NatsOutboxJob.class);

    private final ReservationEventRepository reservationEventRepository;
    private final JetStream jetStream;
    private final ObjectMapper objectMapper;
    private final NatsProperties properties;

    public NatsOutboxJob(ReservationEventRepository repository,
                         JetStream jetStream,
                         ObjectMapper objectMapper,
                         NatsProperties properties) {
        this.reservationEventRepository = repository;
        this.jetStream = jetStream;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * Scheduler job running on interval.
     */
    @Scheduled(cron = "${nats.job-cron}")
    public void run() {
        //1. find the unpublished events
        List<ReservationEvents> reservationEventsList = reservationEventRepository.findUnpublishedOrderByIdAsc(
                PageRequest.of(0, properties.batchSize()));

        if (reservationEventsList.isEmpty()) {
            log.debug("No event found from DB to publish to NATS JetStream");
            return;
        }

        log.debug("NATS relay start batch size={}", reservationEventsList.size());
        //2. Published each event then update reservation_events table.
        int published = 0;
        for (ReservationEvents reservationEvent : reservationEventsList) {
            try {
                publishOne(reservationEvent);
                reservationEvent.markPublished();
                reservationEventRepository.save(reservationEvent);
                published++;
                log.debug("Reservation Event Id {} published to the NATS JetStream.", reservationEvent.getReservationId());
            } catch (Exception exception) {
                log.error("NATS relay error eventId={} type={}", reservationEvent.getId(), reservationEvent.getEventType(), exception);
            }
        }

        log.info("NATS relay complete published={} total={}", published, reservationEventsList.size());
    }

    /**
     * Method to publish event to NATS JetStream
     * @param event ReservationEvents
     * @throws Exception
     */
    private void publishOne(ReservationEvents event) throws Exception {
        JsonNode rawPayload = objectMapper.readTree(event.getPayload());
        String orderId = rawPayload.path("orderId").asText();
        //1. Build the NatsEventEnvelope
        NatsEventEnvelope envelope = new NatsEventEnvelope(
                event.getEventType(),
                event.getReservationId().toString(),
                orderId,
                event.getCreatedAt().toString(),
                rawPayload
        );
        //2. Convert the NatsEventEnvelope to byte[]
        byte[] data = objectMapper.writeValueAsBytes(envelope);
        String subject = NatsSubject.of(EventType.valueOf(event.getEventType()));

        // Stable dedup key: relay retries won't create duplicate stream entries within the dedup window.
        String messageId = event.getReservationId() + "." + event.getEventType() + "." + event.getId();
        //3. Build PublishOptions with messageId
        PublishOptions po = PublishOptions.builder()
                .messageId(messageId)
                .streamTimeout(Duration.ofSeconds(5))
                .build();
        //4. Publish the event and get the Ack.
        PublishAck ack = jetStream.publish(subject, data, po);
        log.debug("NATS published subject={} seq={} duplicate={}", subject, ack.getSeqno(), ack.isDuplicate());
    }
}