CREATE TABLE payments (
  order_id UUID PRIMARY KEY,
  amount_vnd BIGINT NOT NULL CHECK (amount_vnd >= 0),
  status VARCHAR(20) NOT NULL CHECK (status IN ('AUTHORIZED','DECLINED','REFUNDED')),
  method VARCHAR(30) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
