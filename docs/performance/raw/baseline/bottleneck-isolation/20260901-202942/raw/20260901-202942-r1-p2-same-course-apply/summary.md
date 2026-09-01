# A/B/C/D 병목 분리 테스트 요약

- Run ID: 20260901-202942-r1-p2-same-course-apply
- Base URL: https://sugang-5de3.onrender.com
- Scenarios: same-course-apply
- VUs: 20
- Ramp: 30s
- Hold: 2m30s
- Observation window: 1m

| Scenario | VUs | k6 exit | success p95 ms | success p99 ms | fail % | req/s | rejected % | Process CPU max % | System CPU max % | Heap max % | Tomcat busy/max % | GC pause sec | Hikari active/max % | Hikari pending max | Hikari acquire avg ms | Hikari acquire max ms | Hikari usage avg ms | Hikari usage max ms | Hikari timeouts | DB applied | DB actual | Count mismatch |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| same-course-apply | 20 | 0 | 2635.60 | 2856.42 | 0.00 | 5.34 | 0.00 | 11.20 | 11.65 | 47.75 | 7.50 | 0.02 | 100.00 | 4.00 | 578.74 | 787.34 | 1748.76 | 1982.00 | 0.00 | 987 | 987 | 0 |
