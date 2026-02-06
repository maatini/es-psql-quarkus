import http from 'k6/http';
import { check, sleep } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
  stages: [
    { duration: '30s', target: 20 }, // Rope up to 20 users
    { duration: '1m', target: 20 },  // Stay at 20 users for 1 min
    { duration: '10s', target: 0 },  // Scale down
  ],
  thresholds: {
    http_req_duration: ['p(95)<100'], // 95% of requests must complete below 100ms
  },
};

const BASE_URL = 'http://localhost:8080';

export default function () {
  const eventId = uuidv4();
  const vertreterId = `bench-${eventId.substring(0, 8)}`;

  const payload = JSON.stringify({
    id: eventId,
    source: '/k6-benchmark',
    type: 'de.vertreter.created',
    subject: vertreterId,
    data: {
      id: vertreterId,
      name: `Benchmark User ${vertreterId}`,
      email: `${vertreterId}@benchmark.com`
    }
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  // 1. Ingest Event
  const resPost = http.post(`${BASE_URL}/events`, payload, params);

  check(resPost, {
    'is status 201': (r) => r.status === 201,
  });

  // 2. Read Aggregate with Retry (Eventual Consistency)
  // Async projector runs real-time. We poll logic:
  // Happy Path: Data is there immediately -> No sleep -> Fast p95.
  // Backlog Path: Data is delayed -> We wait 100ms.
  let resGet;
  const maxRetries = 50; // Wait up to 5s
  for (let i = 0; i < maxRetries; i++) {
    resGet = http.get(`${BASE_URL}/aggregates/vertreter/${vertreterId}`);
    if (resGet.status === 200) {
      break;
    }
    sleep(0.1); // Poll every 100ms (Relieve server load)
  }

  check(resGet, {
    'is status 200': (r) => r.status === 200,
    'has correct id': (r) => r.json('id') === vertreterId,
  });
}
