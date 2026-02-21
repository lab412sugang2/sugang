package sugang.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpSession;
import sugang.exception.CreditLimitExceededException;
import sugang.exception.TimeConflictException;

@Service
public class RegistrationService {

    private final PlannerService plannerService;
    private final SessionStudentService sessionStudentService;

    public RegistrationService(PlannerService plannerService, SessionStudentService sessionStudentService) {
        this.plannerService = plannerService;
        this.sessionStudentService = sessionStudentService;
    }

    public String mainRedirectView() {
        return "redirect:/";
    }

    public String timetablePopupView() {
        return "timetable-popup";
    }

    public String saveCourseRedirectView() {
        return "redirect:/";
    }

    public String deleteCourseRedirectView() {
        return "redirect:/";
    }

    public String findSubjectRedirectView() {
        return "redirect:/";
    }

    public String applyCourse(HttpSession session, Long courseId, RedirectAttributes redirectAttributes) {
        String resolvedStudentId = sessionStudentService.getOrCreateStudentId(session);
        try {
            plannerService.applyCourse(resolvedStudentId, courseId);
        } catch (CreditLimitExceededException | TimeConflictException | IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("message", e.getMessage());
        }
        return "redirect:/";
    }

    public String deleteCourse(HttpSession session, Long courseId, RedirectAttributes redirectAttributes) {
        String resolvedStudentId = sessionStudentService.getOrCreateStudentId(session);
        plannerService.deleteCourse(resolvedStudentId, courseId);
        redirectAttributes.addFlashAttribute("message", "신청 과목이 삭제되었습니다.");
        return "redirect:/";
    }
}
