import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'https://sugang-5de3.onrender.com';
const VUS_TARGET = Number(__ENV.VUS_TARGET || '10');
const RAMP_DURATION = __ENV.RAMP_DURATION || '30s';
const HOLD_DURATION = __ENV.HOLD_DURATION || '2m30s';
const scenarioRequestDuration = new Trend('scenario_request_duration', true);
const scenarioRequestFailed = new Rate('scenario_request_failed');
const scenarioRequests = new Counter('scenario_requests');

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
    scenario_request_failed: ['rate<0.01'],
    scenario_request_duration: ['p(95)<1000', 'p(99)<2000'],
  },
};

export default function () {
  const response = http.get(`${BASE_URL}/performance/ping`, {
    tags: { endpoint: 'perf_ping' },
  });
  const succeeded = response.status === 200;
  scenarioRequestDuration.add(response.timings.duration);
  scenarioRequestFailed.add(!succeeded);
  scenarioRequests.add(1);
  check(response, { 'ping returns 200': () => succeeded });
  sleep(1);
}
