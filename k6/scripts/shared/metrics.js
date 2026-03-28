import { Trend, Counter, Rate } from 'k6/metrics';
import { check } from 'k6';

// Per-endpoint latency trends (in milliseconds)
export const cartAddDuration = new Trend('cart_add_duration', true);
export const orderCreateDuration = new Trend('order_create_duration', true);
export const orderConfirmDuration = new Trend('order_confirm_duration', true);
export const customerOrdersDuration = new Trend('customer_orders_duration', true);

// Business metrics
export const conflictCount = new Counter('conflict_count');
export const conflictRate = new Rate('conflict_rate');

/**
 * Records whether a response was a 409 conflict and updates conflict metrics.
 * Call this after any order-creation request.
 */
export function recordConflict(res) {
  const isConflict = res.status === 409;
  conflictRate.add(isConflict);
  if (isConflict) conflictCount.add(1);
  return isConflict;
}

/** Checks that the response status is 2xx. */
export function checkOk(res, tag) {
  return check(res, {
    [`${tag ?? 'response'} is 2xx`]: (r) => r.status >= 200 && r.status < 300,
  });
}

/**
 * Checks that a conflict was handled gracefully (200 or 409, never 5xx).
 * For order creation under contention: 409 is an expected, correct response.
 */
export function checkConflictHandled(res) {
  return check(res, {
    'conflict handled (2xx or 409)': (r) => r.status === 200 || r.status === 409 || r.status === 201 || r.status === 204,
  });
}

/**
 * Checks that available stock in the response body is non-negative.
 * A negative available value indicates an oversell bug in the CRUD system.
 */
export function checkNoOversell(res) {
  return check(res, {
    'no oversell (available >= 0)': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.available === undefined || body.available >= 0;
      } catch {
        return true; // non-stock endpoint, skip
      }
    },
  });
}
