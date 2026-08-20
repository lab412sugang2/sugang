package sugang;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import sugang.service.PerformanceTestService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.performance-test.enabled=true",
        "app.performance-test.token=test-performance-token"
})
@AutoConfigureMockMvc
class PerformanceTestApiIntegrationTest {

    private static final String TOKEN = "test-performance-token";
    private static final String TOKEN_HEADER = "X-Performance-Test-Token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PerformanceTestService performanceTestService;

    @BeforeEach
    @AfterEach
    void cleanup() {
        performanceTestService.cleanupFixtures();
    }

    @Test
    void pingIsAvailableWhenPerformanceApiIsEnabled() throws Exception {
        mockMvc.perform(get("/performance/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void mutationEndpointRejectsMissingToken() throws Exception {
        mockMvc.perform(post("/performance/fixtures/same-course")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capacity\":10}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void fixtureApplyDuplicateStatusAndCleanupWorkTogether() throws Exception {
        String fixtureJson = mockMvc.perform(post("/performance/fixtures/same-course")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capacity\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses[0].appliedCount").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode fixture = objectMapper.readTree(fixtureJson);
        long courseId = fixture.path("courses").get(0).path("id").asLong();
        String request = "{\"studentId\":\"performance-test-user\",\"courseId\":" + courseId + "}";

        mockMvc.perform(post("/performance/apply")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("success"));

        mockMvc.perform(post("/performance/apply")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("rejected"));

        mockMvc.perform(get("/performance/fixtures/status")
                        .header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses[0].appliedCount").value(1))
                .andExpect(jsonPath("$.courses[0].actualApplications").value(1))
                .andExpect(jsonPath("$.courses[0].countMatches").value(true));

        mockMvc.perform(post("/performance/fixtures/cleanup")
                        .header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coursesDeleted").value(1))
                .andExpect(jsonPath("$.applicationsDeleted").value(1));

        mockMvc.perform(get("/performance/fixtures/status")
                        .header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses.length()").value(0));
    }

    @Test
    void applyRejectsStudentIdLongerThanDatabaseLimit() throws Exception {
        String request = "{\"studentId\":\"1234567890123456789012345678901\",\"courseId\":1}";

        mockMvc.perform(post("/performance/apply")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());
    }
}
