--liquibase formatted sql

--changeset warehouse:006-add-indexes
--comment: Indexes that target the hot paths:
--           - listing reservations by status with paging  (status + created_at DESC)
--           - expiry job scan over PENDING + expires_at   (partial index on PENDING only)
--           - draining unpublished events                 (partial index where published_at IS NULL)
--           - locating items by reservation_id            (FK lookup during cancel/expiry)
CREATE INDEX idx_reservations_status_created   ON reservations (status, created_at DESC);
CREATE INDEX idx_reservations_pending_expires  ON reservations (expires_at) WHERE status = 'PENDING';
CREATE INDEX idx_reservation_items_reservation ON reservation_items (reservation_id);
CREATE INDEX idx_reservation_events_unpublished ON reservation_events (id) WHERE published_at IS NULL;
--rollback DROP INDEX idx_reservations_status_created;
--rollback DROP INDEX idx_reservations_pending_expires;
--rollback DROP INDEX idx_reservation_items_reservation;
--rollback DROP INDEX idx_reservation_events_unpublished;
