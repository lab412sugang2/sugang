package sugang.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sugang.entity.Course;

import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {

    Optional<Course> findByCodeAndDivisionNumber(String code, Integer divisionNumber);

    List<Course> findByCodeStartingWithOrderByCodeAsc(String codePrefix);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Course c set c.appliedCount = c.appliedCount + 1 " +
            "where c.id = :courseId and c.appliedCount < c.limitCount and c.canceled = false")
    int increaseAppliedCountIfNotFull(@Param("courseId") Long courseId);
}
