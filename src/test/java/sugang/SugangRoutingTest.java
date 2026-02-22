package sugang;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import sugang.service.SessionStudentService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class SugangRoutingTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void homeShouldRedirectToLoginWhenAnonymous() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void homeShouldRenderPlannerWhenLoggedIn() throws Exception {
        mockMvc.perform(get("/")
                        .sessionAttr(SessionStudentService.SESSION_AUTHENTICATED_KEY, true)
                        .sessionAttr(SessionStudentService.SESSION_STUDENT_ID_KEY, "20260001")
                        .sessionAttr(SessionStudentService.SESSION_USER_NAME_KEY, "샘플사용자"))
                .andExpect(status().isOk())
                .andExpect(view().name("planner"));
    }

    @Test
    void mainDoShouldRedirectToHome() throws Exception {
        mockMvc.perform(get("/main.do"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void timetablePopupShouldRenderTemplate() throws Exception {
        mockMvc.perform(get("/findGsLctTmtbl.do")
                        .sessionAttr(SessionStudentService.SESSION_AUTHENTICATED_KEY, true)
                        .sessionAttr(SessionStudentService.SESSION_STUDENT_ID_KEY, "20260001")
                        .sessionAttr(SessionStudentService.SESSION_USER_NAME_KEY, "샘플사용자"))
                .andExpect(status().isOk())
                .andExpect(view().name("timetable-popup"));
    }

    @Test
    void formPostsShouldRedirectToHome() throws Exception {
        mockMvc.perform(post("/saveTkcrsApl.do").param("courseId", "1")
                        .sessionAttr(SessionStudentService.SESSION_AUTHENTICATED_KEY, true)
                        .sessionAttr(SessionStudentService.SESSION_STUDENT_ID_KEY, "20260001")
                        .sessionAttr(SessionStudentService.SESSION_USER_NAME_KEY, "샘플사용자"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        mockMvc.perform(post("/deleteTkcrsApl.do").param("courseId", "1")
                        .sessionAttr(SessionStudentService.SESSION_AUTHENTICATED_KEY, true)
                        .sessionAttr(SessionStudentService.SESSION_STUDENT_ID_KEY, "20260001")
                        .sessionAttr(SessionStudentService.SESSION_USER_NAME_KEY, "샘플사용자"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        mockMvc.perform(post("/findSubjInfo.do"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void loginPageAndLoginFlowShouldWork() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));

        mockMvc.perform(post("/login")
                        .param("studentId", "20260001")
                        .param("password", "dku1234!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }
}
