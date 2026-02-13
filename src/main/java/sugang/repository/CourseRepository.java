package sugang.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sugang.entity.Course;

public interface CourseRepository extends JpaRepository<Course, Long> {
}
