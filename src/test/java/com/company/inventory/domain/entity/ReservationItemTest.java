package com.company.inventory.domain.entity;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationItemTest {

    private Reservation reservation() {
        return new Reservation(UUID.randomUUID(), "ORD-1",
                OffsetDateTime.now(), OffsetDateTime.now().plusMinutes(10));
    }

    @Test
    void constructor_setsFields_forValidQuantity() {
        Reservation r = reservation();
        ReservationItem item = new ReservationItem(r, "SKU-A", 3);

        assertThat(item.getSku()).isEqualTo("SKU-A");
        assertThat(item.getQuantity()).isEqualTo(3);
        assertThat(item.getReservation()).isSameAs(r);
        assertThat(item.getId()).isNull();
    }

    @Test
    void constructor_throwsIllegalArgumentException_forZeroQuantity() {
        Reservation r = reservation();

        assertThatThrownBy(() -> new ReservationItem(r, "SKU-A", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity must be > 0");
    }

    @Test
    void constructor_throwsIllegalArgumentException_forNegativeQuantity() {
        Reservation r = reservation();

        assertThatThrownBy(() -> new ReservationItem(r, "SKU-A", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}