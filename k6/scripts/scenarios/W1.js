/**
 * W1 — Conflict-Heavy (Inventory Contention)
 *
 * Goal: Expose the difference between ES optimistic concurrency (version
 * conflicts / retries) and CRUD pessimistic locking (oversell bugs) under
 * heavy contention on a small set of low-stock "hot" products.
 *
 * Hot products start with 10–50 units each. At high VU counts the stock is
 * quickly exhausted, triggering 409 responses. Cancel operations release
 * reserved stock back, sustaining the contention loop.
 *
 * Stages (20 min total):
 *    50 VU → 500 VU ramp over 20 min (linear)
 *
 * Endpoint mix:
 *   45%  POST /api/v1/carts/{id}/items           add hot product to cart
 *   30%  POST /api/v1/orders/from-cart/{id}      create order (hot products)
 *   15%  DELETE /api/v1/orders/{id}/cancel       cancel order (releases stock)
 *    5%  POST /api/v1/orders/{id}/confirm        confirm order
 *    5%  GET  /api/v1/products/{id}/stock        check hot product stock
 *
 * Key measurements:
 *   - conflict_rate: % of order-create requests that return 409
 *   - throughput degradation curve as VU count rises
 *   - oversell check on stock GET responses (CRUD failure mode)
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { customerIds, hotProductIds, randomFrom, randInt } from '../shared/data.js';
import {
  orderCreateDuration,
  checkOk,
  checkConflictHandled,
  checkNoOversell,
  recordConflict,
} from '../shared/metrics.js';
import { thresholds } from '../shared/thresholds.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const HEADERS = { 'Content-Type': 'application/json' };

export const options = {
  stages: [
    { duration: '20m', target: 500 },
    { duration: '2m',  target: 0   }, // cool-down
  ],
  thresholds: {
    ...thresholds,
    // W1-specific: allow higher error rate since 409s are expected
    http_req_failed: ['rate<0.60'],
    conflict_rate:   ['rate<0.80'],
  },
};

let vuState = {
  customerId: null,
  hotProductId: null,
  orderId: null,
  orderStatus: null,
};

function ensureVuInit() {
  if (vuState.customerId) return;
  const idx = exec.vu.idInTest - 1;
  vuState.customerId   = customerIds[idx % customerIds.length];
  vuState.hotProductId = hotProductIds[idx % hotProductIds.length];
}

// ---- Operations -------------------------------------------------------

function addHotToCart() {
  const res = http.post(
    `${BASE_URL}/api/v1/carts/${vuState.customerId}/items`,
    JSON.stringify({
      productId: vuState.hotProductId,
      quantity: 1,
      unitPrice: '19.99',
    }),
    { headers: HEADERS, tags: { endpoint: 'cart_add_hot' } }
  );
  checkOk(res, 'cart_add_hot');
}

function createOrder() {
  if (vuState.orderId && vuState.orderStatus === 'NEW') return;

  const res = http.post(
    `${BASE_URL}/api/v1/orders/from-cart/${vuState.customerId}`,
    null,
    { headers: HEADERS, tags: { endpoint: 'order_create' } }
  );
  orderCreateDuration.add(res.timings.duration);
  checkConflictHandled(res);
  recordConflict(res);
  if (res.status === 201) {
    vuState.orderId = JSON.parse(res.body).orderId;
    vuState.orderStatus = 'NEW';
  }
}

function cancelOrder() {
  if (!vuState.orderId || vuState.orderStatus === null) {
    // Nothing to cancel — add to cart instead
    addHotToCart();
    return;
  }
  const res = http.del(
    `${BASE_URL}/api/v1/orders/${vuState.orderId}/cancel`,
    JSON.stringify({ reason: 'load-test-release' }),
    { headers: HEADERS, tags: { endpoint: 'order_cancel' } }
  );
  check(res, { 'cancel ok (204 or 404)': (r) => r.status === 204 || r.status === 404 });
  // Reset state regardless of outcome (stock released or order already gone)
  vuState.orderId = null;
  vuState.orderStatus = null;
}

function confirmOrder() {
  if (!vuState.orderId || vuState.orderStatus !== 'NEW') {
    addHotToCart();
    return;
  }
  const res = http.post(
    `${BASE_URL}/api/v1/orders/${vuState.orderId}/confirm`,
    null,
    { headers: HEADERS, tags: { endpoint: 'order_confirm' } }
  );
  checkOk(res, 'order_confirm');
  if (res.status === 204) {
    vuState.orderStatus = 'CONFIRMED';
    vuState.orderId = null;
    vuState.orderStatus = null;
  }
}

function getHotStock() {
  const res = http.get(
    `${BASE_URL}/api/v1/products/${vuState.hotProductId}/stock`,
    { tags: { endpoint: 'stock_get_hot' } }
  );
  checkOk(res, 'stock_get_hot');
  checkNoOversell(res); // critical: negative available = oversell bug
}

// ---- Default function -------------------------------------------------

export default function () {
  ensureVuInit();

  const r = Math.random();
  if      (r < 0.45) addHotToCart();
  else if (r < 0.75) createOrder();
  else if (r < 0.90) cancelOrder();
  else if (r < 0.95) confirmOrder();
  else               getHotStock();

  sleep(randInt(0, 1) * 0.05); // minimal think time to maximise contention
}
