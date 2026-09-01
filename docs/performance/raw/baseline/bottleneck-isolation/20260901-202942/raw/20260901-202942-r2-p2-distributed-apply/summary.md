# A/B/C/D 병목 분리 테스트 요약

- Run ID: 20260901-202942-r2-p2-distributed-apply
- Base URL: https://sugang-5de3.onrender.com
- Scenarios: distributed-apply
- VUs: 20
- Ramp: 30s
- Hold: 2m30s
- Observation window: 1m

| Scenario | VUs | k6 exit | success p95 ms | success p99 ms | fail % | req/s | rejected % | Process CPU max % | System CPU max % | Heap max % | Tomcat busy/max % | GC pause sec | Hikari active/max % | Hikari pending max | Hikari acquire avg ms | Hikari acquire max ms | Hikari usage avg ms | Hikari usage max ms | Hikari timeouts | DB applied | DB actual | Count mismatch |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| distributed-apply | 20 | 0 | 1025.71 | 1117.61 | 0.00 | 9.29 | 0.00 | 43.20 | 43.27 | 47.76 | 5.50 | 0.02 | 100.00 | 0.00 | 23.01 | 178.03 | 696.11 | 5602.00 | 0.00 | 1748 | 1748 | 0 |
