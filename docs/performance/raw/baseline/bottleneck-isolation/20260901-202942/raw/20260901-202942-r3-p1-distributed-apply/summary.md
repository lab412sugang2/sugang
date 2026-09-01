# A/B/C/D 병목 분리 테스트 요약

- Run ID: 20260901-202942-r3-p1-distributed-apply
- Base URL: https://sugang-5de3.onrender.com
- Scenarios: distributed-apply
- VUs: 20
- Ramp: 30s
- Hold: 2m30s
- Observation window: 1m

| Scenario | VUs | k6 exit | success p95 ms | success p99 ms | fail % | req/s | rejected % | Process CPU max % | System CPU max % | Heap max % | Tomcat busy/max % | GC pause sec | Hikari active/max % | Hikari pending max | Hikari acquire avg ms | Hikari acquire max ms | Hikari usage avg ms | Hikari usage max ms | Hikari timeouts | DB applied | DB actual | Count mismatch |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| distributed-apply | 20 | 0 | 1014.12 | 1114.33 | 0.00 | 9.31 | 0.00 | 7.60 | 7.01 | 45.91 | 5.50 | 0.02 | 100.00 | 0.00 | 14.41 | 168.64 | 694.10 | 5320.00 | 0.00 | 1751 | 1751 | 0 |
