CREATE TABLE orders (
    id UUID PRIMARY KEY,
    checkout_key_hash CHAR(64) NOT NULL UNIQUE,
    request_hash CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (
        status IN ('PENDING','CONFIRMED','REJECTED','CANCEL_PENDING','CANCELLED')
    ),
    total_vnd BIGINT NOT NULL CHECK (total_vnd >= 0),
    customer_name VARCHAR(120) NOT NULL,
    customer_phone VARCHAR(20) NOT NULL,
    shipping_address TEXT NOT NULL,
    last_error_code VARCHAR(60),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_status_created ON orders(status, created_at);
CREATE INDEX idx_orders_status_updated ON orders(status, updated_at);

CREATE TABLE order_items (
    order_id UUID NOT NULL REFERENCES orders(id),
    product_id UUID NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price_vnd BIGINT NOT NULL CHECK (unit_price_vnd >= 0),
    line_total_vnd BIGINT NOT NULL CHECK (line_total_vnd >= 0),
    PRIMARY KEY (order_id, product_id)
);
