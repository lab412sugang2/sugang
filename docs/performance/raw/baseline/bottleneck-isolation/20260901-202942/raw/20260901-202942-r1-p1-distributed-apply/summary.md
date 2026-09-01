# A/B/C/D 병목 분리 테스트 요약

- Run ID: 20260901-202942-r1-p1-distributed-apply
- Base URL: https://sugang-5de3.onrender.com
- Scenarios: distributed-apply
- VUs: 20
- Ramp: 30s
- Hold: 2m30s
- Observation window: 1m

| Scenario | VUs | k6 exit | success p95 ms | success p99 ms | fail % | req/s | rejected % | Process CPU max % | System CPU max % | Heap max % | Tomcat busy/max % | GC pause sec | Hikari active/max % | Hikari pending max | Hikari acquire avg ms | Hikari acquire max ms | Hikari usage avg ms | Hikari usage max ms | Hikari timeouts | DB applied | DB actual | Count mismatch |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| distributed-apply | 20 | 0 | 1414.38 | 1637.17 | 0.00 | 8.56 | 0.00 | 40.80 | 40.26 | 46.06 | 7.00 | 0.02 | 100.00 | 3.00 | 34.67 | 601.56 | 710.31 | 5612.00 | 0.00 | 1615 | 1615 | 0 |
