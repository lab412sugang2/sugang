package sugang.service;

import jakarta.annotation.PostConstruct;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sugang.entity.Course;
import sugang.entity.CourseApplication;
import sugang.exception.CreditLimitExceededException;
import sugang.exception.TimeConflictException;
import sugang.repository.CourseApplicationRepository;
import sugang.repository.CourseRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class PlannerService {

    public static final String DEFAULT_STUDENT_ID = "202600000";
    public static final int MAX_APPLICABLE_CREDIT = 19;

    private final CourseRepository courseRepository;
    private final CourseApplicationRepository courseApplicationRepository;

    public PlannerService(CourseRepository courseRepository, CourseApplicationRepository courseApplicationRepository) {
        this.courseRepository = courseRepository;
        this.courseApplicationRepository = courseApplicationRepository;
    }

    @PostConstruct
    @Transactional
    public void initSampleCourses() {
        boolean seeded = false;
        if (courseRepository.count() == 0) {
            List<Course> samples = new ArrayList<>();
            samples.add(new Course("329810", 1, "데이터베이스", 3, "강지훈", "금1,2,3(2공105)", 40, 12, false));
            samples.add(new Course("340112", 1, "운영체제", 3, "김태현", "월4,5/수4,5(1공201)", 45, 28, false));
            samples.add(new Course("340201", 2, "알고리즘", 3, "이선영", "화2,3/목2,3(2공310)", 35, 17, false));
            samples.add(new Course("340305", 1, "컴퓨터네트워크", 3, "박지훈", "월1,2/수1,2(1공104)", 50, 31, false));
            samples.add(new Course("340410", 1, "인공지능", 3, "최민수", "화6,7/목6,7(소프트401)", 40, 40, false));
            samples.add(new Course("341120", 3, "소프트웨어공학", 3, "한지원", "월10,11/수10,11(공학507)", 45, 22, false));
            samples.add(new Course("341230", 2, "웹프로그래밍", 3, "윤수빈", "화10,11/목10,11(소프트310)", 40, 19, false));
            samples.add(new Course("341340", 1, "정보보호", 3, "정우성", "수6,7/금6,7(2공405)", 38, 21, false));
            samples.add(new Course("341450", 1, "머신러닝", 3, "김하늘", "월13,14/수13,14(소프트502)", 42, 26, false));
            samples.add(new Course("341560", 1, "클라우드컴퓨팅", 3, "신동혁", "화16,17/목16,17(공학303)", 36, 14, false));
            samples.add(new Course("349901", 1, "캡스톤디자인실습A", 3, "김연우", "월15,16(공학201)", 1, 0, false));
            samples.add(new Course("349902", 1, "캡스톤디자인실습B", 3, "박민지", "수15,16(공학202)", 1, 0, false));

            courseRepository.saveAll(samples);
            seeded = true;
        }

        if (seeded) {
            courseRepository.findByCodeAndDivisionNumber("349901", 1).ifPresent(course ->
                    courseApplicationRepository.save(new CourseApplication("seed-closed-1", course)));
            courseRepository.findByCodeAndDivisionNumber("349902", 1).ifPresent(course ->
                    courseApplicationRepository.save(new CourseApplication("seed-closed-2", course)));
        }

        syncAllAppliedCounts();
    }

    @Transactional(readOnly = true)
    public List<Course> getCourses() {
        return courseRepository.findAll(Sort.by(Sort.Order.asc("code"), Sort.Order.asc("divisionNumber")));
    }

    @Transactional(readOnly = true)
    public List<CourseApplication> getApplications(String studentId) {
        return courseApplicationRepository.findByStudentIdOrderByCreatedAtAsc(studentId);
    }

    @Transactional(readOnly = true)
    public int getTotalCredit(String studentId) {
        return getApplications(studentId).stream().mapToInt(a -> a.getCourse().getCredit()).sum();
    }

    @Transactional
    public void applyCourse(String studentId, Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 과목입니다."));

        if (course.isCanceled()) {
            throw new IllegalStateException("폐강된 과목은 신청할 수 없습니다.");
        }
        if (course.isFull()) {
            throw new IllegalStateException("마감된 강좌입니다.");
        }
        if (courseApplicationRepository.existsByStudentIdAndCourseId(studentId, courseId)) {
            throw new IllegalStateException("이미 신청된 과목입니다.");
        }

        List<CourseApplication> apps = courseApplicationRepository.findByStudentIdOrderByCreatedAtAsc(studentId);
        validateCreditLimit(apps, course);
        validateTimeConflict(apps, course);

        courseApplicationRepository.save(new CourseApplication(studentId, course));
        course.syncAppliedCount(courseApplicationRepository.countByCourseId(courseId));
        courseRepository.save(course);
    }

    @Transactional
    public void deleteCourse(String studentId, Long courseId) {
        courseApplicationRepository.findByStudentIdAndCourseId(studentId, courseId).ifPresent(app -> {
            courseApplicationRepository.delete(app);
            Course course = app.getCourse();
            course.syncAppliedCount(courseApplicationRepository.countByCourseId(courseId));
            courseRepository.save(course);
        });
    }

    private Set<String> parseSlots(String schedule) {
        Set<String> slots = new HashSet<>();
        if (schedule == null || schedule.isBlank()) {
            return slots;
        }

        String timePart = schedule.split("\\(")[0];
        String[] blocks = timePart.split("/");
        for (String rawBlock : blocks) {
            String block = rawBlock.trim();
            if (block.isEmpty()) {
                continue;
            }

            String dayChar = block.substring(0, 1);
            int dayIndex = dayToIndex(dayChar);
            if (dayIndex < 0) {
                continue;
            }

            String periodsText = block.substring(1);
            String[] periods = periodsText.split(",");
            for (String p : periods) {
                String period = p.trim();
                if (!period.isEmpty()) {
                    slots.add(dayIndex + "-" + period);
                }
            }
        }

        return slots;
    }

    private void validateCreditLimit(List<CourseApplication> apps, Course targetCourse) {
        int currentCredit = apps.stream().mapToInt(a -> a.getCourse().getCredit()).sum();
        int nextCredit = currentCredit + targetCourse.getCredit();
        if (nextCredit > MAX_APPLICABLE_CREDIT) {
            throw new CreditLimitExceededException("신청가능학점(19학점)을 초과할 수 없습니다.");
        }
    }

    private void validateTimeConflict(List<CourseApplication> apps, Course targetCourse) {
        if (hasTimeConflict(apps, targetCourse)) {
            throw new TimeConflictException("시간표가 중복되는 과목은 신청할 수 없습니다.");
        }
    }

    private boolean hasTimeConflict(List<CourseApplication> apps, Course targetCourse) {
        Set<String> targetSlots = parseSlots(targetCourse.getSchedule());
        for (CourseApplication app : apps) {
            Set<String> existingSlots = parseSlots(app.getCourse().getSchedule());
            for (String slot : existingSlots) {
                if (targetSlots.contains(slot)) {
                    return true;
                }
            }
        }
        return false;
    }

    private int dayToIndex(String day) {
        return switch (day) {
            case "월" -> 1;
            case "화" -> 2;
            case "수" -> 3;
            case "목" -> 4;
            case "금" -> 5;
            case "토" -> 6;
            default -> -1;
        };
    }

    private void syncAllAppliedCounts() {
        List<Course> courses = courseRepository.findAll();
        for (Course course : courses) {
            int actualCount = courseApplicationRepository.countByCourseId(course.getId());
            if (!course.getAppliedCount().equals(actualCount)) {
                course.syncAppliedCount(actualCount);
                courseRepository.save(course);
            }
        }
    }
}
