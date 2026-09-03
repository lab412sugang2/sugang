# A/B/C/D 병목 분리 테스트 요약

- Run ID: 20260903-1350-duplicate-query-after-stable-r3-p1-distributed-apply
- Base URL: https://sugang-5de3.onrender.com
- Scenarios: distributed-apply
- VUs: 20
- Ramp: 30s
- Hold: 2m30s
- Observation window: 1m

| Scenario | VUs | k6 exit | success p95 ms | success p99 ms | fail % | req/s | rejected % | Process CPU max % | System CPU max % | Heap max % | Tomcat busy/max % | GC pause sec | Hikari active/max % | Hikari pending max | Hikari acquire avg ms | Hikari acquire max ms | Hikari usage avg ms | Hikari usage max ms | Hikari timeouts | DB applied | DB actual | Count mismatch |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| distributed-apply | 20 | 0 | 937.67 | 1033.34 | 0.00 | 9.70 | 0.00 | 61.33 | 61.28 | 48.90 | 6.50 | 0.02 | 100.00 | 2.00 | 28.54 | 252.15 | 604.41 | 5668.00 | 0.00 | 1828 | 1828 | 0 |
