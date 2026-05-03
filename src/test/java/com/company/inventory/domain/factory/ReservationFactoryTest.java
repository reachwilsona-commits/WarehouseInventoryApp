package com.company.inventory.domain.factory;

import com.company.inventory.config.ReservationProperties;
import com.company.inventory.domain.entity.Reservation;
import com.company.inventory.domain.entity.ReservationStatus;
import com.company.inventory.model.request.RequestedItem;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationFactoryTest {

    private final ReservationProperties props = new ReservationProperties(10, "0 */2 * * * *", 100);
    private final Clock fixed = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final ReservationFactory factory = new ReservationFactory(props, fixed);

    @Test
    void createsReservation_withPendingStatus_andTtlBasedExpiry() {
        Reservation r = factory.create("ORD-1",
                List.of(new RequestedItem("A100", 5)));
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(r.getOrderId()).isEqualTo("ORD-1");
        assertThat(r.getCreatedAt().toInstant()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(r.getExpiresAt().toInstant()).isEqualTo(Instant.parse("2026-01-01T00:10:00Z"));
        assertThat(r.getId()).isNotNull();
        assertThat(r.getItems()).hasSize(1);
    }

    @Test
    void coalescesDuplicateSkus_inSameRequest() {
        Reservation r = factory.create("ORD-2", List.of(
                new RequestedItem("A100", 3),
                new RequestedItem("A100", 7)));
        assertThat(r.getItems()).hasSize(1);
        assertThat(r.getItems().get(0).getSku()).isEqualTo("A100");
        assertThat(r.getItems().get(0).getQuantity()).isEqualTo(10);
    }

    @Test
    void rejectsBlankOrderId() {
        assertThatThrownBy(() -> factory.create("",
                List.of(new RequestedItem("A100", 1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmptyItems() {
        assertThatThrownBy(() -> factory.create("ORD-3", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> factory.create("ORD-4",
                List.of(new RequestedItem("A100", 0))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
