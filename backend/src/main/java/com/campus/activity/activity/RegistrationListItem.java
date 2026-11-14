package com.campus.activity.activity;

import java.time.LocalDateTime;

public class RegistrationListItem {
    public final String name;
    public final String phone;
    public final String college;
    public final LocalDateTime registeredAt;

    RegistrationListItem(String name, String phone, String college, LocalDateTime registeredAt) {
        this.name = name;
        this.phone = phone;
        this.college = college;
        this.registeredAt = registeredAt;
    }
}
