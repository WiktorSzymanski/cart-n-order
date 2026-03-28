/**
 * D1 — Data Preparation
 *
 * Seeds the target system with:
 *   - 10 000 products: 80% high-stock (200 units), 20% hot low-stock (10–50 units)
 *   - 1 000 customer UUIDs (generated server-side)
 *
 * Writes k6/scripts/test-data.json via handleSummary so that the scenario
 * scripts can load it with open().
 *
 * Usage:
 *   k6 run k6/scripts/data-prep.js -e BASE_URL=http://localhost:8080
 *
 * Runtime: ~3–5 min depending on server throughput.
 */

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const HEADERS = { 'Content-Type': 'application/json' };

const TOTAL_PRODUCTS = 10_000;
const HOT_COUNT = 2_000; // 20% hot (low stock)
const BATCH_SIZE = 50;   // parallel requests per http.batch() call

export const options = {
  // Single VU — setup() does all the work; default() is a no-op.
  vus: 1,
  iterations: 1,
  // setup() can take a while when creating 10k products.
  setupTimeout: '15m',
};

export function setup() {
  console.log('=== D1: Creating customers ===');
  const custRes = http.post(
    `${BASE_URL}/admin/customers`,
    JSON.stringify({ count: 1000 }),
    { headers: HEADERS }
  );
  check(custRes, { 'customers created (201)': (r) => r.status === 201 });
  const customerIds = JSON.parse(custRes.body).customerIds;
  console.log(`Created ${customerIds.length} customers`);

  console.log('=== D1: Creating products (batched) ===');
  const productIds = [];
  const hotProductIds = [];

  for (let i = 0; i < TOTAL_PRODUCTS; i += BATCH_SIZE) {
    const batch = [];
    for (let j = i; j < Math.min(i + BATCH_SIZE, TOTAL_PRODUCTS); j++) {
      const isHot = j < HOT_COUNT;
      const stock = isHot
        ? 10 + Math.floor(Math.random() * 41)  // 10–50
        : 200;
      batch.push([
        'POST',
        `${BASE_URL}/admin/products`,
        JSON.stringify({ name: `product-${j}`, initialStock: stock }),
        { headers: HEADERS },
      ]);
    }

    const responses = http.batch(batch);
    responses.forEach((res, idx) => {
      if (res.status === 201) {
        const id = JSON.parse(res.body).id;
        const globalIdx = i + idx;
        productIds.push(id);
        if (globalIdx < HOT_COUNT) hotProductIds.push(id);
      } else {
        console.warn(`Product creation failed: status=${res.status} body=${res.body}`);
      }
    });

    if ((i / BATCH_SIZE) % 20 === 0) {
      console.log(`  ${productIds.length}/${TOTAL_PRODUCTS} products created…`);
    }
    sleep(0.05); // small pause to avoid overwhelming the server
  }

  console.log(`Created ${productIds.length} products (${hotProductIds.length} hot)`);
  return { customerIds, productIds, hotProductIds };
}

// No-op: all work is done in setup().
export default function () {}

/**
 * Writes test-data.json so scenario scripts can load it with open().
 * data.setup_data is the return value of setup() (k6 v0.35+).
 * Path is relative to the working directory from which k6 is invoked.
 */
export function handleSummary(data) {
  const testData = data.setup_data;
  if (!testData) {
    console.error('setup_data missing from summary — test-data.json not written');
    return {};
  }
  // Output path is relative to the working directory from which k6 is invoked.
  // Override with OUTPUT_PATH env var if running from a directory other than the project root.
  const path = __ENV.OUTPUT_PATH || 'k6/scripts/test-data.json';
  console.log(`Writing ${path} (${testData.productIds.length} products, ${testData.customerIds.length} customers)`);
  return {
    [path]: JSON.stringify(testData, null, 2),
    stdout: `\nData prep complete. Run scenario scripts with:\n  k6 run k6/scripts/scenarios/B1.js -e BASE_URL=${BASE_URL}\n`,
  };
}
