# A/B/C/D 병목 분리 테스트 요약

- Run ID: 20260903-1350-duplicate-query-after-stable-r3-p2-same-course-apply
- Base URL: https://sugang-5de3.onrender.com
- Scenarios: same-course-apply
- VUs: 20
- Ramp: 30s
- Hold: 2m30s
- Observation window: 1m

| Scenario | VUs | k6 exit | success p95 ms | success p99 ms | fail % | req/s | rejected % | Process CPU max % | System CPU max % | Heap max % | Tomcat busy/max % | GC pause sec | Hikari active/max % | Hikari pending max | Hikari acquire avg ms | Hikari acquire max ms | Hikari usage avg ms | Hikari usage max ms | Hikari timeouts | DB applied | DB actual | Count mismatch |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| same-course-apply | 20 | 0 | 2460.92 | 2540.35 | 0.00 | 5.50 | 0.00 | 23.61 | 25.18 | 50.12 | 7.50 | 0.02 | 100.00 | 4.00 | 528.16 | 1103.36 | 1699.03 | 1806.00 | 0.00 | 1017 | 1017 | 0 |
