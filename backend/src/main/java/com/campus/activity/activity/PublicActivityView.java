package com.campus.activity.activity;

import java.time.LocalDateTime;

public class PublicActivityView {
    public final String publicCode;
    public final String title;
    public final String activityType;
    public final String description;
    public final String location;
    public final LocalDateTime startsAt;
    public final LocalDateTime endsAt;
    public final LocalDateTime registrationDeadline;
    public final ActivityStatus status;
    public final boolean registrationFull;

    PublicActivityView(ActivityView activity) {
        publicCode = activity.publicCode;
        title = activity.title;
        activityType = activity.activityType;
        description = activity.description;
        location = activity.location;
        startsAt = activity.startsAt;
        endsAt = activity.endsAt;
        registrationDeadline = activity.registrationDeadline;
        status = activity.status;
        registrationFull = activity.registeredCount >= activity.capacity;
    }
}
