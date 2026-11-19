package com.campus.activity.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "CAMPUS_RUN_MYSQL_IT", matches = "true")
class CheckInIntegrationTest {

    private static final String AUTH = "/api/admin/auth";
    private static final String ACTIVITIES = "/api/admin/activities/";
    private static final String PUBLIC = "/api/public/check-in/";
    private static final String PHONE = "19900000001";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private Clock clock;
    @Value("${campus.admin.username}") private String username;
    @Value("${campus.admin.password}") private String password;

    @Test
    void adminCanOpenAndCloseAndRosterShowsOnlyOneCheckIn() throws Exception {
        String registrationCode = createActivity(now().plusMinutes(10), now().plusHours(1));
        long id = jdbc.queryForObject("SELECT id FROM activity WHERE public_code = ?", Long.class, registrationCode);
        String adminPath = ACTIVITIES + id + "/check-in";
        jdbc.update("INSERT INTO registration (activity_id, participant_name, phone, college) VALUES (?, ?, ?, ?)",
                id, "Attendee", PHONE, "Test college");
        jdbc.update("INSERT INTO registration (activity_id, participant_name, phone, college, status) "
                + "VALUES (?, ?, ?, ?, 'CANCELLED')", id, "Cancelled attendee", "19900000002", "Test college");

        mvc.perform(get(adminPath)).andExpect(status().isUnauthorized());
        MockHttpSession admin = login();
        mvc.perform(post(adminPath + "/open").session(admin)).andExpect(status().isForbidden());
        String token = adminToken(admin);
        mvc.perform(get(adminPath).session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("READY"));
        String code = body(mvc.perform(post(adminPath + "/open").session(admin).header("X-CSRF-TOKEN", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("OPEN")).andReturn())
                .get("code").asText();
        assertThat(code).matches("[0-9a-f]{32}");
        mvc.perform(post(adminPath + "/open").session(admin).header("X-CSRF-TOKEN", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(code));
        mvc.perform(get(PUBLIC + code)).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Check-in test"))
                .andExpect(jsonPath("$.phone").doesNotExist()).andExpect(jsonPath("$.checkedInCount").doesNotExist());
        mvc.perform(get("/api/public/activities/" + registrationCode)).andExpect(status().isOk())
                .andExpect(jsonPath("$.checkInCode").doesNotExist());
        mvc.perform(post(PUBLIC + code).contentType(MediaType.APPLICATION_JSON).content(phone(PHONE)))
                .andExpect(status().isForbidden());
        submit(code, "bad").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CHECK_IN_PHONE"));
        submit(code, "19900000002").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REGISTRATION_NOT_FOUND"));
        submit(code, PHONE).andExpect(status().isCreated());
        submit(code, PHONE).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_CHECKED_IN"));
        mvc.perform(get(adminPath).session(admin)).andExpect(jsonPath("$.checkedInCount").value(1));
        mvc.perform(get(ACTIVITIES + id + "/registrations").session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].checkedInAt").isNotEmpty());

        mvc.perform(post(adminPath + "/close").session(admin).header("X-CSRF-TOKEN", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("READY"))
                .andExpect(jsonPath("$.checkedInCount").value(1));
        mvc.perform(get(PUBLIC + code)).andExpect(status().isNotFound());
        submit(code, PHONE).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CHECK_IN_UNAVAILABLE"));
        String reopened = body(mvc.perform(post(adminPath + "/open").session(admin).header("X-CSRF-TOKEN", token))
                .andExpect(status().isOk()).andReturn()).get("code").asText();
        assertThat(reopened).isNotEqualTo(code);
        submit(reopened, PHONE).andExpect(status().isConflict());
    }

    @Test
    void checkInCannotStartTooEarlyAndStopsAtActivityEnd() throws Exception {
        MockHttpSession admin = login();
        String token = adminToken(admin);
        String registrationCode = createActivity(now().plusHours(1), now().plusHours(2));
        long id = jdbc.queryForObject("SELECT id FROM activity WHERE public_code = ?", Long.class, registrationCode);
        String adminPath = ACTIVITIES + id + "/check-in";
        mvc.perform(get(adminPath).session(admin)).andExpect(jsonPath("$.state").value("TOO_EARLY"));
        mvc.perform(post(adminPath + "/open").session(admin).header("X-CSRF-TOKEN", token))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CHECK_IN_OUTSIDE_WINDOW"));

        String expired = UUID.randomUUID().toString().replace("-", "");
        jdbc.update("UPDATE activity SET check_in_code = ?, starts_at = ?, ends_at = ? WHERE id = ?",
                expired, now().minusHours(2), now().minusSeconds(1), id);
        mvc.perform(get(adminPath).session(admin)).andExpect(jsonPath("$.state").value("ENDED"))
                .andExpect(jsonPath("$.code").doesNotExist());
        mvc.perform(get(PUBLIC + expired)).andExpect(status().isConflict());
        submit(expired, PHONE).andExpect(status().isConflict());
    }

    private String createActivity(LocalDateTime start, LocalDateTime end) {
        String code = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        jdbc.update("INSERT INTO activity (public_code, title, activity_type, description, location, starts_at, "
                        + "ends_at, registration_deadline, capacity, status, published_at, created_by) "
                        + "SELECT ?, 'Check-in test', 'LECTURE', 'Description', 'Hall A', ?, ?, ?, 5, 'PUBLISHED', ?, id "
                        + "FROM admin_user WHERE username = ?", code, start, end, start.minusMinutes(5), now(), username);
        return code;
    }

    private MockHttpSession login() throws Exception {
        MvcResult csrf = mvc.perform(get(AUTH + "/csrf")).andExpect(status().isOk()).andReturn();
        MvcResult login = mvc.perform(post(AUTH + "/login").session((MockHttpSession) csrf.getRequest().getSession(false))
                .header("X-CSRF-TOKEN", body(csrf).get("token").asText()).param("username", username).param("password", password))
                .andExpect(status().isNoContent()).andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private String adminToken(MockHttpSession admin) throws Exception {
        return body(mvc.perform(get(AUTH + "/csrf").session(admin)).andReturn()).get("token").asText();
    }

    private ResultActions submit(String code, String phone) throws Exception {
        MvcResult csrf = mvc.perform(get("/api/public/csrf")).andExpect(status().isOk()).andReturn();
        return mvc.perform(post(PUBLIC + code).session((MockHttpSession) csrf.getRequest().getSession(false))
                .header("X-CSRF-TOKEN", body(csrf).get("token").asText()).contentType(MediaType.APPLICATION_JSON).content(phone(phone)));
    }

    private String phone(String phone) {
        return json.createObjectNode().put("phone", phone).toString();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
