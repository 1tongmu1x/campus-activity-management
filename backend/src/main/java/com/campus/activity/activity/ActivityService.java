package com.campus.activity.activity;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ActivityService {

    private final ActivityRepository activities;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    ActivityService(ActivityRepository activities, JdbcTemplate jdbc, Clock clock) {
        this.activities = activities;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    List<ActivityView> list(String type, ActivityStatus status, LocalDateTime from, LocalDateTime to,
                            int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (from != null && to != null && !from.isBefore(to))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date range or pagination");
        }
        return activities.list(now(), type, status, from, to, (long) page * size, size);
    }

    ActivityView get(long id) {
        return requireActivity(id, now(), false);
    }

    @Transactional
    public ActivityView create(ActivityInput input, String username) {
        validateTimes(input);
        long adminId = jdbc.queryForObject("SELECT id FROM admin_user WHERE username = ?", Long.class, username);
        String publicCode = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        LocalDateTime now = now();
        long id = activities.insert(input, publicCode, now, adminId);
        return requireActivity(id, now, false);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ActivityView update(long id, long version, ActivityInput input) {
        validateTimes(input);
        LocalDateTime now = now();
        ActivityView current = requireActivity(id, now, true);
        checkVersion(current, version);
        if (input.capacity < current.registeredCount) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Capacity is below the current registration count");
        }
        activities.update(id, input, now);
        return requireActivity(id, now, false);
    }

    @Transactional
    public ActivityView publish(long id) {
        LocalDateTime now = now();
        ActivityView current = requireActivity(id, now, true);
        if (current.publishedAt != null) {
            return current;
        }
        if (current.status != ActivityStatus.DRAFT || !now.isBefore(current.registrationDeadline)
                || !now.isBefore(current.startsAt)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a draft with a future deadline can be published");
        }
        activities.publish(id, now);
        return requireActivity(id, now, false);
    }

    @Transactional
    public void delete(long id, long version) {
        LocalDateTime now = now();
        ActivityView current = requireActivity(id, now, true);
        checkVersion(current, version);
        activities.delete(id, now);
    }

    private ActivityView requireActivity(long id, LocalDateTime now, boolean forUpdate) {
        return activities.find(id, now, forUpdate)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Activity not found"));
    }

    private void checkVersion(ActivityView activity, long version) {
        if (activity.version != version) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Activity changed. Reload it and try again.");
        }
    }

    private void validateTimes(ActivityInput input) {
        if (input.startsAt.getNano() != 0 || input.endsAt.getNano() != 0
                || input.registrationDeadline.getNano() != 0
                || input.startsAt.getYear() < 1000 || input.endsAt.getYear() > 9999
                || input.registrationDeadline.getYear() < 1000
                || !input.startsAt.isBefore(input.endsAt) || input.registrationDeadline.isAfter(input.startsAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Use whole-second times. The end must follow the start and the deadline cannot follow the start.");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
