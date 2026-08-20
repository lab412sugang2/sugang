import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'https://sugang-5de3.onrender.com';
const VUS_TARGET = Number(__ENV.VUS_TARGET || '10');
const RAMP_DURATION = __ENV.RAMP_DURATION || '30s';
const HOLD_DURATION = __ENV.HOLD_DURATION || '2m30s';

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    ping: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: RAMP_DURATION, target: VUS_TARGET },
        { duration: HOLD_DURATION, target: VUS_TARGET },
      ],
      gracefulRampDown: '0s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
  },
};

export default function () {
  const response = http.get(`${BASE_URL}/performance/ping`, {
    tags: { endpoint: 'perf_ping' },
  });
  check(response, { 'ping returns 200': (result) => result.status === 200 });
  sleep(1);
}
