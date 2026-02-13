package sugang;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

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
    void homeShouldRenderPlanner() throws Exception {
        mockMvc.perform(get("/"))
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
        mockMvc.perform(get("/findGsLctTmtbl.do"))
                .andExpect(status().isOk())
                .andExpect(view().name("timetable-popup"));
    }

    @Test
    void formPostsShouldRedirectToHome() throws Exception {
        mockMvc.perform(post("/saveTkcrsApl.do").param("courseId", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        mockMvc.perform(post("/deleteTkcrsApl.do").param("courseId", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        mockMvc.perform(post("/findSubjInfo.do"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }
}
