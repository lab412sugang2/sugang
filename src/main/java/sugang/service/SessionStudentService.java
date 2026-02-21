package sugang.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SessionStudentService {

    private static final String SESSION_STUDENT_ID_KEY = "practiceStudentId";

    public String getOrCreateStudentId(HttpSession session) {
        Object existing = session.getAttribute(SESSION_STUDENT_ID_KEY);
        if (existing instanceof String value && !value.isBlank()) {
            return value;
        }

        String generated = "P" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        session.setAttribute(SESSION_STUDENT_ID_KEY, generated);
        return generated;
    }
}
