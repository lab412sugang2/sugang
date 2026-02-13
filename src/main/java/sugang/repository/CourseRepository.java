package sugang.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sugang.entity.Course;

import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {

    Optional<Course> findByCodeAndDivisionNumber(String code, Integer divisionNumber);
}
