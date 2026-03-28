/**
 * S1 — Soak Test (Stability Over Time)
 *
 * Goal: Detect memory leaks, connection pool exhaustion, and projection backlog
 * drift by running the B1 load mix at 70% of the identified breaking point
 * (140 VU ≈ 0.70 × 200 VU breakpoint) for 2 hours.
 *
 * Stages:
 *    0 →  140 VU ×  5 min (ramp-up)
 *         140 VU × 120 min (sustained load — 2 hours)
 *  140 →    0 VU ×  5 min (ramp-down)
 *
 * The same endpoint mix as B1 is used so results are directly comparable.
 * Monitor heap growth and latency trend over time externally via Prometheus.
 *
 * Thresholds are relaxed slightly vs B1 to allow for occasional GC pauses
 * over the 2-hour window, while still catching sustained degradation.
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { customerIds, productIds, randomFrom, randInt } from '../shared/data.js';
import {
  cartAddDuration,
  orderCreateDuration,
  orderConfirmDuration,
  checkOk,
  checkConflictHandled,
  recordConflict,
} from '../shared/metrics.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const HEADERS = { 'Content-Type': 'application/json' };

export const options = {
  stages: [
    { duration: '5m',   target: 140 },
    { duration: '120m', target: 140 },
    { duration: '5m',   target: 0   },
  ],
  thresholds: {
    // Slightly relaxed vs B1 to tolerate GC pauses over 2h
    http_req_duration: ['p(50)<300', 'p(95)<1000', 'p(99)<3000'],
    http_req_failed:   ['rate<0.05'],
    conflict_rate:     ['rate<0.05'],
    cart_add_duration:    ['p(95)<700'],
    order_create_duration: ['p(95)<1000'],
  },
};

let vuState = {
  customerId: null,
  productId: null,
  orderId: null,
  orderStatus: null,
};

function ensureVuInit() {
  if (vuState.customerId) return;
  const idx = exec.vu.idInTest - 1;
  vuState.customerId = customerIds[idx % customerIds.length];
  vuState.productId  = productIds[idx % productIds.length];
}

// ---- B1 operations (copy-minimal — same logic as B1.js) ---------------

function addToCart() {
  const res = http.post(
    `${BASE_URL}/api/v1/carts/${vuState.customerId}/items`,
    JSON.stringify({ productId: vuState.productId, quantity: 1, unitPrice: '9.99' }),
    { headers: HEADERS, tags: { endpoint: 'cart_add' } }
  );
  cartAddDuration.add(res.timings.duration);
  checkOk(res, 'cart_add');
}

function removeFromCart() {
  http.del(
    `${BASE_URL}/api/v1/carts/${vuState.customerId}/items/${vuState.productId}`,
    null,
    { tags: { endpoint: 'cart_remove' } }
  );
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

function confirmOrder() {
  if (!vuState.orderId || vuState.orderStatus !== 'NEW') {
    http.get(`${BASE_URL}/api/v1/carts/${vuState.customerId}`, { tags: { endpoint: 'cart_get' } });
    return;
  }
  const res = http.post(
    `${BASE_URL}/api/v1/orders/${vuState.orderId}/confirm`,
    null,
    { headers: HEADERS, tags: { endpoint: 'order_confirm' } }
  );
  orderConfirmDuration.add(res.timings.duration);
  checkOk(res, 'order_confirm');
  if (res.status === 204) {
    vuState.orderId = null;
    vuState.orderStatus = null;
  }
}

function getCart() {
  const res = http.get(`${BASE_URL}/api/v1/carts/${vuState.customerId}`, { tags: { endpoint: 'cart_get' } });
  checkOk(res, 'cart_get');
}

function getOrder() {
  if (!vuState.orderId) { getCart(); return; }
  const res = http.get(`${BASE_URL}/api/v1/orders/${vuState.orderId}`, { tags: { endpoint: 'order_get' } });
  checkOk(res, 'order_get');
}

function getStock() {
  const res = http.get(`${BASE_URL}/api/v1/products/${vuState.productId}/stock`, { tags: { endpoint: 'stock_get' } });
  checkOk(res, 'stock_get');
}

// ---- Default function -------------------------------------------------

export default function () {
  ensureVuInit();

  const r = Math.random();
  if      (r < 0.20) addToCart();
  else if (r < 0.35) removeFromCart();
  else if (r < 0.60) createOrder();
  else if (r < 0.75) confirmOrder();
  else if (r < 0.85) getCart();
  else if (r < 0.95) getOrder();
  else               getStock();

  sleep(randInt(1, 3) * 0.1);
}
