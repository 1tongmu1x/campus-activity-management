package com.campus.activity.activity;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicCsrfController {

    @GetMapping("/api/public/csrf")
    public ResponseEntity<CsrfToken> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(token);
    }
}
