package com.campus.activity.activity;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CheckInService {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    CheckInService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    AdminView status(long activityId) {
        Target activity = find("id = ?", activityId, false);
        return adminView(activity, now());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminView open(long activityId) {
        Target activity = find("id = ?", activityId, true);
        LocalDateTime now = now();
        requireWindow(activity, now);
        if (activity.code == null) {
            activity.code = UUID.randomUUID().toString().replace("-", "");
            jdbc.update("UPDATE activity SET check_in_code = ? WHERE id = ?", activity.code, activity.id);
        }
        return adminView(activity, now);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminView close(long activityId) {
        Target activity = find("id = ?", activityId, true);
        activity.code = null;
        jdbc.update("UPDATE activity SET check_in_code = NULL WHERE id = ?", activity.id);
        return adminView(activity, now());
    }

    PublicView detail(String code) {
        Target activity = find("check_in_code = ?", code, false);
        requireWindow(activity, now());
        return new PublicView(activity);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void checkIn(String code, String phone) {
        // Opening, closing and changing an activity all lock the same row as a check-in.
        long activityId = find("check_in_code = ?", code, false).id;
        Target activity = find("id = ?", activityId, true);
        if (!code.equals(activity.code)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "CHECK_IN_UNAVAILABLE");
        }
        LocalDateTime now = now();
        requireWindow(activity, now);
        try {
            int inserted = jdbc.update("INSERT INTO check_in (registration_id, activity_id, checked_in_at) "
                            + "SELECT id, activity_id, ? FROM registration "
                            + "WHERE activity_id = ? AND phone = ? AND status = 'REGISTERED'",
                    now, activity.id, phone);
            if (inserted == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "REGISTRATION_NOT_FOUND");
            }
        } catch (DuplicateKeyException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ALREADY_CHECKED_IN", error);
        }
    }

    private Target find(String condition, Object parameter, boolean forUpdate) {
        return jdbc.query("SELECT id, title, location, starts_at, ends_at, check_in_code FROM activity "
                        + "WHERE deleted_at IS NULL AND status = 'PUBLISHED' AND " + condition
                        + (forUpdate ? " FOR UPDATE" : ""),
                (rs, row) -> new Target(rs.getLong("id"), rs.getString("title"), rs.getString("location"),
                        rs.getObject("starts_at", LocalDateTime.class), rs.getObject("ends_at", LocalDateTime.class),
                        rs.getString("check_in_code")), parameter)
                .stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CHECK_IN_UNAVAILABLE"));
    }

    private AdminView adminView(Target activity, LocalDateTime now) {
        State state = state(activity, now);
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM check_in WHERE activity_id = ?", Long.class, activity.id);
        return new AdminView(state, state == State.OPEN ? activity.code : null, activity.opensAt(), activity.endsAt, count);
    }

    private State state(Target activity, LocalDateTime now) {
        if (now.isBefore(activity.opensAt())) return State.TOO_EARLY;
        if (!now.isBefore(activity.endsAt)) return State.ENDED;
        return activity.code == null ? State.READY : State.OPEN;
    }

    private void requireWindow(Target activity, LocalDateTime now) {
        State state = state(activity, now);
        if (state == State.TOO_EARLY || state == State.ENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CHECK_IN_OUTSIDE_WINDOW");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    enum State { TOO_EARLY, READY, OPEN, ENDED }

    static final class AdminView {
        public final State state;
        public final String code;
        public final LocalDateTime opensAt;
        public final LocalDateTime endsAt;
        public final long checkedInCount;

        AdminView(State state, String code, LocalDateTime opensAt, LocalDateTime endsAt, long checkedInCount) {
            this.state = state;
            this.code = code;
            this.opensAt = opensAt;
            this.endsAt = endsAt;
            this.checkedInCount = checkedInCount;
        }
    }

    static final class PublicView {
        public final String title;
        public final String location;
        public final LocalDateTime endsAt;

        PublicView(Target activity) {
            this.title = activity.title;
            this.location = activity.location;
            this.endsAt = activity.endsAt;
        }
    }

    private static final class Target {
        final long id;
        final String title;
        final String location;
        final LocalDateTime startsAt;
        final LocalDateTime endsAt;
        String code;

        Target(long id, String title, String location, LocalDateTime startsAt, LocalDateTime endsAt, String code) {
            this.id = id;
            this.title = title;
            this.location = location;
            this.startsAt = startsAt;
            this.endsAt = endsAt;
            this.code = code;
        }

        LocalDateTime opensAt() {
            return startsAt.minusMinutes(30);
        }
    }
}
