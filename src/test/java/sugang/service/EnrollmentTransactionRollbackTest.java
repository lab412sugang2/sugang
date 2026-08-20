package sugang.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import sugang.entity.Course;
import sugang.entity.CourseApplication;
import sugang.repository.CourseApplicationRepository;
import sugang.repository.CourseRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = "jdbc:mysql:.*")
class EnrollmentTransactionRollbackTest {

    private static final String STUDENT_ID = "rollback-user";

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseApplicationRepository courseApplicationRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long courseId;

    @AfterEach
    void cleanUp() {
        if (courseId == null) {
            return;
        }
        courseApplicationRepository.findByStudentIdAndCourseId(STUDENT_ID, courseId)
                .ifPresent(courseApplicationRepository::delete);
        courseRepository.deleteById(courseId);
    }

    @Test
    void countAndApplicationAreRolledBackTogetherAfterLaterFailure() {
        Course course = courseRepository.save(new Course(
                "ROLLBACK-FIXED",
                1,
                "Rollback 테스트",
                3,
                "테스트",
                "토3,4(테스트102)",
                10,
                0,
                false
        ));
        courseId = course.getId();
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        assertThrows(ForcedFailureException.class, () ->
                transactionTemplate.executeWithoutResult(status -> {
                    int updatedRows = courseRepository.increaseAppliedCountIfNotFull(courseId);
                    assertEquals(1, updatedRows);
                    courseApplicationRepository.saveAndFlush(new CourseApplication(STUDENT_ID, course));
                    throw new ForcedFailureException();
                })
        );

        Course reloaded = courseRepository.findById(courseId).orElseThrow();
        int actualApplications = courseApplicationRepository.countByCourseId(courseId);

        System.out.println("=== Enrollment Rollback Result ===");
        System.out.println("applied_count=" + reloaded.getAppliedCount()
                + ", actual_applications=" + actualApplications);

        assertEquals(0, reloaded.getAppliedCount());
        assertEquals(0, actualApplications);
    }

    private static class ForcedFailureException extends RuntimeException {
    }
}
