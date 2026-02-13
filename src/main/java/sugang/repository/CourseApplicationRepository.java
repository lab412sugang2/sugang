package sugang.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sugang.entity.CourseApplication;

import java.util.List;
import java.util.Optional;

public interface CourseApplicationRepository extends JpaRepository<CourseApplication, Long> {

    List<CourseApplication> findByStudentIdOrderByCreatedAtAsc(String studentId);

    boolean existsByStudentIdAndCourseId(String studentId, Long courseId);

    Optional<CourseApplication> findByStudentIdAndCourseId(String studentId, Long courseId);
}
