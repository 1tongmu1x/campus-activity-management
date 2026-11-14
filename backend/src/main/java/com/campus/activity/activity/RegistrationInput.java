package com.campus.activity.activity;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class RegistrationInput {

    @NotBlank
    @Size(max = 64)
    private String name;

    @NotBlank
    @Pattern(regexp = "1[3-9][0-9]{9}")
    private String phone;

    @NotBlank
    @Size(max = 100)
    private String college;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? null : name.trim();
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone == null ? null : phone.trim();
    }

    public String getCollege() {
        return college;
    }

    public void setCollege(String college) {
        this.college = college == null ? null : college.trim();
    }
}
