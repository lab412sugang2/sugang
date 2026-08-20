# A/B/C/D 병목 분리 테스트 요약

- Run ID: 20260820-214149
- Base URL: https://sugang-5de3.onrender.com
- Scenarios: ping course-read distributed-apply same-course-apply
- VUs: 10 50 100
- Ramp: 30s
- Hold: 2m30s
- Observation window: 1m

| Scenario | VUs | k6 exit | p95 ms | p99 ms | fail % | req/s | rejected % | Process CPU max % | System CPU max % | Heap max % | Tomcat busy/max % | GC pause sec | Hikari active/max % | Hikari pending max | Hikari timeouts | DB applied | DB actual | Count mismatch |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| ping | 10 | 0 | 222.46 | 258.70 | 0.00 | 7.76 | - | 13.00 | 13.10 | 45.14 | 1.00 | 0.01 | 0.00 | 0.00 | 0.00 | - | - | - |
| ping | 50 | 0 | 217.45 | 251.77 | 0.00 | 39.16 | - | 40.45 | 40.97 | 45.31 | 0.50 | 0.01 | 0.00 | 0.00 | 0.00 | - | - | - |
| ping | 100 | 0 | 220.48 | 258.82 | 0.00 | 78.13 | - | 13.88 | 13.70 | 42.13 | 0.50 | 0.01 | 0.00 | 0.00 | 0.00 | - | - | - |
| course-read | 10 | 0 | 870.03 | 927.89 | 0.00 | 5.07 | - | 9.13 | 8.67 | 47.24 | 4.50 | 0.00 | 80.00 | 0.00 | 0.00 | - | - | - |
| course-read | 50 | 99 | 1893.44 | 772429.15 | 1.61 | 3.25 | - | 14.80 | 14.72 | 48.49 | 15.50 | 0.02 | 100.00 | 20.00 | 0.00 | - | - | - |
| course-read | 100 | 141 | NaN | NaN | NaN | NaN | - | 31.60 | 31.46 | 56.61 | 40.50 | 0.04 | 100.00 | 70.00 | 0.00 | - | - | - |
