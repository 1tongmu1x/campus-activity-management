package com.campus.activity.activity;

import java.time.LocalDateTime;

public class ActivityView {
    public final long id;
    public final String publicCode;
    public final String title;
    public final String activityType;
    public final String description;
    public final LocalDateTime startsAt;
    public final LocalDateTime endsAt;
    public final String location;
    public final LocalDateTime registrationDeadline;
    public final int capacity;
    public final ActivityStatus status;
    public final LocalDateTime publishedAt;
    public final long registeredCount;
    public final long version;

    ActivityView(long id, String publicCode, String title, String activityType, String description,
                 LocalDateTime startsAt, LocalDateTime endsAt, String location,
                 LocalDateTime registrationDeadline, int capacity, ActivityStatus status,
                 LocalDateTime publishedAt, long registeredCount, long version) {
        this.id = id;
        this.publicCode = publicCode;
        this.title = title;
        this.activityType = activityType;
        this.description = description;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.location = location;
        this.registrationDeadline = registrationDeadline;
        this.capacity = capacity;
        this.status = status;
        this.publishedAt = publishedAt;
        this.registeredCount = registeredCount;
        this.version = version;
    }
}
