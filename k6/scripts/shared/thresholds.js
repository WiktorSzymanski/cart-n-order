/**
 * Global pass/fail thresholds shared across all scenarios.
 * Spread these into each scenario's options.thresholds.
 *
 * Targets (from spec):
 *   p50 < 200ms, p95 < 800ms, p99 < 2000ms
 *   error rate < 5%
 *   conflict rate < 5%
 */
export const thresholds = {
  http_req_duration: ['p(50)<200', 'p(95)<800', 'p(99)<2000'],
  http_req_failed: ['rate<0.05'],
  conflict_rate: ['rate<0.05'],
  // Per-endpoint latency thresholds
  cart_add_duration: ['p(95)<500'],
  order_create_duration: ['p(95)<800'],
  order_confirm_duration: ['p(95)<500'],
};
