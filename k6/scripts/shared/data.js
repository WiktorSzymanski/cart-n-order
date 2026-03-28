import { SharedArray } from 'k6/data';

// Loaded at init time — requires test-data.json to exist (run data-prep.js first).
// Path is resolved relative to this file: k6/scripts/test-data.json
const _raw = JSON.parse(open('../test-data.json'));

export const customerIds = new SharedArray('customers', () => _raw.customerIds);
export const productIds = new SharedArray('products', () => _raw.productIds);
export const hotProductIds = new SharedArray('hotProducts', () => _raw.hotProductIds);

// Top 1% of customers (first 10) — used by R1 for skewed read traffic
export const activeCustomerIds = new SharedArray('activeCustomers', () => _raw.customerIds.slice(0, 10));

/** Returns a uniformly random element from arr. */
export function randomFrom(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

/** Returns a random integer in [min, max] inclusive. */
export function randInt(min, max) {
  return min + Math.floor(Math.random() * (max - min + 1));
}
