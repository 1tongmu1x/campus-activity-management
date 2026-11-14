package com.campus.activity.activity;

import java.util.Collections;
import java.util.Map;
import javax.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/public/activities/{publicCode:[0-9a-f]{12}}")
public class PublicActivityController {

    private final RegistrationService registrations;

    PublicActivityController(RegistrationService registrations) {
        this.registrations = registrations;
    }

    @GetMapping
    public ResponseEntity<PublicActivityView> detail(@PathVariable String publicCode) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(registrations.detail(publicCode));
    }

    @PostMapping("/registrations")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@PathVariable String publicCode, @Valid @RequestBody RegistrationInput participant) {
        registrations.register(publicCode, participant);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> registrationError(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatus())
                .body(Collections.singletonMap("code", error.getReason()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalidRegistration() {
        return ResponseEntity.badRequest().body(Collections.singletonMap("code", "INVALID_REGISTRATION"));
    }
}
