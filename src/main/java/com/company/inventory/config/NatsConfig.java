package com.company.inventory.config;

import com.company.inventory.domain.event.NatsSubject;
import io.nats.client.Connection;
import io.nats.client.ErrorListener;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.JetStreamApiException;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Duration;

/**
 * Wires up the NATS connection, JetStream context, and ensures the RESERVATIONS stream exists.
 * Activated only when nats.enabled=true so the app starts cleanly without a NATS server.
 */
@Configuration
@ConditionalOnProperty(name = "nats.enabled", havingValue = "true")
public class NatsConfig implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(NatsConfig.class);

    private Connection connection;

    @Bean
    public Connection natsConnection(NatsProperties props) throws IOException, InterruptedException {
        Options options = new Options.Builder()
                .server(props.url())
                .maxReconnects(-1)
                .reconnectWait(Duration.ofSeconds(2))
                .connectionTimeout(Duration.ofSeconds(5))
                .connectionListener((conn, type) ->
                        log.info("nats_connection_event type={} url={}", type, props.url()))
                .errorListener(new ErrorListener() {
                    @Override
                    public void errorOccurred(Connection conn, String error) {
                        log.error("nats_error error={}", error);
                    }
                    @Override
                    public void exceptionOccurred(Connection conn, Exception exp) {
                        log.error("nats_exception", exp);
                    }
                    @Override
                    public void slowConsumerDetected(Connection conn, io.nats.client.Consumer consumer) {
                        log.warn("nats_slow_consumer");
                    }
                })
                .build();

        connection = Nats.connect(options);
        log.info("nats_connected url={}", props.url());
        return connection;
    }

    @Bean
    public JetStream jetStream(Connection connection) throws IOException {
        return connection.jetStream();
    }

    @Bean
    public JetStreamManagement jetStreamManagement(Connection connection) throws IOException {
        return connection.jetStreamManagement();
    }

    /**
     * Creates (or updates) the RESERVATIONS stream with all three reservation subjects.
     * Idempotent: if the stream already exists, its configuration is updated to match.
     */
    @Bean
    public StreamInfo reservationsStream(JetStreamManagement jsm, NatsProperties props)
            throws IOException, JetStreamApiException {
        StreamConfiguration config = StreamConfiguration.builder()
                .name(props.streamName())
                .subjects(NatsSubject.CREATED, NatsSubject.CONFIRMED, NatsSubject.CANCELLED)
                .storageType(StorageType.File)
                .retentionPolicy(RetentionPolicy.Limits)
                .replicas(1)
                .build();

        try {
            StreamInfo info = jsm.addStream(config);
            log.info("nats_stream_created stream={} subjects={}", props.streamName(), config.getSubjects());
            return info;
        } catch (JetStreamApiException e) {
            if (e.getApiErrorCode() == 10058) { // stream name already in use
                StreamInfo info = jsm.updateStream(config);
                log.info("nats_stream_updated stream={}", props.streamName());
                return info;
            }
            throw e;
        }
    }

    @Override
    public void destroy() throws Exception {
        if (connection != null && connection.getStatus() != Connection.Status.CLOSED) {
            log.info("nats_shutdown closing connection");
            connection.close();
        }
    }
}