CREATE TABLE proj_orders (
    id           UUID          NOT NULL PRIMARY KEY,
    customer_id  UUID          NOT NULL,
    status       VARCHAR(50)   NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL
);

CREATE TABLE proj_order_items (
    id         UUID          NOT NULL PRIMARY KEY,
    order_id   UUID          NOT NULL REFERENCES proj_orders(id) ON DELETE CASCADE,
    product_id UUID          NOT NULL,
    quantity   INTEGER       NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL
);

CREATE TABLE proj_carts (
    customer_id UUID        NOT NULL PRIMARY KEY,
    items       JSONB       NOT NULL DEFAULT '[]',
    updated_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_proj_orders_customer_status ON proj_orders (customer_id, status);
CREATE INDEX idx_proj_orders_created         ON proj_orders (created_at);
CREATE INDEX idx_proj_order_items_order      ON proj_order_items (order_id);
