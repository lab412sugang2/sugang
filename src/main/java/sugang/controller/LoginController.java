package sugang.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sugang.service.SessionStudentService;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Controller
public class LoginController {

    private static final String DEMO_PASSWORD = "1234";
    private static final int WINDOW_SECONDS = 15;
    private static final int ALLOW_SECONDS = 5;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final SessionStudentService sessionStudentService;

    public LoginController(SessionStudentService sessionStudentService) {
        this.sessionStudentService = sessionStudentService;
    }

    @GetMapping("/login")
    public String loginPage(HttpSession session) {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam(name = "studentId", required = false) String studentId,
                        @RequestParam(name = "password", required = false) String password,
                        HttpSession session,
                        RedirectAttributes redirectAttributes) {
        String normalizedStudentId = studentId == null ? "" : studentId.trim();
        String normalizedPassword = password == null ? "" : password.trim();

        if (normalizedStudentId.isEmpty()) {
            redirectAttributes.addFlashAttribute("loginError", "학번을 입력해 주세요.");
            return "redirect:/login";
        }

        if (!isLoginWindowOpen()) {
            redirectAttributes.addFlashAttribute("loginError", "수강신청 기간이 아닙니다. 수강신청 기간을 확인하십시요.");
            return "redirect:/login";
        }

        if (!DEMO_PASSWORD.equals(normalizedPassword)) {
            redirectAttributes.addFlashAttribute("loginError", "비밀번호가 올바르지 않습니다.");
            return "redirect:/login";
        }

        sessionStudentService.login(session, normalizedStudentId);
        return "redirect:/";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        sessionStudentService.logout(session);
        return "redirect:/login";
    }

    private boolean isLoginWindowOpen() {
        int second = LocalDateTime.now(KST).getSecond();
        return (second % WINDOW_SECONDS) < ALLOW_SECONDS;
    }
}
