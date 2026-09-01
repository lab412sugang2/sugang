# 수강신청 트랜잭션 단계별 시간

| Scenario | VUs | transaction avg ms | transaction p95 ms | conditional UPDATE avg ms | conditional UPDATE p95 ms | saveAndFlush avg ms | saveAndFlush p95 ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| distributed-apply | 20 | 623.45 | 714.97 | 97.35 | 175.44 | 87.47 | 109.03 |
