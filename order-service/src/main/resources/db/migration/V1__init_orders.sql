CREATE TABLE orders (
  id UUID PRIMARY KEY,
  checkout_key VARCHAR(80) NOT NULL UNIQUE,
  customer_email VARCHAR(254) NOT NULL,
  shipping_address TEXT NOT NULL,
  status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','CONFIRMED','PAID','CANCELLED')),
  total_vnd BIGINT NOT NULL CHECK (total_vnd >= 0),
  currency VARCHAR(8) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE order_items (
  order_id UUID NOT NULL REFERENCES orders(id),
  product_id UUID NOT NULL,
  sku VARCHAR(80) NOT NULL,
  name VARCHAR(200) NOT NULL,
  quantity INTEGER NOT NULL CHECK (quantity > 0),
  unit_price_vnd BIGINT NOT NULL CHECK (unit_price_vnd >= 0),
  PRIMARY KEY (order_id, product_id)
);
