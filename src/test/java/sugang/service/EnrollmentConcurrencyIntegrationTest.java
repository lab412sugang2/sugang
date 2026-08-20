package sugang.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import sugang.entity.Course;
import sugang.repository.CourseApplicationRepository;
import sugang.repository.CourseRepository;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = "jdbc:mysql:.*")
class EnrollmentConcurrencyIntegrationTest {

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
            deleteApplication("conditional-user-" + i);
        }
        deleteApplication("duplicate-user");
        courseRepository.deleteById(courseId);
    }

    @Test
    void conditionalUpdateKeepsEnrollmentWithinCapacity() throws Exception {
        courseId = createCourse("RACE-FIXED", CAPACITY).getId();

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(REQUEST_COUNT);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger fullRejected = new AtomicInteger();
        AtomicInteger unexpectedFailure = new AtomicInteger();

        for (int i = 0; i < REQUEST_COUNT; i++) {
            String studentId = "conditional-user-" + i;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    plannerService.applyCourse(studentId, courseId);
                    success.incrementAndGet();
                } catch (IllegalStateException e) {
                    if (e.getMessage() != null && e.getMessage().contains("마감된 강좌")) {
                        fullRejected.incrementAndGet();
                    } else {
                        unexpectedFailure.incrementAndGet();
                    }
                } catch (Exception e) {
                    unexpectedFailure.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS), "스레드 풀이 시작 준비를 마쳐야 합니다.");
        long startedAt = System.nanoTime();
        start.countDown();
        boolean completed = done.await(30, TimeUnit.SECONDS);
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        executor.shutdownNow();

        Course reloaded = courseRepository.findById(courseId).orElseThrow();
        int actualApplications = courseApplicationRepository.countByCourseId(courseId);

        System.out.println("=== Conditional Update Concurrency Result ===");
        System.out.println("requests=" + REQUEST_COUNT
                + ", capacity=" + CAPACITY
                + ", success=" + success.get()
                + ", full_rejected=" + fullRejected.get()
                + ", unexpected_failure=" + unexpectedFailure.get()
                + ", actual_applications=" + actualApplications
                + ", applied_count=" + reloaded.getAppliedCount()
                + ", duration_ms=" + durationMs);

        assertTrue(completed, "동시 요청이 제한 시간 안에 종료되어야 합니다.");
        assertEquals(CAPACITY, success.get());
        assertEquals(REQUEST_COUNT - CAPACITY, fullRejected.get());
        assertEquals(0, unexpectedFailure.get());
        assertEquals(CAPACITY, actualApplications);
        assertEquals(CAPACITY, reloaded.getAppliedCount());
    }

    @Test
    void concurrentDuplicateRequestCreatesOneApplication() throws Exception {
        courseId = createCourse("DUPLICATE-FIXED", CAPACITY).getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger duplicateRejected = new AtomicInteger();
        AtomicInteger unexpectedFailure = new AtomicInteger();

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    plannerService.applyCourse("duplicate-user", courseId);
                    success.incrementAndGet();
                } catch (IllegalStateException e) {
                    if (e.getMessage() != null && e.getMessage().contains("이미 신청된 과목")) {
                        duplicateRejected.incrementAndGet();
                    } else {
                        unexpectedFailure.incrementAndGet();
                    }
                } catch (Exception e) {
                    unexpectedFailure.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        executor.shutdownNow();

        Course reloaded = courseRepository.findById(courseId).orElseThrow();
        int actualApplications = courseApplicationRepository.countByCourseId(courseId);

        System.out.println("=== Concurrent Duplicate Result ===");
        System.out.println("success=" + success.get()
                + ", duplicate_rejected=" + duplicateRejected.get()
                + ", unexpected_failure=" + unexpectedFailure.get()
                + ", actual_applications=" + actualApplications
                + ", applied_count=" + reloaded.getAppliedCount());

        assertEquals(1, success.get());
        assertEquals(1, duplicateRejected.get());
        assertEquals(0, unexpectedFailure.get());
        assertEquals(1, actualApplications);
        assertEquals(1, reloaded.getAppliedCount());
    }

    private Course createCourse(String code, int capacity) {
        return courseRepository.save(new Course(
                code,
                1,
                code + " 테스트",
                3,
                "테스트",
                "토1,2(테스트101)",
                capacity,
                0,
                false
        ));
    }

    private void deleteApplication(String studentId) {
        courseApplicationRepository.findByStudentIdAndCourseId(studentId, courseId)
                .ifPresent(courseApplicationRepository::delete);
    }
}
