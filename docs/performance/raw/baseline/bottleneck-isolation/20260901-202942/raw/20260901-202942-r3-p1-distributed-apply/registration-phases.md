# 수강신청 트랜잭션 단계별 시간

| Scenario | VUs | transaction avg ms | transaction p95 ms | conditional UPDATE avg ms | conditional UPDATE p95 ms | saveAndFlush avg ms | saveAndFlush p95 ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| distributed-apply | 20 | 608.80 | 709.08 | 92.82 | 140.13 | 85.91 | 107.25 |
