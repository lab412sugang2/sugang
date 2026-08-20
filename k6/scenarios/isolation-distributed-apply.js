import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'https://sugang-5de3.onrender.com';
const PERFORMANCE_TEST_TOKEN = __ENV.PERFORMANCE_TEST_TOKEN || '';
const VUS_TARGET = Number(__ENV.VUS_TARGET || '10');
const RAMP_DURATION = __ENV.RAMP_DURATION || '30s';
const HOLD_DURATION = __ENV.HOLD_DURATION || '2m30s';
const COURSE_COUNT = Number(__ENV.COURSE_COUNT || '20');
const CAPACITY = Number(__ENV.CAPACITY || '100000');
const RUN_ID = __ENV.RUN_ID || String(Date.now());
const RUN_TOKEN = compactRunToken(RUN_ID);
const applicationRejected = new Rate('application_rejected');
const scenarioRequestDuration = new Trend('scenario_request_duration', true);
const scenarioRequestFailed = new Rate('scenario_request_failed');
const scenarioRequests = new Counter('scenario_requests');

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    distributed_apply: {
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
    scenario_request_duration: ['p(95)<2000', 'p(99)<4000'],
    application_rejected: ['rate==0'],
  },
};

function compactRunToken(value) {
  let hash = 0;
  for (let index = 0; index < value.length; index += 1) {
    hash = ((hash << 5) - hash + value.charCodeAt(index)) | 0;
  }
  return (hash >>> 0).toString(36);
}

function studentId() {
  return `PD-${RUN_TOKEN}-${exec.vu.idInTest.toString(36)}-${exec.scenario.iterationInTest.toString(36)}`;
}

function jsonParams(endpoint) {
  return {
    headers: {
      'Content-Type': 'application/json',
      'X-Performance-Test-Token': PERFORMANCE_TEST_TOKEN,
    },
    tags: { endpoint },
  };
}

function cleanupFixtures() {
  return http.post(
    `${BASE_URL}/performance/fixtures/cleanup`,
    null,
    jsonParams('perf_fixture_cleanup'),
  );
}

export function setup() {
  if (!PERFORMANCE_TEST_TOKEN) {
    throw new Error('PERFORMANCE_TEST_TOKEN is required');
  }
  cleanupFixtures();

  const response = http.post(
    `${BASE_URL}/performance/fixtures/distributed-courses`,
    JSON.stringify({ courseCount: COURSE_COUNT, capacity: CAPACITY }),
    jsonParams('perf_fixture_distributed'),
  );

  if (!check(response, { 'distributed fixture is ready': (result) => result.status === 200 })) {
    throw new Error(`distributed fixture failed: status=${response.status}`);
  }
  return { courseIds: response.json('courses').map((course) => course.id) };
}

export default function (data) {
  const courseId = data.courseIds[
    (exec.vu.idInTest + exec.scenario.iterationInTest) % data.courseIds.length
  ];
  const response = http.post(
    `${BASE_URL}/performance/apply`,
    JSON.stringify({ studentId: studentId(), courseId }),
    jsonParams('perf_distributed_apply'),
  );
  const httpSucceeded = response.status === 200;
  const accepted = response.status === 200 && response.json('result') === 'success';

  scenarioRequestDuration.add(response.timings.duration);
  scenarioRequestFailed.add(!httpSucceeded);
  scenarioRequests.add(1);
  applicationRejected.add(!accepted);
  check(response, { 'distributed application succeeds': () => accepted });
  sleep(1);
}

export function teardown() {
  if (__ENV.AUTO_CLEANUP !== 'false') {
    check(cleanupFixtures(), { 'fixtures are cleaned': (result) => result.status === 200 });
  }
}
