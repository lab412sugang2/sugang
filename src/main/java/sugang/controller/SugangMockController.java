package sugang.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sugang.service.RegistrationService;
import jakarta.servlet.http.HttpSession;

@Controller
public class SugangMockController {

    private final RegistrationService registrationService;

    public SugangMockController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @GetMapping("/main.do")
    public String main() {
        return registrationService.mainRedirectView();
    }

    @GetMapping("/findGsLctTmtbl.do")
    public String timetablePopup() {
        return registrationService.timetablePopupView();
    }

    @PostMapping("/saveTkcrsApl.do")
    public String saveCourseApplication(@RequestParam("courseId") Long courseId,
                                        HttpSession session,
                                        RedirectAttributes redirectAttributes) {
        return registrationService.applyCourse(session, courseId, redirectAttributes);
    }

    @PostMapping("/deleteTkcrsApl.do")
    public String deleteCourseApplication(@RequestParam("courseId") Long courseId,
                                          HttpSession session,
                                          RedirectAttributes redirectAttributes) {
        return registrationService.deleteCourse(session, courseId, redirectAttributes);
    }

    @PostMapping("/findSubjInfo.do")
    public String findSubjectInfo() {
        return registrationService.findSubjectRedirectView();
    }
}
