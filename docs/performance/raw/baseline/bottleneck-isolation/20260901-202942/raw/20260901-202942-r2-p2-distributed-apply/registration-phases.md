# 수강신청 트랜잭션 단계별 시간

| Scenario | VUs | transaction avg ms | transaction p95 ms | conditional UPDATE avg ms | conditional UPDATE p95 ms | saveAndFlush avg ms | saveAndFlush p95 ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| distributed-apply | 20 | 610.56 | 709.97 | 93.36 | 138.97 | 86.15 | 107.70 |
