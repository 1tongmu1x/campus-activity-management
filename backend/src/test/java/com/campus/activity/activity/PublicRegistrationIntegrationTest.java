package com.campus.activity.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
class PublicRegistrationIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Clock clock;
    @Value("${campus.admin.username}") private String username;

    @Test
    void onlyPublishedActivitiesArePublicAndResponsesExcludeInternalData() throws Exception {
        String draft = createActivity("DRAFT", 4, now().plusHours(1));
        String deleted = createActivity("PUBLISHED", 4, now().plusHours(1));
        jdbc.update("UPDATE activity SET deleted_at = ? WHERE public_code = ?", now(), deleted);
        mvc.perform(get(path(draft))).andExpect(status().isNotFound());
        mvc.perform(get(path(deleted))).andExpect(status().isNotFound());
        assertThat(register(draft, "19900000001").getResponse().getStatus()).isEqualTo(404);
        assertThat(register(deleted, "19900000001").getResponse().getStatus()).isEqualTo(404);

        String published = createActivity("PUBLISHED", 4, now().plusHours(1));
        mvc.perform(get(path(published))).andExpect(status().isOk())
                .andExpect(jsonPath("$.publicCode").value(published))
                .andExpect(jsonPath("$.status").value("REGISTRATION_OPEN"))
                .andExpect(jsonPath("$.registrationFull").value(false))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.registrations").doesNotExist());
    }

    @Test
    void rejectsInvalidDuplicateFullAndLateSubmissions() throws Exception {
        String code = createActivity("PUBLISHED", 1, now().plusHours(1));
        mvc.perform(post(path(code) + "/registrations").contentType(MediaType.APPLICATION_JSON)
                .content(participant("19900000001"))).andExpect(status().isForbidden());
        MvcResult invalid = register(code, "123");
        assertThat(invalid.getResponse().getStatus()).isEqualTo(400);
        assertThat(invalid.getResponse().getContentAsString()).isEqualTo("{\"code\":\"INVALID_REGISTRATION\"}");

        assertThat(register(code, "19900000001").getResponse().getStatus()).isEqualTo(201);
        assertConflict(register(code, "19900000001"), "DUPLICATE_REGISTRATION");
        assertConflict(register(code, "19900000002"), "ACTIVITY_FULL");
        mvc.perform(get(path(code))).andExpect(jsonPath("$.registrationFull").value(true));

        String closed = createActivity("PUBLISHED", 4, now().minusSeconds(1));
        assertConflict(register(closed, "19900000001"), "REGISTRATION_CLOSED");
        assertThat(countRegistrations(closed)).isZero();
    }

    @Test
    void onlyOneConcurrentRequestGetsTheLastSeat() throws Exception {
        String code = createActivity("PUBLISHED", 2, now().plusHours(1));
        assertThat(register(code, "19900000001").getResponse().getStatus()).isEqualTo(201);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier start = new CyclicBarrier(2);
        try {
            Future<MvcResult> first = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return register(code, "19900000002");
            });
            Future<MvcResult> second = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return register(code, "19900000003");
            });
            MvcResult a = first.get(10, TimeUnit.SECONDS);
            MvcResult b = second.get(10, TimeUnit.SECONDS);
            assertThat(new int[]{a.getResponse().getStatus(), b.getResponse().getStatus()})
                    .containsExactlyInAnyOrder(201, 409);
            assertConflict(a.getResponse().getStatus() == 409 ? a : b, "ACTIVITY_FULL");
            assertThat(countRegistrations(code)).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    private MvcResult register(String code, String phone) throws Exception {
        MvcResult csrf = mvc.perform(get("/api/public/csrf")).andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) csrf.getRequest().getSession(false);
        String token = json.readTree(csrf.getResponse().getContentAsString()).get("token").asText();
        return mvc.perform(post(path(code) + "/registrations").session(session)
                .header("X-CSRF-TOKEN", token).contentType(MediaType.APPLICATION_JSON)
                .content(participant(phone))).andReturn();
    }

    private String participant(String phone) throws Exception {
        return json.createObjectNode().put("name", "Test participant").put("phone", phone)
                .put("college", "Test college").toString();
    }

    private void assertConflict(MvcResult response, String code) throws Exception {
        assertThat(response.getResponse().getStatus()).isEqualTo(409);
        assertThat(json.readTree(response.getResponse().getContentAsString()).get("code").asText())
                .isEqualTo(code);
    }

    private String createActivity(String status, int capacity, LocalDateTime deadline) {
        String code = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        LocalDateTime start = now().plusDays(1);
        jdbc.update("INSERT INTO activity (public_code, title, activity_type, description, location, "
                        + "starts_at, ends_at, registration_deadline, capacity, status, created_by) "
                        + "SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, id FROM admin_user WHERE username = ?",
                code, "Public registration test", "LECTURE", "Test activity", "Hall A",
                start, start.plusHours(1), deadline, capacity, status, username);
        return code;
    }

    private int countRegistrations(String code) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM registration r JOIN activity a ON a.id = r.activity_id "
                + "WHERE a.public_code = ?", Integer.class, code);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    private String path(String code) {
        return "/api/public/activities/" + code;
    }
}
