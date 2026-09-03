package sugang.service;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.hikari.maximum-pool-size=32",
        "spring.datasource.hikari.minimum-idle=10",
        "spring.datasource.hikari.connection-timeout=10000"
})
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = "jdbc:mysql:.*")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConcurrencyStrategyComparisonTest {

    private static final int REQUEST_COUNT = 100;
    private static final int THREAD_COUNT = 100;
    private static final int CAPACITY = 10;
    private static final int REPETITIONS = 3;
    private static final int MAX_OPTIMISTIC_RETRIES = 100;
    private static final Strategy[][] EXECUTION_ORDERS = {
            {Strategy.PESSIMISTIC_LOCK, Strategy.OPTIMISTIC_LOCK, Strategy.CONDITIONAL_UPDATE},
            {Strategy.OPTIMISTIC_LOCK, Strategy.CONDITIONAL_UPDATE, Strategy.PESSIMISTIC_LOCK},
            {Strategy.CONDITIONAL_UPDATE, Strategy.PESSIMISTIC_LOCK, Strategy.OPTIMISTIC_LOCK}
    };

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private SeatAllocationExecutor seatAllocationExecutor;

    @BeforeAll
    void setUp() {
        seatAllocationExecutor = new SeatAllocationExecutor(jdbcTemplate, transactionManager);
        seatAllocationExecutor.recreateTables();
        warmUpStrategies();
    }

    @AfterAll
    void tearDown() {
        if (seatAllocationExecutor != null) {
            seatAllocationExecutor.dropTables();
        }
    }

    @Test
    void compareThreeStrategiesUnderTheSameConcurrentRequests() throws Exception {
        List<SimulationResult> results = new ArrayList<>();

        for (int repetition = 1; repetition <= REPETITIONS; repetition++) {
            Strategy[] order = EXECUTION_ORDERS[repetition - 1];
            for (int position = 1; position <= order.length; position++) {
                Strategy strategy = order[position - 1];
                long courseId = seatAllocationExecutor.createCourse(CAPACITY);
                try {
                    SimulationResult result = runSimulation(repetition, position, strategy, courseId,
                            REQUEST_COUNT, THREAD_COUNT);
                    results.add(result);
                    printResult(result);
                } finally {
                    seatAllocationExecutor.deleteCourse(courseId);
                }
            }
        }

        writeArtifacts(results);
        assertAll(results.stream().map(result -> () -> verifyConsistency(result)));
    }

    private void warmUpStrategies() {
        for (Strategy strategy : Strategy.values()) {
            long courseId = seatAllocationExecutor.createCourse(2);
            try {
                runSimulation(0, 0, strategy, courseId, 10, 10);
            } catch (Exception e) {
                throw new IllegalStateException("동시성 전략 워밍업에 실패했습니다: " + strategy, e);
            } finally {
                seatAllocationExecutor.deleteCourse(courseId);
            }
        }
    }

    private SimulationResult runSimulation(
            int repetition,
            int orderPosition,
            Strategy strategy,
            long courseId,
            int requestCount,
            int threadCount
    ) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(requestCount);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger fullRejected = new AtomicInteger();
        AtomicInteger optimisticConflicts = new AtomicInteger();
        AtomicInteger retryExhausted = new AtomicInteger();
        AtomicInteger otherFailures = new AtomicInteger();
        ConcurrentLinkedQueue<Long> allLatencies = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Long> successLatencies = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < requestCount; i++) {
            String studentId = "strategy-r" + repetition + "-" + strategy.name() + "-" + i;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    long startedAt = System.nanoTime();
                    AllocationResult allocation = seatAllocationExecutor.allocate(
                            strategy, studentId, courseId, MAX_OPTIMISTIC_RETRIES
                    );
                    long latencyNanos = System.nanoTime() - startedAt;
                    allLatencies.add(latencyNanos);
                    optimisticConflicts.addAndGet(allocation.optimisticConflicts());

                    switch (allocation.status()) {
                        case SUCCESS -> {
                            success.incrementAndGet();
                            successLatencies.add(latencyNanos);
                        }
                        case FULL -> fullRejected.incrementAndGet();
                        case RETRY_EXHAUSTED -> retryExhausted.incrementAndGet();
                    }
                } catch (Exception e) {
                    otherFailures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        assertTrue(ready.await(10, TimeUnit.SECONDS), "모든 요청 스레드가 시작 준비를 마쳐야 합니다.");
        long batchStartedAt = System.nanoTime();
        start.countDown();
        boolean completed = done.await(60, TimeUnit.SECONDS);
        long durationNanos = System.nanoTime() - batchStartedAt;
        executor.shutdownNow();
        assertTrue(completed, "동시 요청이 60초 안에 종료되어야 합니다.");

        SeatState finalState = seatAllocationExecutor.readState(courseId);
        return new SimulationResult(
                repetition,
                orderPosition,
                strategy,
                requestCount,
                CAPACITY,
                threadCount,
                success.get(),
                fullRejected.get(),
                optimisticConflicts.get(),
                retryExhausted.get(),
                otherFailures.get(),
                nanosToMillis(durationNanos),
                requestCount / (durationNanos / 1_000_000_000.0),
                percentileMillis(allLatencies, 0.50),
                percentileMillis(allLatencies, 0.95),
                percentileMillis(allLatencies, 0.99),
                percentileMillis(successLatencies, 0.95),
                finalState.appliedCount(),
                finalState.applicationCount(),
                finalState.appliedCount() - finalState.applicationCount()
        );
    }

    private void verifyConsistency(SimulationResult result) {
        assertEquals(CAPACITY, result.successCount(), result.strategy() + " 성공 수");
        assertEquals(REQUEST_COUNT - CAPACITY, result.fullRejectedCount(), result.strategy() + " 마감 거절 수");
        assertEquals(0, result.retryExhaustedCount(), result.strategy() + " 재시도 소진 수");
        assertEquals(0, result.otherFailureCount(), result.strategy() + " 기타 실패 수");
        assertEquals(CAPACITY, result.finalAppliedCount(), result.strategy() + " 최종 카운터");
        assertEquals(CAPACITY, result.actualApplicationCount(), result.strategy() + " 실제 신청 행");
        assertEquals(0, result.countMismatch(), result.strategy() + " 카운트 불일치");
    }

    private void printResult(SimulationResult result) {
        System.out.printf(Locale.US,
                "STRATEGY_RESULT repetition=%d order=%d strategy=%s requests=%d capacity=%d threads=%d "
                        + "success=%d full_rejected=%d optimistic_conflicts=%d retry_exhausted=%d other_failures=%d "
                        + "duration_ms=%.2f throughput_rps=%.2f p50_ms=%.2f p95_ms=%.2f p99_ms=%.2f "
                        + "success_p95_ms=%.2f final_count=%d actual_applications=%d count_mismatch=%d%n",
                result.repetition(), result.orderPosition(), result.strategy(), result.requestCount(),
                result.capacity(), result.threadCount(), result.successCount(), result.fullRejectedCount(),
                result.optimisticConflictCount(), result.retryExhaustedCount(), result.otherFailureCount(),
                result.durationMs(), result.throughputRps(), result.p50Ms(), result.p95Ms(), result.p99Ms(),
                result.successP95Ms(), result.finalAppliedCount(), result.actualApplicationCount(),
                result.countMismatch());
    }

    private void writeArtifacts(List<SimulationResult> results) throws IOException {
        String configuredDirectory = System.getenv("CONCURRENCY_RESULT_DIR");
        Path outputDirectory = configuredDirectory == null || configuredDirectory.isBlank()
                ? Path.of("build", "concurrency-strategy-comparison")
                : Path.of(configuredDirectory);
        Files.createDirectories(outputDirectory);

        Files.writeString(outputDirectory.resolve("results.csv"), buildCsv(results), StandardCharsets.UTF_8);
        Files.writeString(outputDirectory.resolve("summary.md"), buildMarkdown(results), StandardCharsets.UTF_8);
    }

    private String buildCsv(List<SimulationResult> results) {
        StringBuilder csv = new StringBuilder();
        csv.append("repetition,order_position,strategy,requests,capacity,threads,success,full_rejected,")
                .append("optimistic_conflicts,retry_exhausted,other_failures,duration_ms,throughput_rps,")
                .append("p50_ms,p95_ms,p99_ms,success_p95_ms,final_count,actual_applications,count_mismatch\n");
        results.forEach(result -> csv.append(String.format(Locale.US,
                "%d,%d,%s,%d,%d,%d,%d,%d,%d,%d,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%d,%d,%d%n",
                result.repetition(), result.orderPosition(), result.strategy(), result.requestCount(),
                result.capacity(), result.threadCount(), result.successCount(), result.fullRejectedCount(),
                result.optimisticConflictCount(), result.retryExhaustedCount(), result.otherFailureCount(),
                result.durationMs(), result.throughputRps(), result.p50Ms(), result.p95Ms(), result.p99Ms(),
                result.successP95Ms(), result.finalAppliedCount(), result.actualApplicationCount(),
                result.countMismatch())));
        return csv.toString();
    }

    private String buildMarkdown(List<SimulationResult> results) {
        DatabaseInfo databaseInfo = seatAllocationExecutor.databaseInfo();
        StringBuilder markdown = new StringBuilder();
        markdown.append("# 동시성 전략 반복 비교 원본 요약\n\n")
                .append("- Java runtime: ").append(System.getProperty("java.version")).append("\n")
                .append("- MySQL: ").append(databaseInfo.version()).append("\n")
                .append("- 격리 수준: ").append(databaseInfo.isolationLevel()).append("\n")
                .append("- 동시 요청: ").append(REQUEST_COUNT).append("개\n")
                .append("- 요청 스레드: ").append(THREAD_COUNT).append("개\n")
                .append("- Hikari 최대 connection: 32개\n")
                .append("- 강의 정원: ").append(CAPACITY).append("명\n")
                .append("- 반복: 각 전략 ").append(REPETITIONS).append("회\n")
                .append("- 낙관적 락: version 기반 CAS, 충돌 시 최대 ")
                .append(MAX_OPTIMISTIC_RETRIES).append("회 재시도\n\n")
                .append("| 반복 | 순서 | 전략 | 성공 | 마감 | 충돌·재시도 | 기타 실패 | 전체 ms | req/s | p95 ms | p99 ms | 성공 p95 ms | 카운트 불일치 |\n")
                .append("| ---: | ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");

        results.forEach(result -> markdown.append(String.format(Locale.US,
                "| %d | %d | %s | %d | %d | %d | %d | %.2f | %.2f | %.2f | %.2f | %.2f | %d |%n",
                result.repetition(), result.orderPosition(), result.strategy(), result.successCount(),
                result.fullRejectedCount(), result.optimisticConflictCount(), result.otherFailureCount(),
                result.durationMs(), result.throughputRps(), result.p95Ms(), result.p99Ms(),
                result.successP95Ms(), result.countMismatch())));

        markdown.append("\n## 전략별 중앙값\n\n")
                .append("| 전략 | 전체 ms | req/s | p95 ms | p99 ms | 성공 p95 ms | 충돌·재시도 |\n")
                .append("| --- | ---: | ---: | ---: | ---: | ---: | ---: |\n");

        for (Strategy strategy : Strategy.values()) {
            List<SimulationResult> strategyResults = results.stream()
                    .filter(result -> result.strategy() == strategy)
                    .toList();
            markdown.append(String.format(Locale.US,
                    "| %s | %.2f | %.2f | %.2f | %.2f | %.2f | %.2f |%n",
                    strategy,
                    median(strategyResults, SimulationResult::durationMs),
                    median(strategyResults, SimulationResult::throughputRps),
                    median(strategyResults, SimulationResult::p95Ms),
                    median(strategyResults, SimulationResult::p99Ms),
                    median(strategyResults, SimulationResult::successP95Ms),
                    median(strategyResults, result -> result.optimisticConflictCount())));
        }

        markdown.append("\n모든 요청의 p95·p99는 성공과 마감 거절을 포함한 사용자 관점의 최종 응답시간이다. ")
                .append("성공 p95는 실제 좌석을 획득한 10개 요청만 대상으로 한다.\n");
        return markdown.toString();
    }

    private double median(List<SimulationResult> results, DoubleValue extractor) {
        List<Double> values = results.stream().map(extractor::get).sorted().toList();
        return values.get(values.size() / 2);
    }

    private static double percentileMillis(ConcurrentLinkedQueue<Long> values, double percentile) {
        if (values.isEmpty()) {
            return 0.0;
        }
        List<Long> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
        int index = (int) Math.ceil(sorted.size() * percentile) - 1;
        return nanosToMillis(sorted.get(Math.max(index, 0)));
    }

    private static double nanosToMillis(long nanos) {
        return BigDecimal.valueOf(nanos)
                .divide(BigDecimal.valueOf(1_000_000), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private enum Strategy {
        PESSIMISTIC_LOCK,
        OPTIMISTIC_LOCK,
        CONDITIONAL_UPDATE
    }

    private enum AllocationStatus {
        SUCCESS,
        FULL,
        CONFLICT,
        RETRY_EXHAUSTED
    }

    private record AllocationResult(AllocationStatus status, int optimisticConflicts) {
    }

    private record SeatSnapshot(int appliedCount, int limitCount, long version) {
    }

    private record SeatState(int appliedCount, int applicationCount) {
    }

    private record DatabaseInfo(String version, String isolationLevel) {
    }

    private record SimulationResult(
            int repetition,
            int orderPosition,
            Strategy strategy,
            int requestCount,
            int capacity,
            int threadCount,
            int successCount,
            int fullRejectedCount,
            int optimisticConflictCount,
            int retryExhaustedCount,
            int otherFailureCount,
            double durationMs,
            double throughputRps,
            double p50Ms,
            double p95Ms,
            double p99Ms,
            double successP95Ms,
            int finalAppliedCount,
            int actualApplicationCount,
            int countMismatch
    ) {
    }

    @FunctionalInterface
    private interface DoubleValue {
        double get(SimulationResult result);
    }

    private static final class SeatAllocationExecutor {

        private final JdbcTemplate jdbcTemplate;
        private final TransactionTemplate transactionTemplate;

        private SeatAllocationExecutor(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
            this.jdbcTemplate = jdbcTemplate;
            this.transactionTemplate = new TransactionTemplate(transactionManager);
            this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            this.transactionTemplate.setTimeout(20);
        }

        private void recreateTables() {
            dropTables();
            jdbcTemplate.execute("""
                    create table concurrency_strategy_courses (
                        id bigint not null auto_increment,
                        limit_count int not null,
                        applied_count int not null,
                        version bigint not null,
                        primary key (id)
                    ) engine=InnoDB
                    """);
            jdbcTemplate.execute("""
                    create table concurrency_strategy_applications (
                        id bigint not null auto_increment,
                        student_id varchar(80) not null,
                        course_id bigint not null,
                        created_at datetime(6) not null,
                        primary key (id),
                        unique key uk_strategy_student_course (student_id, course_id),
                        constraint fk_strategy_application_course
                            foreign key (course_id) references concurrency_strategy_courses (id)
                            on delete cascade
                    ) engine=InnoDB
                    """);
        }

        private void dropTables() {
            jdbcTemplate.execute("drop table if exists concurrency_strategy_applications");
            jdbcTemplate.execute("drop table if exists concurrency_strategy_courses");
        }

        private long createCourse(int capacity) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "insert into concurrency_strategy_courses "
                                + "(limit_count, applied_count, version) values (?, 0, 0)",
                        Statement.RETURN_GENERATED_KEYS
                );
                statement.setInt(1, capacity);
                return statement;
            }, keyHolder);
            return Objects.requireNonNull(keyHolder.getKey()).longValue();
        }

        private void deleteCourse(long courseId) {
            jdbcTemplate.update("delete from concurrency_strategy_courses where id = ?", courseId);
        }

        private AllocationResult allocate(
                Strategy strategy,
                String studentId,
                long courseId,
                int maxOptimisticRetries
        ) {
            return switch (strategy) {
                case PESSIMISTIC_LOCK -> new AllocationResult(
                        executePessimistic(studentId, courseId), 0
                );
                case OPTIMISTIC_LOCK -> executeOptimistic(studentId, courseId, maxOptimisticRetries);
                case CONDITIONAL_UPDATE -> new AllocationResult(
                        executeConditionalUpdate(studentId, courseId), 0
                );
            };
        }

        private AllocationStatus executePessimistic(String studentId, long courseId) {
            return transactionTemplate.execute(status -> {
                SeatSnapshot seat = readSeat(courseId, true);
                if (seat.appliedCount() >= seat.limitCount()) {
                    return AllocationStatus.FULL;
                }
                jdbcTemplate.update(
                        "update concurrency_strategy_courses set applied_count = applied_count + 1 where id = ?",
                        courseId
                );
                insertApplication(studentId, courseId);
                return AllocationStatus.SUCCESS;
            });
        }

        private AllocationResult executeOptimistic(
                String studentId,
                long courseId,
                int maxOptimisticRetries
        ) {
            int conflicts = 0;
            for (int attempt = 0; attempt <= maxOptimisticRetries; attempt++) {
                AllocationStatus status = transactionTemplate.execute(transaction -> {
                    SeatSnapshot seat = readSeat(courseId, false);
                    if (seat.appliedCount() >= seat.limitCount()) {
                        return AllocationStatus.FULL;
                    }
                    int updated = jdbcTemplate.update("""
                                    update concurrency_strategy_courses
                                    set applied_count = applied_count + 1, version = version + 1
                                    where id = ? and version = ? and applied_count < limit_count
                                    """,
                            courseId, seat.version());
                    if (updated == 0) {
                        return AllocationStatus.CONFLICT;
                    }
                    insertApplication(studentId, courseId);
                    return AllocationStatus.SUCCESS;
                });

                if (status != AllocationStatus.CONFLICT) {
                    return new AllocationResult(status, conflicts);
                }
                conflicts++;
            }
            return new AllocationResult(AllocationStatus.RETRY_EXHAUSTED, conflicts);
        }

        private AllocationStatus executeConditionalUpdate(String studentId, long courseId) {
            return transactionTemplate.execute(status -> {
                int updated = jdbcTemplate.update("""
                                update concurrency_strategy_courses
                                set applied_count = applied_count + 1
                                where id = ? and applied_count < limit_count
                                """,
                        courseId);
                if (updated == 0) {
                    return AllocationStatus.FULL;
                }
                insertApplication(studentId, courseId);
                return AllocationStatus.SUCCESS;
            });
        }

        private SeatSnapshot readSeat(long courseId, boolean forUpdate) {
            String suffix = forUpdate ? " for update" : "";
            return jdbcTemplate.queryForObject(
                    "select applied_count, limit_count, version "
                            + "from concurrency_strategy_courses where id = ?" + suffix,
                    (resultSet, rowNumber) -> new SeatSnapshot(
                            resultSet.getInt("applied_count"),
                            resultSet.getInt("limit_count"),
                            resultSet.getLong("version")
                    ),
                    courseId
            );
        }

        private void insertApplication(String studentId, long courseId) {
            jdbcTemplate.update("""
                            insert into concurrency_strategy_applications
                            (student_id, course_id, created_at) values (?, ?, now(6))
                            """,
                    studentId, courseId);
        }

        private SeatState readState(long courseId) {
            int appliedCount = Objects.requireNonNull(jdbcTemplate.queryForObject(
                    "select applied_count from concurrency_strategy_courses where id = ?",
                    Integer.class,
                    courseId
            ));
            int applicationCount = Objects.requireNonNull(jdbcTemplate.queryForObject(
                    "select count(*) from concurrency_strategy_applications where course_id = ?",
                    Integer.class,
                    courseId
            ));
            return new SeatState(appliedCount, applicationCount);
        }

        private DatabaseInfo databaseInfo() {
            String version = jdbcTemplate.queryForObject("select version()", String.class);
            String isolation = jdbcTemplate.queryForObject("select @@transaction_isolation", String.class);
            return new DatabaseInfo(version, isolation);
        }
    }
}
