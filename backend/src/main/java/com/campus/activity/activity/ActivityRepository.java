package com.campus.activity.activity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
class ActivityRepository {

    private static final String DISPLAY_STATUS =
            "CASE WHEN a.status <> 'PUBLISHED' THEN a.status "
            + "WHEN a.ends_at <= :now THEN 'FINISHED' "
            + "WHEN a.registration_deadline <= :now OR a.starts_at <= :now THEN 'REGISTRATION_CLOSED' "
            + "ELSE 'REGISTRATION_OPEN' END";

    private static final String SELECT_ACTIVITY =
            "SELECT a.*, " + DISPLAY_STATUS + " AS display_status, "
            + "(SELECT COUNT(*) FROM registration r "
            + "WHERE r.activity_id = a.id AND r.status = 'REGISTERED') AS registered_count "
            + "FROM activity a WHERE a.deleted_at IS NULL";

    private static final RowMapper<ActivityView> VIEW_MAPPER = (rs, row) -> new ActivityView(
            rs.getLong("id"), rs.getString("public_code"), rs.getString("title"),
            rs.getString("activity_type"), rs.getString("description"),
            rs.getObject("starts_at", LocalDateTime.class), rs.getObject("ends_at", LocalDateTime.class),
            rs.getString("location"), rs.getObject("registration_deadline", LocalDateTime.class),
            rs.getInt("capacity"), ActivityStatus.valueOf(rs.getString("display_status")),
            rs.getObject("published_at", LocalDateTime.class), rs.getLong("registered_count"),
            rs.getLong("version"));

    private final NamedParameterJdbcTemplate jdbc;

    ActivityRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<ActivityView> list(LocalDateTime now, String type, ActivityStatus status,
                            LocalDateTime from, LocalDateTime to, long offset, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource("now", now)
                .addValue("offset", offset).addValue("size", size);
        StringBuilder sql = new StringBuilder(SELECT_ACTIVITY);
        if (type != null) {
            sql.append(" AND a.activity_type = :type");
            params.addValue("type", type);
        }
        if (status != null) {
            sql.append(" AND (").append(DISPLAY_STATUS).append(") = :status");
            params.addValue("status", status.name());
        }
        if (from != null) {
            sql.append(" AND a.starts_at >= :from");
            params.addValue("from", from);
        }
        if (to != null) {
            sql.append(" AND a.starts_at < :to");
            params.addValue("to", to);
        }
        sql.append(" ORDER BY a.starts_at DESC, a.id DESC LIMIT :size OFFSET :offset");
        return jdbc.query(sql.toString(), params, VIEW_MAPPER);
    }

    Optional<ActivityView> find(long id, LocalDateTime now, boolean forUpdate) {
        String sql = SELECT_ACTIVITY + " AND a.id = :id" + (forUpdate ? " FOR UPDATE" : "");
        return jdbc.query(sql, new MapSqlParameterSource("id", id).addValue("now", now), VIEW_MAPPER)
                .stream().findFirst();
    }

    Optional<ActivityView> findPublishedByCode(String code, LocalDateTime now) {
        return jdbc.query(SELECT_ACTIVITY + " AND a.public_code = :code AND a.status = 'PUBLISHED'",
                new MapSqlParameterSource("code", code).addValue("now", now), VIEW_MAPPER)
                .stream().findFirst();
    }

    boolean lockPublishedByCode(String code) {
        return !jdbc.queryForList("SELECT id FROM activity WHERE public_code = :code "
                        + "AND deleted_at IS NULL AND status = 'PUBLISHED' FOR UPDATE",
                new MapSqlParameterSource("code", code), Long.class).isEmpty();
    }

    long insert(ActivityInput input, String publicCode, LocalDateTime now, long adminId) {
        MapSqlParameterSource params = inputParameters(input)
                .addValue("publicCode", publicCode).addValue("now", now).addValue("adminId", adminId);
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update("INSERT INTO activity (public_code, title, activity_type, description, starts_at, ends_at, "
                + "location, registration_deadline, capacity, created_by, created_at, updated_at) "
                + "VALUES (:publicCode, :title, :type, :description, :startsAt, :endsAt, :location, "
                + ":deadline, :capacity, :adminId, :now, :now)", params, key);
        return key.getKey().longValue();
    }

    void update(long id, ActivityInput input, LocalDateTime now) {
        jdbc.update("UPDATE activity SET title = :title, activity_type = :type, description = :description, "
                + "check_in_code = CASE WHEN starts_at <> :startsAt OR ends_at <> :endsAt "
                + "THEN NULL ELSE check_in_code END, "
                + "starts_at = :startsAt, ends_at = :endsAt, location = :location, "
                + "registration_deadline = :deadline, capacity = :capacity, version = version + 1, updated_at = :now "
                + "WHERE id = :id", inputParameters(input).addValue("now", now).addValue("id", id));
    }

    void publish(long id, LocalDateTime now) {
        jdbc.update("UPDATE activity SET status = 'PUBLISHED', published_at = :now, "
                + "version = version + 1, updated_at = :now WHERE id = :id",
                new MapSqlParameterSource("id", id).addValue("now", now));
    }

    void delete(long id, LocalDateTime now) {
        jdbc.update("UPDATE activity SET deleted_at = :now, version = version + 1, updated_at = :now WHERE id = :id",
                new MapSqlParameterSource("id", id).addValue("now", now));
    }

    private MapSqlParameterSource inputParameters(ActivityInput input) {
        return new MapSqlParameterSource("title", input.title.trim())
                .addValue("type", input.activityType.trim()).addValue("description", input.description.trim())
                .addValue("startsAt", input.startsAt).addValue("endsAt", input.endsAt)
                .addValue("location", input.location.trim()).addValue("deadline", input.registrationDeadline)
                .addValue("capacity", input.capacity);
    }
}
