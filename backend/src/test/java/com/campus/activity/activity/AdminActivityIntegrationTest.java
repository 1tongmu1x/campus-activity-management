package com.campus.activity.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "CAMPUS_RUN_MYSQL_IT", matches = "true")
class AdminActivityIntegrationTest {

    private static final String AUTH = "/api/admin/auth";
    private static final String ACTIVITIES = "/api/admin/activities";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Clock clock;

    @Value("${campus.admin.username}") private String username;
    @Value("${campus.admin.password}") private String password;

    @Test
    void adminCanManageActivitiesAndPreserveRegistrations() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
        mvc.perform(get(ACTIVITIES)).andExpect(status().isUnauthorized());

        MvcResult csrf = mvc.perform(get(AUTH + "/csrf")).andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) csrf.getRequest().getSession(false);
        String token = body(csrf).get("token").asText();

        mvc.perform(post(AUTH + "/login").session(session).param("username", username)
                .param("password", password)).andExpect(status().isForbidden());
        mvc.perform(post(AUTH + "/login").session(session).header("X-CSRF-TOKEN", token)
                .param("username", username).param("password", "incorrect-password"))
                .andExpect(status().isUnauthorized());
        MvcResult login = mvc.perform(post(AUTH + "/login").session(session).header("X-CSRF-TOKEN", token)
                .param("username", username).param("password", password))
                .andExpect(status().isNoContent()).andReturn();
        session = (MockHttpSession) login.getRequest().getSession(false);
        mvc.perform(get(AUTH + "/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));
        token = body(mvc.perform(get(AUTH + "/csrf").session(session)).andReturn()).get("token").asText();
        assertThat(jdbc.queryForObject("SELECT password_hash FROM admin_user WHERE username = ?",
                String.class, username)).startsWith("$2").isNotEqualTo(password);

        LocalDateTime start = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS).plusDays(2);
        ObjectNode input = json.createObjectNode().put("title", "Campus lecture")
                .put("activityType", "LECTURE").put("description", "Example activity")
                .put("location", "Hall A").put("startsAt", start.toString())
                .put("endsAt", start.plusHours(1).toString())
                .put("registrationDeadline", start.plusMinutes(1).toString()).put("capacity", 2);

        mvc.perform(post(ACTIVITIES).session(session).header("X-CSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON).content(input.toString()))
                .andExpect(status().isBadRequest());
        input.put("registrationDeadline", start.minusDays(1).toString());
        JsonNode created = body(mvc.perform(post(ACTIVITIES).session(session).header("X-CSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON).content(input.toString()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT")).andReturn());

        long id = created.get("id").asLong();
        String url = ACTIVITIES + "/" + id;
        JsonNode published = body(mvc.perform(post(url + "/publish").session(session).header("X-CSRF-TOKEN", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REGISTRATION_OPEN"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty()).andReturn());
        long version = published.get("version").asLong();
        mvc.perform(post(url + "/publish").session(session).header("X-CSRF-TOKEN", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.publishedAt").value(published.get("publishedAt").asText()))
                .andExpect(jsonPath("$.version").value((int) version));

        mvc.perform(get(ACTIVITIES).session(session).param("type", "LECTURE").param("status", "REGISTRATION_OPEN")
                .param("from", start.minusHours(1).toString()).param("to", start.plusHours(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].publicCode", hasItem(created.get("publicCode").asText())));

        jdbc.update("INSERT INTO registration (activity_id, participant_name, phone, college) VALUES (?, ?, ?, ?)",
                id, "Test A", "10000000000", "Demo");
        jdbc.update("INSERT INTO registration (activity_id, participant_name, phone, college) VALUES (?, ?, ?, ?)",
                id, "Test B", "10000000001", "Demo");
        mvc.perform(get(url + "/registrations")).andExpect(status().isUnauthorized());
        mvc.perform(get(url + "/registrations").session(session).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Test B"))
                .andExpect(jsonPath("$[0].phone").value("10000000001"));
        mvc.perform(get(url + "/registrations").session(session).param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Test A"));
        input.put("capacity", 1);
        mvc.perform(put(url).session(session).header("X-CSRF-TOKEN", token).param("version", String.valueOf(version))
                .contentType(MediaType.APPLICATION_JSON).content(input.toString())).andExpect(status().isConflict());

        input.put("capacity", 3).put("title", "Updated lecture")
                .put("registrationDeadline", start.minusDays(3).toString());
        JsonNode updated = body(mvc.perform(put(url).session(session).header("X-CSRF-TOKEN", token)
                .param("version", String.valueOf(version)).contentType(MediaType.APPLICATION_JSON).content(input.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.registeredCount").value(2))
                .andExpect(jsonPath("$.status").value("REGISTRATION_CLOSED"))
                .andExpect(jsonPath("$.publicCode").value(created.get("publicCode").asText())).andReturn());

        mvc.perform(delete(url).session(session).header("X-CSRF-TOKEN", token).param("version", String.valueOf(version)))
                .andExpect(status().isConflict());
        input.put("startsAt", start.minusDays(3).plusHours(1).toString())
                .put("endsAt", start.minusDays(3).plusHours(2).toString());
        JsonNode finished = body(mvc.perform(put(url).session(session).header("X-CSRF-TOKEN", token)
                .param("version", updated.get("version").asText())
                .contentType(MediaType.APPLICATION_JSON).content(input.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FINISHED")).andReturn());
        mvc.perform(delete(url).session(session).header("X-CSRF-TOKEN", token)
                .param("version", finished.get("version").asText()))
                .andExpect(status().isNoContent());
        mvc.perform(get(url).session(session)).andExpect(status().isNotFound());
        mvc.perform(get(url + "/registrations").session(session)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM registration WHERE activity_id = ?", Integer.class, id))
                .isEqualTo(2);

        mvc.perform(post(AUTH + "/logout").session(session).header("X-CSRF-TOKEN", token))
                .andExpect(status().isNoContent());
        mvc.perform(get(ACTIVITIES)).andExpect(status().isUnauthorized());
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }
}
