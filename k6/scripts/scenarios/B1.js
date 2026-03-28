/**
 * B1 — Baseline Capacity
 *
 * Goal: Establish maximum stable throughput and record latency percentiles
 * across the full endpoint mix. Ramps VUs until the system saturates.
 *
 * Stages (30 min total):
 *   10 VU ×  2 min  (warm-up)
 *   50 VU ×  5 min
 *  100 VU ×  5 min
 *  200 VU ×  5 min  ← expected saturation zone
 *   50 VU ×  3 min  (cool-down)
 *
 * Endpoint mix (weighted by real-world frequency):
 *   20%  POST /api/v1/carts/{id}/items         add to cart
 *   15%  DELETE /api/v1/carts/{id}/items/{pid} remove from cart
 *   25%  POST /api/v1/orders/from-cart/{id}    create order
 *   15%  POST /api/v1/orders/{id}/confirm      confirm order
 *   10%  GET  /api/v1/carts/{id}               read cart
 *   10%  GET  /api/v1/orders/{id}              read order
 *    5%  GET  /api/v1/products/{id}/stock      check stock
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
import { thresholds } from '../shared/thresholds.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const HEADERS = { 'Content-Type': 'application/json' };

export const options = {
  stages: [
    { duration: '2m',  target: 10  },
    { duration: '5m',  target: 50  },
    { duration: '5m',  target: 100 },
    { duration: '5m',  target: 200 },
    { duration: '3m',  target: 50  },
  ],
  thresholds,
};

// VU-local state (each VU has its own JS runtime in k6)
let vuState = {
  customerId: null,
  productId: null,
  orderId: null,
  orderStatus: null, // 'NEW' | 'CONFIRMED' | null
};

function ensureVuInit() {
  if (vuState.customerId) return;
  // Stable assignment: VU N always owns customer[N % len] and product[N % len]
  const idx = exec.vu.idInTest - 1;
  vuState.customerId = customerIds[idx % customerIds.length];
  vuState.productId  = productIds[idx % productIds.length];
}

// ---- Operations -------------------------------------------------------

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
  cartAddDuration.add(res.timings.duration);
  checkOk(res, 'cart_add');
}

function removeFromCart() {
  http.del(
    `${BASE_URL}/api/v1/carts/${vuState.customerId}/items/${vuState.productId}`,
    null,
    { tags: { endpoint: 'cart_remove' } }
  );
  // 404 is acceptable (cart may not exist yet)
}

function createOrder() {
  if (vuState.orderId && vuState.orderStatus === 'NEW') {
    // Already have a pending order — skip to avoid duplicate creates
    return;
  }
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
    // Nothing to confirm — read the cart instead as fallback
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
    vuState.orderStatus = 'CONFIRMED';
    // Reset after confirm so the VU can create a new order next cycle
    vuState.orderId = null;
    vuState.orderStatus = null;
  }
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

function getStock() {
  const res = http.get(
    `${BASE_URL}/api/v1/products/${vuState.productId}/stock`,
    { tags: { endpoint: 'stock_get' } }
  );
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

  sleep(randInt(1, 3) * 0.1); // 100–300 ms think time
}
