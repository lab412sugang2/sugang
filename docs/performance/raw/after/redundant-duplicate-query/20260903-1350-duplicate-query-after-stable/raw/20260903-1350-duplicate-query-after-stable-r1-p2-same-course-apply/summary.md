# A/B/C/D 병목 분리 테스트 요약

- Run ID: 20260903-1350-duplicate-query-after-stable-r1-p2-same-course-apply
- Base URL: https://sugang-5de3.onrender.com
- Scenarios: same-course-apply
- VUs: 20
- Ramp: 30s
- Hold: 2m30s
- Observation window: 1m

| Scenario | VUs | k6 exit | success p95 ms | success p99 ms | fail % | req/s | rejected % | Process CPU max % | System CPU max % | Heap max % | Tomcat busy/max % | GC pause sec | Hikari active/max % | Hikari pending max | Hikari acquire avg ms | Hikari acquire max ms | Hikari usage avg ms | Hikari usage max ms | Hikari timeouts | DB applied | DB actual | Count mismatch |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| same-course-apply | 20 | 0 | 2673.16 | 2906.56 | 0.00 | 5.39 | 0.00 | 82.67 | 81.98 | 51.17 | 7.50 | 0.13 | 100.00 | 4.00 | 567.98 | 1408.46 | 1739.56 | 2100.00 | 0.00 | 999 | 999 | 0 |
