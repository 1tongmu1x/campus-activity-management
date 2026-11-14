package com.campus.activity.activity;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RegistrationService {

    private final ActivityRepository activities;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    RegistrationService(ActivityRepository activities, JdbcTemplate jdbc, Clock clock) {
        this.activities = activities;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    PublicActivityView detail(String publicCode) {
        return new PublicActivityView(requirePublishedActivity(publicCode, now()));
    }

    @Transactional(readOnly = true)
    public List<RegistrationListItem> listForAdmin(long activityId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination");
        }
        if (!activities.find(activityId, now(), false).isPresent()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Activity not found");
        }
        return jdbc.query("SELECT participant_name, phone, college, registered_at FROM registration "
                        + "WHERE activity_id = ? AND status = 'REGISTERED' "
                        + "ORDER BY registered_at DESC, id DESC LIMIT ? OFFSET ?",
                (rs, row) -> new RegistrationListItem(rs.getString("participant_name"), rs.getString("phone"),
                        rs.getString("college"), rs.getObject("registered_at", LocalDateTime.class)),
                activityId, size, (long) page * size);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void register(String publicCode, RegistrationInput participant) {
        // The administrator and all registrations for this activity lock the same activity row.
        if (!activities.lockPublishedByCode(publicCode)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ACTIVITY_NOT_FOUND");
        }
        LocalDateTime now = now();
        ActivityView activity = requirePublishedActivity(publicCode, now);
        if (activity.status != ActivityStatus.REGISTRATION_OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "REGISTRATION_CLOSED");
        }
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM registration WHERE activity_id = ? AND phone = ?)",
                Boolean.class, activity.id, participant.getPhone()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "DUPLICATE_REGISTRATION");
        }
        if (activity.registeredCount >= activity.capacity) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ACTIVITY_FULL");
        }
        try {
            jdbc.update("INSERT INTO registration (activity_id, participant_name, phone, college, registered_at) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    activity.id, participant.getName(), participant.getPhone(), participant.getCollege(), now);
        } catch (DuplicateKeyException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "DUPLICATE_REGISTRATION", error);
        }
    }

    private ActivityView requirePublishedActivity(String publicCode, LocalDateTime now) {
        return activities.findPublishedByCode(publicCode, now)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ACTIVITY_NOT_FOUND"));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
