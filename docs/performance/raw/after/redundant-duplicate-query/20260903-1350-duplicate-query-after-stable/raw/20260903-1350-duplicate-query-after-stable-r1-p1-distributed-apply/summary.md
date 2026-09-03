# A/B/C/D 병목 분리 테스트 요약

- Run ID: 20260903-1350-duplicate-query-after-stable-r1-p1-distributed-apply
- Base URL: https://sugang-5de3.onrender.com
- Scenarios: distributed-apply
- VUs: 20
- Ramp: 30s
- Hold: 2m30s
- Observation window: 1m

| Scenario | VUs | k6 exit | success p95 ms | success p99 ms | fail % | req/s | rejected % | Process CPU max % | System CPU max % | Heap max % | Tomcat busy/max % | GC pause sec | Hikari active/max % | Hikari pending max | Hikari acquire avg ms | Hikari acquire max ms | Hikari usage avg ms | Hikari usage max ms | Hikari timeouts | DB applied | DB actual | Count mismatch |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| distributed-apply | 20 | 0 | 1410.09 | 1783.12 | 0.00 | 8.93 | 0.00 | 98.77 | 99.47 | 50.63 | 5.00 | 0.14 | 100.00 | 0.00 | 25.11 | 803.69 | 651.12 | 5359.00 | 0.00 | 1679 | 1679 | 0 |
