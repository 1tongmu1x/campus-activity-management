package com.campus.activity.activity;

import java.time.LocalDateTime;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

public class ActivityInput {
    @NotBlank @Size(max = 120)
    public String title;

    @NotBlank @Size(max = 32)
    public String activityType;

    @NotBlank @Size(max = 10000)
    public String description;

    @NotNull
    public LocalDateTime startsAt;

    @NotNull
    public LocalDateTime endsAt;

    @NotBlank @Size(max = 255)
    public String location;

    @NotNull
    public LocalDateTime registrationDeadline;

    @NotNull @Positive
    public Integer capacity;
}
