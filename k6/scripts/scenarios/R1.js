/**
 * R1 — Read-Heavy (CQRS Projections vs Normalised Joins)
 *
 * Goal: Measure read scalability. ES CQRS projections (pre-materialised views)
 * are expected to outperform CRUD's customer-order JOIN queries at high concurrency.
 *
 * Traffic is intentionally skewed: 80% of reads target the top 1% of "active"
 * customers (first 10 IDs) to reproduce a realistic hot-path access pattern.
 *
 * Stages (25 min total):
 *   100 VU →  500 VU × 10 min
 *   500 VU → 1000 VU × 10 min
 *  1000 VU →    0 VU ×  5 min (cool-down)
 *
 * Endpoint mix:
 *   60%  GET  /api/v1/customers/{id}/orders?status=CONFIRMED  (skewed to top 10)
 *   20%  GET  /api/v1/carts/{id}
 *   10%  GET  /api/v1/orders/{id}
 *   10%  POST /api/v1/carts/{id}/items                        (keeps data fresh)
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import {
  customerIds,
  productIds,
  activeCustomerIds,
  randomFrom,
  randInt,
} from '../shared/data.js';
import { customerOrdersDuration, checkOk } from '../shared/metrics.js';
import { thresholds } from '../shared/thresholds.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const HEADERS = { 'Content-Type': 'application/json' };

export const options = {
  stages: [
    { duration: '2m',  target: 100  },
    { duration: '10m', target: 500  },
    { duration: '10m', target: 1000 },
    { duration: '5m',  target: 0    },
  ],
  thresholds: {
    ...thresholds,
    // R1 is read-heavy so latency targets are tighter for read endpoints
    customer_orders_duration: ['p(95)<400'],
    http_req_duration: ['p(50)<150', 'p(95)<600', 'p(99)<1500'],
  },
};

let vuState = {
  customerId: null,
  activeCustomerId: null,
  productId: null,
  orderId: null,
};

function ensureVuInit() {
  if (vuState.customerId) return;
  const idx = exec.vu.idInTest - 1;
  vuState.customerId       = customerIds[idx % customerIds.length];
  vuState.activeCustomerId = activeCustomerIds[idx % activeCustomerIds.length];
  vuState.productId        = productIds[idx % productIds.length];
}

// ---- Operations -------------------------------------------------------

/**
 * List confirmed orders for a customer.
 * 80% of the time uses an "active" (hot) customer to stress the read path.
 */
function listCustomerOrders() {
  const useHot = Math.random() < 0.80;
  const cid = useHot ? vuState.activeCustomerId : vuState.customerId;
  const res = http.get(
    `${BASE_URL}/api/v1/customers/${cid}/orders?status=CONFIRMED`,
    { tags: { endpoint: 'customer_orders' } }
  );
  customerOrdersDuration.add(res.timings.duration);
  checkOk(res, 'customer_orders');
}

function getCart() {
  const res = http.get(
    `${BASE_URL}/api/v1/carts/${vuState.customerId}`,
    { tags: { endpoint: 'cart_get' } }
  );
  checkOk(res, 'cart_get');
}

function getOrder() {
  if (!vuState.orderId) {
    getCart();
    return;
  }
  const res = http.get(
    `${BASE_URL}/api/v1/orders/${vuState.orderId}`,
    { tags: { endpoint: 'order_get' } }
  );
  checkOk(res, 'order_get');
}

/** Write operation to keep data fresh and avoid stale-read optimisation. */
function addToCart() {
  const res = http.post(
    `${BASE_URL}/api/v1/carts/${vuState.customerId}/items`,
    JSON.stringify({
      productId: vuState.productId,
      quantity: 1,
      unitPrice: '9.99',
    }),
    { headers: HEADERS, tags: { endpoint: 'cart_add' } }
  );
  checkOk(res, 'cart_add');
  // Attempt to create an order so getOrder() has something to fetch
  if (res.status === 204 && !vuState.orderId) {
    const orderRes = http.post(
      `${BASE_URL}/api/v1/orders/from-cart/${vuState.customerId}`,
      null,
      { headers: HEADERS, tags: { endpoint: 'order_create_r1' } }
    );
    if (orderRes.status === 201) {
      vuState.orderId = JSON.parse(orderRes.body).orderId;
    }
  }
}

// ---- Default function -------------------------------------------------

export default function () {
  ensureVuInit();

  const r = Math.random();
  if      (r < 0.60) listCustomerOrders();
  else if (r < 0.80) getCart();
  else if (r < 0.90) getOrder();
  else               addToCart();

  sleep(randInt(1, 2) * 0.1); // 100–200 ms think time
}
