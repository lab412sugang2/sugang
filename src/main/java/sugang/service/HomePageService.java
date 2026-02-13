package sugang.service;

import org.springframework.stereotype.Service;

@Service
public class HomePageService {

    public String getPlannerViewName() {
        return "planner";
    }
}
