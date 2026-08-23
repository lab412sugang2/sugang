package sugang.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import sugang.entity.Course;
import sugang.repository.CourseApplicationRepository;
import sugang.repository.CourseRepository;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class RegistrationMetricsIntegrationTest {

    private static final String SUCCESS_STUDENT_ID = "metric-success-user";
    private static final String ROLLBACK_STUDENT_ID = "metric-rollback-user";

    @Autowired
    private PlannerService plannerService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseApplicationRepository courseApplicationRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    private final List<Long> courseIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long courseId : courseIds) {
            courseApplicationRepository.findByStudentIdAndCourseId(SUCCESS_STUDENT_ID, courseId)
                    .ifPresent(courseApplicationRepository::delete);
            courseApplicationRepository.findByStudentIdAndCourseId(ROLLBACK_STUDENT_ID, courseId)
                    .ifPresent(courseApplicationRepository::delete);
            courseRepository.deleteById(courseId);
        }
    }

    @Test
    void recordsCommittedTransactionAndDatabasePhases() {
        Course course = createCourse("METRIC-SUCCESS", 10, 0);
        double committedBefore = transactionTimer("committed").count();
        double updateBefore = phaseTimer(RegistrationMetrics.PHASE_CONDITIONAL_UPDATE).count();
        double flushBefore = phaseTimer(RegistrationMetrics.PHASE_APPLICATION_FLUSH).count();

        plannerService.applyCourse(SUCCESS_STUDENT_ID, course.getId());

        assertEquals(committedBefore + 1, transactionTimer("committed").count());
        assertEquals(updateBefore + 1, phaseTimer(RegistrationMetrics.PHASE_CONDITIONAL_UPDATE).count());
        assertEquals(flushBefore + 1, phaseTimer(RegistrationMetrics.PHASE_APPLICATION_FLUSH).count());
    }

    @Test
    void recordsRolledBackTransactionWhenCourseIsFull() {
        Course course = createCourse("METRIC-ROLLBACK", 1, 1);
        double rolledBackBefore = transactionTimer("rolled_back").count();
        double updateBefore = phaseTimer(RegistrationMetrics.PHASE_CONDITIONAL_UPDATE).count();

        assertThrows(IllegalStateException.class,
                () -> plannerService.applyCourse(ROLLBACK_STUDENT_ID, course.getId()));

        assertEquals(rolledBackBefore + 1, transactionTimer("rolled_back").count());
        assertEquals(updateBefore + 1, phaseTimer(RegistrationMetrics.PHASE_CONDITIONAL_UPDATE).count());
    }

    private Course createCourse(String code, int limitCount, int appliedCount) {
        Course course = courseRepository.save(new Course(
                code,
                1,
                code,
                3,
                "성능측정",
                "토20,21(성능측정101)",
                limitCount,
                appliedCount,
                false
        ));
        courseIds.add(course.getId());
        return course;
    }

    private Timer phaseTimer(String phase) {
        return meterRegistry.get("sugang.registration.phase")
                .tag("phase", phase)
                .timer();
    }

    private Timer transactionTimer(String outcome) {
        return meterRegistry.get("sugang.registration.transaction")
                .tag("outcome", outcome)
                .timer();
    }
}
