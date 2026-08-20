package sugang.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sugang.entity.Course;
import sugang.repository.CourseApplicationRepository;
import sugang.repository.CourseRepository;

import java.util.ArrayList;
import java.util.List;

@Service
public class PerformanceTestService {

    private static final String PERFORMANCE_CODE_PREFIX = "PERF-";
    private static final String SAME_COURSE_CODE = PERFORMANCE_CODE_PREFIX + "SAME";
    private static final String DISTRIBUTED_CODE_PREFIX = PERFORMANCE_CODE_PREFIX + "DIST-";
    private static final int MAX_FIXTURE_COURSES = 100;
    private static final int MAX_CAPACITY = 1_000_000;

    private final PlannerService plannerService;
    private final CourseRepository courseRepository;
    private final CourseApplicationRepository courseApplicationRepository;

    public PerformanceTestService(PlannerService plannerService,
                                  CourseRepository courseRepository,
                                  CourseApplicationRepository courseApplicationRepository) {
        this.plannerService = plannerService;
        this.courseRepository = courseRepository;
        this.courseApplicationRepository = courseApplicationRepository;
    }

    // B 시나리오는 강의 한 건 조회 외의 추가 SQL을 실행하지 않는다.
    @Transactional(readOnly = true)
    public CourseSummary getCourse(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 과목입니다."));
        return CourseSummary.from(course);
    }

    @Transactional
    public FixtureResponse prepareSameCourse(int capacity) {
        int normalizedCapacity = normalizeCapacity(capacity);
        Course course = upsertCourse(SAME_COURSE_CODE, 1, "성능테스트-동일강의", normalizedCapacity);
        resetCourse(course, normalizedCapacity);
        return new FixtureResponse(List.of(CourseSummary.from(course)));
    }

    @Transactional
    public FixtureResponse prepareDistributedCourses(int courseCount, int capacity) {
        int normalizedCourseCount = Math.max(1, Math.min(courseCount, MAX_FIXTURE_COURSES));
        int normalizedCapacity = normalizeCapacity(capacity);
        List<CourseSummary> courses = new ArrayList<>();

        for (int i = 1; i <= normalizedCourseCount; i++) {
            String code = DISTRIBUTED_CODE_PREFIX + String.format("%03d", i);
            Course course = upsertCourse(code, 1, "성능테스트-분산강의-" + i, normalizedCapacity);
            resetCourse(course, normalizedCapacity);
            courses.add(CourseSummary.from(course));
        }

        return new FixtureResponse(courses);
    }

    @Transactional(readOnly = true)
    public FixtureStatusResponse getFixtureStatus() {
        List<FixtureCourseStatus> courses = courseRepository
                .findByCodeStartingWithOrderByCodeAsc(PERFORMANCE_CODE_PREFIX)
                .stream()
                .map(course -> FixtureCourseStatus.from(
                        course,
                        courseApplicationRepository.countByCourseId(course.getId())
                ))
                .toList();
        return new FixtureStatusResponse(courses);
    }

    @Transactional
    public CleanupResponse cleanupFixtures() {
        List<Course> courses = courseRepository.findByCodeStartingWithOrderByCodeAsc(PERFORMANCE_CODE_PREFIX);
        int deletedApplications = 0;

        for (Course course : courses) {
            deletedApplications += courseApplicationRepository.deleteByCourseId(course.getId());
            courseRepository.deleteById(course.getId());
        }
        courseRepository.flush();

        return new CleanupResponse(courses.size(), deletedApplications);
    }

    // PlannerService가 트랜잭션 경계를 소유하도록 여기에는 @Transactional을 붙이지 않는다.
    public ApplyResponse apply(String studentId, Long courseId) {
        try {
            plannerService.applyCourse(studentId, courseId);
            return new ApplyResponse("success", "신청 성공", courseId, studentId);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return new ApplyResponse("rejected", e.getMessage(), courseId, studentId);
        }
    }

    private Course upsertCourse(String code, int divisionNumber, String name, int capacity) {
        return courseRepository.findByCodeAndDivisionNumber(code, divisionNumber)
                .map(course -> {
                    course.updateLimitCount(capacity);
                    return courseRepository.save(course);
                })
                .orElseGet(() -> courseRepository.save(new Course(
                        code,
                        divisionNumber,
                        name,
                        3,
                        "성능테스트",
                        "금20,21(성능테스트101)",
                        capacity,
                        0,
                        false
                )));
    }

    private void resetCourse(Course course, int capacity) {
        courseApplicationRepository.deleteByCourseId(course.getId());
        course.updateLimitCount(capacity);
        course.syncAppliedCount(0);
        courseRepository.saveAndFlush(course);
    }

    private int normalizeCapacity(int capacity) {
        return Math.max(1, Math.min(capacity, MAX_CAPACITY));
    }

    public record CourseSummary(
            Long id,
            String code,
            Integer divisionNumber,
            String name,
            Integer limitCount,
            Integer appliedCount,
            boolean canceled
    ) {
        static CourseSummary from(Course course) {
            return new CourseSummary(
                    course.getId(),
                    course.getCode(),
                    course.getDivisionNumber(),
                    course.getName(),
                    course.getLimitCount(),
                    course.getAppliedCount(),
                    course.isCanceled()
            );
        }
    }

    public record FixtureCourseStatus(
            Long id,
            String code,
            Integer limitCount,
            Integer appliedCount,
            int actualApplications,
            boolean countMatches
    ) {
        static FixtureCourseStatus from(Course course, int actualApplications) {
            return new FixtureCourseStatus(
                    course.getId(),
                    course.getCode(),
                    course.getLimitCount(),
                    course.getAppliedCount(),
                    actualApplications,
                    course.getAppliedCount() == actualApplications
            );
        }
    }

    public record FixtureResponse(List<CourseSummary> courses) {
    }

    public record FixtureStatusResponse(List<FixtureCourseStatus> courses) {
    }

    public record CleanupResponse(int coursesDeleted, int applicationsDeleted) {
    }

    public record ApplyResponse(String result, String message, Long courseId, String studentId) {
    }
}
