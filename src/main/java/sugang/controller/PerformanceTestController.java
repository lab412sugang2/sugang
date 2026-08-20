package sugang.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import sugang.service.PerformanceTestService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/performance")
@ConditionalOnProperty(name = "app.performance-test.enabled", havingValue = "true")
public class PerformanceTestController {

    private static final int DEFAULT_CAPACITY = 100_000;
    private static final int DEFAULT_DISTRIBUTED_COURSE_COUNT = 20;

    private final PerformanceTestService performanceTestService;
    private final String mutationToken;

    public PerformanceTestController(PerformanceTestService performanceTestService,
                                     @Value("${app.performance-test.token:}") String mutationToken) {
        this.performanceTestService = performanceTestService;
        this.mutationToken = mutationToken;
    }

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of("status", "ok", "timestamp", Instant.now().toString());
    }

    @GetMapping("/courses/{courseId}")
    public PerformanceTestService.CourseSummary getCourse(@PathVariable Long courseId) {
        try {
            return performanceTestService.getCourse(courseId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    @PostMapping("/fixtures/same-course")
    public PerformanceTestService.FixtureResponse prepareSameCourse(
            @RequestHeader(value = "X-Performance-Test-Token", required = false) String token,
            @RequestBody(required = false) FixtureRequest request
    ) {
        requireMutationToken(token);
        int capacity = request != null && request.capacity() != null ? request.capacity() : DEFAULT_CAPACITY;
        return performanceTestService.prepareSameCourse(capacity);
    }

    @PostMapping("/fixtures/distributed-courses")
    public PerformanceTestService.FixtureResponse prepareDistributedCourses(
            @RequestHeader(value = "X-Performance-Test-Token", required = false) String token,
            @RequestBody(required = false) FixtureRequest request
    ) {
        requireMutationToken(token);
        int courseCount = request != null && request.courseCount() != null
                ? request.courseCount()
                : DEFAULT_DISTRIBUTED_COURSE_COUNT;
        int capacity = request != null && request.capacity() != null ? request.capacity() : DEFAULT_CAPACITY;
        return performanceTestService.prepareDistributedCourses(courseCount, capacity);
    }

    @GetMapping("/fixtures/status")
    public PerformanceTestService.FixtureStatusResponse fixtureStatus(
            @RequestHeader(value = "X-Performance-Test-Token", required = false) String token
    ) {
        requireMutationToken(token);
        return performanceTestService.getFixtureStatus();
    }

    @PostMapping("/fixtures/cleanup")
    public PerformanceTestService.CleanupResponse cleanup(
            @RequestHeader(value = "X-Performance-Test-Token", required = false) String token
    ) {
        requireMutationToken(token);
        return performanceTestService.cleanupFixtures();
    }

    @PostMapping("/apply")
    public PerformanceTestService.ApplyResponse apply(
            @RequestHeader(value = "X-Performance-Test-Token", required = false) String token,
            @RequestBody ApplyRequest request
    ) {
        requireMutationToken(token);
        if (request == null || request.studentId() == null || request.studentId().isBlank()
                || request.courseId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "studentId와 courseId가 필요합니다.");
        }
        if (request.studentId().trim().length() > 30) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "studentId는 30자 이하여야 합니다.");
        }
        return performanceTestService.apply(request.studentId().trim(), request.courseId());
    }

    private void requireMutationToken(String providedToken) {
        if (mutationToken == null || mutationToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "성능 테스트 변경 API를 사용하려면 토큰 설정이 필요합니다.");
        }
        byte[] expected = mutationToken.getBytes(StandardCharsets.UTF_8);
        byte[] provided = providedToken == null
                ? new byte[0]
                : providedToken.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, provided)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "성능 테스트 토큰이 올바르지 않습니다.");
        }
    }

    public record FixtureRequest(Integer courseCount, Integer capacity) {
    }

    public record ApplyRequest(String studentId, Long courseId) {
    }
}
