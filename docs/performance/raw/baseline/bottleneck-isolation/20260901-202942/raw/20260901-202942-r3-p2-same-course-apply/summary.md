# A/B/C/D 병목 분리 테스트 요약

- Run ID: 20260901-202942-r3-p2-same-course-apply
- Base URL: https://sugang-5de3.onrender.com
- Scenarios: same-course-apply
- VUs: 20
- Ramp: 30s
- Hold: 2m30s
- Observation window: 1m

| Scenario | VUs | k6 exit | success p95 ms | success p99 ms | fail % | req/s | rejected % | Process CPU max % | System CPU max % | Heap max % | Tomcat busy/max % | GC pause sec | Hikari active/max % | Hikari pending max | Hikari acquire avg ms | Hikari acquire max ms | Hikari usage avg ms | Hikari usage max ms | Hikari timeouts | DB applied | DB actual | Count mismatch |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| same-course-apply | 20 | 0 | 2512.98 | 2581.04 | 0.00 | 5.40 | 0.00 | 4.40 | 4.22 | 49.54 | 7.50 | 0.01 | 100.00 | 4.00 | 564.05 | 607.73 | 1730.75 | 1759.00 | 0.00 | 999 | 999 | 0 |
