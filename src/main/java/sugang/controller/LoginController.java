package sugang.controller;

import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sugang.service.SessionStudentService;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Controller
public class LoginController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    private static final String DEMO_PASSWORD = "1234";
    private static final int WINDOW_SECONDS = 15;
    private static final int ALLOW_SECONDS = 5;
    private static final int ALLOW_GRACE_SECONDS = 3;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final SessionStudentService sessionStudentService;

    public LoginController(SessionStudentService sessionStudentService) {
        this.sessionStudentService = sessionStudentService;
    }

    @GetMapping("/login")
    public String loginPage(HttpSession session, Model model) {
        model.addAttribute("serverEpochMillis", System.currentTimeMillis());
        model.addAttribute("windowSeconds", WINDOW_SECONDS);
        model.addAttribute("allowSeconds", ALLOW_SECONDS);
        model.addAttribute("allowGraceSeconds", ALLOW_GRACE_SECONDS);
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

        int second = LocalDateTime.now(KST).getSecond();
        int mod = second % WINDOW_SECONDS;
        boolean open = mod < (ALLOW_SECONDS + ALLOW_GRACE_SECONDS);

        if (!open) {
            log.info("LOGIN_WINDOW_CHECK studentId={} sec={} mod={} open={} result=denied",
                    normalizedStudentId, second, mod, open);
            redirectAttributes.addFlashAttribute("loginError", "수강신청 기간이 아닙니다. 수강신청 기간을 확인하십시요.");
            return "redirect:/login";
        }

        if (!DEMO_PASSWORD.equals(normalizedPassword)) {
            log.info("LOGIN_WINDOW_CHECK studentId={} sec={} mod={} open={} result=denied_password",
                    normalizedStudentId, second, mod, open);
            redirectAttributes.addFlashAttribute("loginError", "비밀번호가 올바르지 않습니다.");
            return "redirect:/login";
        }

        log.info("LOGIN_WINDOW_CHECK studentId={} sec={} mod={} open={} result=allowed",
                normalizedStudentId, second, mod, open);
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
