CREATE TABLE notifications (
  id UUID PRIMARY KEY,
  order_id UUID NOT NULL,
  recipient VARCHAR(254) NOT NULL,
  event_type VARCHAR(60) NOT NULL,
  message TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_notification_event ON notifications(order_id, event_type);
