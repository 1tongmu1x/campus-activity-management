package com.campus.activity.admin;

import java.security.Principal;
import java.util.Collections;
import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken token) {
        return token;
    }

    @GetMapping("/me")
    public Map<String, String> me(Principal admin) {
        return Collections.singletonMap("username", admin.getName());
    }
}
