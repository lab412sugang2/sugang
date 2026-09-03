# A/B/C/D 병목 분리 테스트 요약

- Run ID: 20260903-1350-duplicate-query-after-stable-r2-p1-same-course-apply
- Base URL: https://sugang-5de3.onrender.com
- Scenarios: same-course-apply
- VUs: 20
- Ramp: 30s
- Hold: 2m30s
- Observation window: 1m

| Scenario | VUs | k6 exit | success p95 ms | success p99 ms | fail % | req/s | rejected % | Process CPU max % | System CPU max % | Heap max % | Tomcat busy/max % | GC pause sec | Hikari active/max % | Hikari pending max | Hikari acquire avg ms | Hikari acquire max ms | Hikari usage avg ms | Hikari usage max ms | Hikari timeouts | DB applied | DB actual | Count mismatch |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| same-course-apply | 20 | 0 | 2684.34 | 2832.05 | 0.00 | 5.44 | 0.00 | 88.00 | 86.51 | 50.46 | 8.00 | 0.12 | 100.00 | 4.00 | 573.02 | 1188.05 | 1742.18 | 2056.00 | 0.00 | 1007 | 1007 | 0 |
