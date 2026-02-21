import http from 'k6/http';
import { check, sleep } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
  stages: [
    { duration: '30s', target: 20 }, // Ramp up to 20 users
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
  const vertreterNr = `V-${eventId.substring(0, 8)}`;
  const vertreterId = eventId;

  // Use low-level CloudEvents API since the example command layer was removed
  const payload = JSON.stringify({
    id: eventId,
    source: '/k6-benchmark',
    type: 'space.maatini.vertreter.created',
    dataschema: 'space.maatini.vertreter.created.json',
    subject: vertreterId,
    aggregateVersion: 1,
    data: {
      vertreterNr: vertreterNr,
      name: `Benchmark User ${vertreterNr}`,
      status: "AKTIV"
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
    'is status 201': (r) => r.status === 201 || r.status === 202,
  });

  // 2. Read Event (Immediate Consistency for Event Store)
  const resGet = http.get(`${BASE_URL}/events/${eventId}`);

  check(resGet, {
    'is status 200': (r) => r.status === 200,
  });
}
