import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  scenarios: {
    ramp: {
      executor: 'ramping-vus',
      startVUs: 20,
      stages: [
        { duration: '2m', target: 100 }, { duration: '8m', target: 100 },
        { duration: '2m', target: 300 }, { duration: '8m', target: 300 },
        { duration: '2m', target: 500 }, { duration: '8m', target: 500 },
        { duration: '2m', target: 1000 }, { duration: '8m', target: 1000 },
        { duration: '2m', target: 1500 }, { duration: '8m', target: 1500 }
      ],
      gracefulRampDown: '30s'
    }
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<800', 'p(99)<1500'],
    'http_req_duration{endpoint:apply}': ['p(95)<1200']
  }
};

function hitHome() {
  return http.get(`${BASE}/`, { tags: { endpoint: 'home' } });
}

function hitPopup() {
  return http.get(`${BASE}/findGsLctTmtbl.do`, { tags: { endpoint: 'popup' } });
}

function hitApply(courseId) {
  const body = `courseId=${courseId}`;
  const params = {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    tags: { endpoint: 'apply' }
  };
  return http.post(`${BASE}/saveTkcrsApl.do`, body, params);
}

export default function () {
  const r = Math.random();

  if (r < 0.7) {
    const res = hitHome();
    check(res, { 'home response handled': (x) => x.status === 200 || x.status === 302 });
  } else if (r < 0.8) {
    const res = hitPopup();
    check(res, { 'popup response handled': (x) => x.status === 200 || x.status === 302 });
  } else {
    const res = hitApply(1);
    check(res, { 'apply response handled': (x) => [200, 302, 400].includes(x.status) });
  }

  sleep(1 + Math.random() * 2);
}
