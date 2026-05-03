package com.company.inventory.messaging.nats;

import com.company.inventory.domain.event.EventType;
import com.company.inventory.domain.event.NatsSubject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class NatsSubjectTest {

    @ParameterizedTest
    @EnumSource(EventType.class)
    void of_returnsNonBlankSubject_forEveryEventType(EventType type) {
        assertThat(NatsSubject.of(type)).isNotBlank();
    }

    @Test
    void of_mapsCreated_toCorrectSubject() {
        assertThat(NatsSubject.of(EventType.RESERVATION_CREATED)).isEqualTo("reservations.created");
    }

    @Test
    void of_mapsConfirmed_toCorrectSubject() {
        assertThat(NatsSubject.of(EventType.RESERVATION_CONFIRMED)).isEqualTo("reservations.confirmed");
    }

    @Test
    void of_mapsCancelled_toCorrectSubject() {
        assertThat(NatsSubject.of(EventType.RESERVATION_CANCELLED)).isEqualTo("reservations.cancelled");
    }

    @Test
    void allEventTypes_produceDifferentSubjects() {
        Set<String> subjects = Arrays.stream(EventType.values())
                .map(NatsSubject::of)
                .collect(Collectors.toSet());

        assertThat(subjects).hasSize(EventType.values().length);
    }

    @Test
    void allSubjects_startWithReservationsPrefix() {
        Arrays.stream(EventType.values())
                .map(NatsSubject::of)
                .forEach(s -> assertThat(s).startsWith("reservations."));
    }
}