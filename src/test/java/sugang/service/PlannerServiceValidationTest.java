package sugang.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import sugang.entity.Course;
import sugang.exception.CreditLimitExceededException;
import sugang.exception.TimeConflictException;
import sugang.repository.CourseApplicationRepository;
import sugang.repository.CourseRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class PlannerServiceValidationTest {

    @Autowired
    private PlannerService plannerService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseApplicationRepository courseApplicationRepository;

    @Test
    void creditLimitExceededShouldFailAndNotPersist() {
        String studentId = "credit-limit-student";

        Course c1 = createCourse("CRED101", 1, "월1,2(101)");
        Course c2 = createCourse("CRED102", 1, "화1,2(102)");
        Course c3 = createCourse("CRED103", 1, "수1,2(103)");
        Course c4 = createCourse("CRED104", 1, "목1,2(104)");
        Course c5 = createCourse("CRED105", 1, "금1,2(105)");
        Course c6 = createCourse("CRED106", 1, "월3,4(106)");
        Course c7 = createCourse("CRED107", 1, "화3,4(107)");

        plannerService.applyCourse(studentId, c1.getId());
        plannerService.applyCourse(studentId, c2.getId());
        plannerService.applyCourse(studentId, c3.getId());
        plannerService.applyCourse(studentId, c4.getId());
        plannerService.applyCourse(studentId, c5.getId());
        plannerService.applyCourse(studentId, c6.getId());

        assertThrows(CreditLimitExceededException.class,
                () -> plannerService.applyCourse(studentId, c7.getId()));

        assertEquals(6, courseApplicationRepository.findByStudentIdOrderByCreatedAtAsc(studentId).size());
        assertFalseApplyExists(studentId, c7.getId());
    }

    @Test
    void timeConflictShouldFailAndNotPersist() {
        String studentId = "time-conflict-student";

        Course first = createCourse("TIME101", 1, "월2,3/수2,3(201)");
        Course conflict = createCourse("TIME102", 1, "월3,4/목3,4(202)");

        plannerService.applyCourse(studentId, first.getId());

        assertThrows(TimeConflictException.class,
                () -> plannerService.applyCourse(studentId, conflict.getId()));

        assertEquals(1, courseApplicationRepository.findByStudentIdOrderByCreatedAtAsc(studentId).size());
        assertFalseApplyExists(studentId, conflict.getId());
    }

    @Test
    void normalApplyShouldSucceed() {
        String studentId = "normal-apply-student";

        Course target = createCourse("NORM101", 1, "목7,8(301)");

        plannerService.applyCourse(studentId, target.getId());

        assertTrue(courseApplicationRepository.existsByStudentIdAndCourseId(studentId, target.getId()));
        Optional<Course> reloaded = courseRepository.findById(target.getId());
        assertTrue(reloaded.isPresent());
        assertEquals(1, reloaded.get().getAppliedCount());
    }

    private Course createCourse(String code, int division, String schedule) {
        Course course = new Course(code, division, code + "-NAME", 3, "교수", schedule, 30, 0, false);
        return courseRepository.save(course);
    }

    private void assertFalseApplyExists(String studentId, Long courseId) {
        boolean exists = courseApplicationRepository.existsByStudentIdAndCourseId(studentId, courseId);
        assertFalse(exists);
    }
}
