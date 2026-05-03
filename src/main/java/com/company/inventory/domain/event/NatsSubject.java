package com.company.inventory.domain.event;

/**
 * Maps domain EventType values to NATS JetStream subject.
 * */
public final class NatsSubject {

    public static final String CREATED   = "reservations.created";
    public static final String CONFIRMED = "reservations.confirmed";
    public static final String CANCELLED = "reservations.cancelled";

    public static String of(EventType type) {
        return switch (type) {
            case RESERVATION_CREATED   -> CREATED;
            case RESERVATION_CONFIRMED -> CONFIRMED;
            case RESERVATION_CANCELLED -> CANCELLED;
        };
    }

    private NatsSubject() {}
}