# A/B/C/D 병목 분리 테스트 요약

- Run ID: 20260820-221513
- Base URL: https://sugang-5de3.onrender.com
- Scenarios: course-read
- VUs: 20 30 40
- Ramp: 30s
- Hold: 2m30s
- Observation window: 1m

| Scenario | VUs | k6 exit | success p95 ms | success p99 ms | fail % | req/s | rejected % | Process CPU max % | System CPU max % | Heap max % | Tomcat busy/max % | GC pause sec | Hikari active/max % | Hikari pending max | Hikari timeouts | DB applied | DB actual | Count mismatch |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| course-read | 20 | 0 | 848.63 | 1070.91 | 0.00 | 9.64 | - | 11.20 | 10.98 | 57.17 | 6.00 | 0.01 | 100.00 | 1.00 | 0.00 | - | - | - |
| course-read | 30 | 0 | 793.39 | 831.70 | 0.00 | 15.83 | - | 9.80 | 9.90 | 56.36 | 6.50 | 0.01 | 100.00 | 2.00 | 0.00 | - | - | - |
| course-read | 40 | 0 | 1273.15 | 1311.35 | 0.00 | 17.04 | - | 7.60 | 7.87 | 56.38 | 10.50 | 0.01 | 100.00 | 10.00 | 0.00 | - | - | - |
