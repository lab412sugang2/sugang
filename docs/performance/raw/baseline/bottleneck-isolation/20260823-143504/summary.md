# A/B/C/D 병목 분리 테스트 요약

- Run ID: 20260823-143504
- Base URL: https://sugang-5de3.onrender.com
- Scenarios: distributed-apply same-course-apply
- VUs: 20
- Ramp: 30s
- Hold: 2m30s
- Observation window: 1m

| Scenario | VUs | k6 exit | success p95 ms | success p99 ms | fail % | req/s | rejected % | Process CPU max % | System CPU max % | Heap max % | Tomcat busy/max % | GC pause sec | Hikari active/max % | Hikari pending max | Hikari acquire avg ms | Hikari acquire max ms | Hikari usage avg ms | Hikari usage max ms | Hikari timeouts | DB applied | DB actual | Count mismatch |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| distributed-apply | 20 | 0 | 1095.57 | 1350.19 | 0.00 | 9.02 | 0.00 | 27.20 | 27.29 | 63.65 | 5.50 | 0.02 | 100.00 | 0.00 | 26.07 | 593.14 | 737.60 | 5728.00 | 0.00 | 1700 | 1700 | 0 |
| same-course-apply | 20 | 0 | 2734.98 | 2994.33 | 0.00 | 5.10 | 0.00 | 8.00 | 7.75 | 64.33 | 7.50 | 0.01 | 100.00 | 4.00 | 657.28 | 1233.74 | 1833.59 | 3995.00 | 0.00 | 943 | 943 | 0 |
