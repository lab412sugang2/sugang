package sugang.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import sugang.entity.Course;
import sugang.repository.CourseApplicationRepository;
import sugang.repository.CourseRepository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class EnrollmentRaceConditionReproductionTest {

    private static final int REQUEST_COUNT = 100;
    private static final int THREAD_COUNT = 32;
    private static final int CAPACITY = 10;

    @Autowired
    private PlannerService plannerService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseApplicationRepository courseApplicationRepository;

    private Long courseId;

    @AfterEach
    void cleanUp() {
        if (courseId == null) {
            return;
        }
        for (int i = 0; i < REQUEST_COUNT; i++) {
            courseApplicationRepository.findByStudentIdAndCourseId("race-baseline-" + i, courseId)
                    .ifPresent(courseApplicationRepository::delete);
        }
        courseRepository.deleteById(courseId);
    }

    @Test
    void reproduceCurrentReadThenWriteRaceCondition() throws Exception {
        Course course = courseRepository.save(new Course(
                "RACE-BASELINE",
                1,
                "Race Condition 재현",
                3,
                "테스트",
                "토1,2(테스트101)",
                CAPACITY,
                0,
                false
        ));
        courseId = course.getId();

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch ready = new CountDownLatch(REQUEST_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(REQUEST_COUNT);
        AtomicInteger success = new AtomicInteger();
        Map<String, AtomicInteger> failures = new ConcurrentHashMap<>();

        for (int i = 0; i < REQUEST_COUNT; i++) {
            String studentId = "race-baseline-" + i;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    plannerService.applyCourse(studentId, courseId);
                    success.incrementAndGet();
                } catch (Exception e) {
                    failures.computeIfAbsent(rootCauseName(e), ignored -> new AtomicInteger()).incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        long startedAt = System.nanoTime();
        start.countDown();
        boolean completed = done.await(30, TimeUnit.SECONDS);
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        executor.shutdownNow();

        Course reloaded = courseRepository.findById(courseId).orElseThrow();
        int actualApplications = courseApplicationRepository.countByCourseId(courseId);

        System.out.println("=== Race Condition Baseline ===");
        System.out.println("requests=" + REQUEST_COUNT
                + ", capacity=" + CAPACITY
                + ", success=" + success.get()
                + ", actual_applications=" + actualApplications
                + ", applied_count=" + reloaded.getAppliedCount()
                + ", duration_ms=" + durationMs);
        System.out.println("failures=" + failures);

        assertTrue(completed, "동시 요청이 제한 시간 안에 종료되어야 합니다.");
    }

    private String rootCauseName(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor.getClass().getSimpleName();
    }
}
