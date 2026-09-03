package sugang.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import sugang.entity.Course;
import sugang.entity.CourseApplication;
import sugang.repository.CourseApplicationRepository;
import sugang.repository.CourseRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class PlannerServiceQueryOptimizationTest {

    private static final String STUDENT_ID = "duplicate-query-test-student";

    @Autowired
    private PlannerService plannerService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseApplicationRepository courseApplicationRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void reusesApplicationsLookupToRejectDuplicateWithTwoSelects() {
        Course course = courseRepository.saveAndFlush(new Course(
                "QUERY101",
                1,
                "중복 쿼리 검증",
                3,
                "테스트",
                "토1,2(테스트201)",
                30,
                1,
                false
        ));
        courseApplicationRepository.saveAndFlush(new CourseApplication(STUDENT_ID, course));
        entityManager.clear();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> plannerService.applyCourse(STUDENT_ID, course.getId())
        );
        long preparedStatements = statistics.getPrepareStatementCount();

        assertEquals("이미 신청된 과목입니다.", exception.getMessage());
        assertEquals(2, preparedStatements, "강의 조회와 신청 목록 조회만 실행해야 합니다.");
    }
}
