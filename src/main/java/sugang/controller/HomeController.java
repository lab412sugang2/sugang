package sugang.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import sugang.service.HomePageService;
import sugang.service.PlannerService;
import sugang.service.SessionStudentService;

import jakarta.servlet.http.HttpSession;

@Controller
public class HomeController {

    private final HomePageService homePageService;
    private final PlannerService plannerService;
    private final SessionStudentService sessionStudentService;

    public HomeController(HomePageService homePageService,
                          PlannerService plannerService,
                          SessionStudentService sessionStudentService) {
        this.homePageService = homePageService;
        this.plannerService = plannerService;
        this.sessionStudentService = sessionStudentService;
    }

    @GetMapping("/")
    public String home(Model model, HttpSession session) {
        String studentId = sessionStudentService.getOrCreateStudentId(session);
        model.addAttribute("studentId", studentId);
        model.addAttribute("courses", plannerService.getCourses());
        model.addAttribute("applications", plannerService.getApplications(studentId));
        model.addAttribute("totalCredit", plannerService.getTotalCredit(studentId));
        return homePageService.getPlannerViewName();
    }
}
