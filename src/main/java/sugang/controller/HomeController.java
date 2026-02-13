package sugang.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import sugang.service.HomePageService;
import sugang.service.PlannerService;

@Controller
public class HomeController {

    private final HomePageService homePageService;
    private final PlannerService plannerService;

    public HomeController(HomePageService homePageService, PlannerService plannerService) {
        this.homePageService = homePageService;
        this.plannerService = plannerService;
    }

    @GetMapping("/")
    public String home(Model model) {
        String studentId = PlannerService.DEFAULT_STUDENT_ID;
        model.addAttribute("studentId", studentId);
        model.addAttribute("courses", plannerService.getCourses());
        model.addAttribute("applications", plannerService.getApplications(studentId));
        model.addAttribute("totalCredit", plannerService.getTotalCredit(studentId));
        return homePageService.getPlannerViewName();
    }
}
