package sugang.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SessionStudentService {

    public static final String SESSION_STUDENT_ID_KEY = "practiceStudentId";
    public static final String SESSION_LOGIN_STUDENT_ID_KEY = "practiceLoginStudentId";
    public static final String SESSION_USER_NAME_KEY = "practiceUserName";
    public static final String SESSION_AUTHENTICATED_KEY = "practiceAuthenticated";

    public String getStudentId(HttpSession session) {
        Object existing = session.getAttribute(SESSION_STUDENT_ID_KEY);
        if (existing instanceof String value && !value.isBlank()) {
            return value;
        }
        return "";
    }

    public String getDisplayStudentId(HttpSession session) {
        Object existing = session.getAttribute(SESSION_LOGIN_STUDENT_ID_KEY);
        if (existing instanceof String value && !value.isBlank()) {
            return value;
        }
        return getStudentId(session);
    }

    public String getDisplayName(HttpSession session) {
        Object existing = session.getAttribute(SESSION_USER_NAME_KEY);
        if (existing instanceof String value && !value.isBlank()) {
            return value;
        }
        return "샘플사용자";
    }

    public boolean isAuthenticated(HttpSession session) {
        Object existing = session.getAttribute(SESSION_AUTHENTICATED_KEY);
        return existing instanceof Boolean value && value;
    }

    public String createPracticeStudentId() {
        return "P" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    public void login(HttpSession session, String studentId) {
        String internalStudentId = createPracticeStudentId();
        session.setAttribute(SESSION_STUDENT_ID_KEY, internalStudentId);
        session.setAttribute(SESSION_LOGIN_STUDENT_ID_KEY, studentId);
        session.setAttribute(SESSION_USER_NAME_KEY, "샘플사용자");
        session.setAttribute(SESSION_AUTHENTICATED_KEY, true);
    }

    public void logout(HttpSession session) {
        session.invalidate();
    }
}
