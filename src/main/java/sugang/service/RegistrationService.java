package sugang.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sugang.exception.CreditLimitExceededException;
import sugang.exception.TimeConflictException;

@Service
public class RegistrationService {

    private final PlannerService plannerService;

    public RegistrationService(PlannerService plannerService) {
        this.plannerService = plannerService;
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

    public String applyCourse(String studentId, Long courseId, RedirectAttributes redirectAttributes) {
        String resolvedStudentId = resolveStudentId(studentId);
        try {
            plannerService.applyCourse(resolvedStudentId, courseId);
        } catch (CreditLimitExceededException | TimeConflictException | IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("message", e.getMessage());
        }
        return "redirect:/";
    }

    public String deleteCourse(String studentId, Long courseId, RedirectAttributes redirectAttributes) {
        String resolvedStudentId = resolveStudentId(studentId);
        plannerService.deleteCourse(resolvedStudentId, courseId);
        redirectAttributes.addFlashAttribute("message", "신청 과목이 삭제되었습니다.");
        return "redirect:/";
    }

    private String resolveStudentId(String studentId) {
        if (studentId == null || studentId.isBlank()) {
            return PlannerService.DEFAULT_STUDENT_ID;
        }
        return studentId;
    }
}
