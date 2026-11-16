CREATE TABLE categories (
  id UUID PRIMARY KEY,
  name VARCHAR(120) NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE products (
  id UUID PRIMARY KEY,
  category_id UUID NOT NULL REFERENCES categories(id),
  sku VARCHAR(80) NOT NULL UNIQUE,
  name VARCHAR(200) NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  price_vnd BIGINT NOT NULL CHECK (price_vnd >= 0),
  available_stock INTEGER NOT NULL CHECK (available_stock >= 0),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_products_category_active ON products(category_id, active);
CREATE INDEX idx_products_name ON products(name);

CREATE TABLE stock_reservations (
  order_id UUID PRIMARY KEY,
  status VARCHAR(12) NOT NULL CHECK (status IN ('ACTIVE','RELEASED')),
  request_hash CHAR(64) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE stock_reservation_items (
  order_id UUID NOT NULL REFERENCES stock_reservations(order_id),
  product_id UUID NOT NULL REFERENCES products(id),
  quantity INTEGER NOT NULL CHECK (quantity > 0),
  unit_price_vnd BIGINT NOT NULL CHECK (unit_price_vnd >= 0),
  PRIMARY KEY(order_id, product_id)
);
