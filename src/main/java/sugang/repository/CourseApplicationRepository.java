package sugang.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sugang.entity.CourseApplication;

import java.util.List;
import java.util.Optional;

public interface CourseApplicationRepository extends JpaRepository<CourseApplication, Long> {

    List<CourseApplication> findByStudentIdOrderByCreatedAtAsc(String studentId);

    boolean existsByStudentIdAndCourseId(String studentId, Long courseId);

    Optional<CourseApplication> findByStudentIdAndCourseId(String studentId, Long courseId);

    int countByCourseId(Long courseId);

    @Modifying(flushAutomatically = true)
    @Query("delete from CourseApplication ca where ca.course.id = :courseId")
    int deleteByCourseId(@Param("courseId") Long courseId);
}
