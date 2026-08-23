# A/B/C/D 병목 분리 테스트 요약

- Run ID: 20260823-142751
- Base URL: https://sugang-5de3.onrender.com
- Scenarios: distributed-apply same-course-apply
- VUs: 10
- Ramp: 30s
- Hold: 2m30s
- Observation window: 1m

| Scenario | VUs | k6 exit | success p95 ms | success p99 ms | fail % | req/s | rejected % | Process CPU max % | System CPU max % | Heap max % | Tomcat busy/max % | GC pause sec | Hikari active/max % | Hikari pending max | Hikari acquire avg ms | Hikari acquire max ms | Hikari usage avg ms | Hikari usage max ms | Hikari timeouts | DB applied | DB actual | Count mismatch |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| distributed-apply | 10 | 0 | 1107.45 | 1199.31 | 0.00 | 4.40 | 0.00 | 22.86 | 22.99 | 62.61 | 4.50 | 0.01 | 80.00 | 0.00 | 77.08 | 99.41 | 736.37 | 5948.00 | 0.00 | 830 | 830 | 0 |
| same-course-apply | 10 | 0 | 1117.17 | 1231.02 | 0.00 | 4.49 | 0.00 | 15.20 | 14.73 | 63.07 | 3.00 | 0.01 | 50.00 | 0.00 | 10.29 | 114.64 | 785.41 | 4290.00 | 0.00 | 823 | 823 | 0 |
