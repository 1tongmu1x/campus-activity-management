package com.campus.activity.activity;

import java.util.Collections;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class CheckInController {

    private static final String ADMIN = "/api/admin/activities/{id}/check-in";
    private static final String PUBLIC = "/api/public/check-in/{code:[0-9a-f]{32}}";
    private final CheckInService checkIns;

    CheckInController(CheckInService checkIns) {
        this.checkIns = checkIns;
    }

    @GetMapping(ADMIN)
    public ResponseEntity<CheckInService.AdminView> status(@PathVariable long id) {
        return noStore(checkIns.status(id));
    }

    @PostMapping(ADMIN + "/open")
    public ResponseEntity<CheckInService.AdminView> open(@PathVariable long id) {
        return noStore(checkIns.open(id));
    }

    @PostMapping(ADMIN + "/close")
    public ResponseEntity<CheckInService.AdminView> close(@PathVariable long id) {
        return noStore(checkIns.close(id));
    }

    @GetMapping(PUBLIC)
    public ResponseEntity<CheckInService.PublicView> detail(@PathVariable String code) {
        return noStore(checkIns.detail(code));
    }

    @PostMapping(PUBLIC)
    @ResponseStatus(HttpStatus.CREATED)
    public void submit(@PathVariable String code, @Valid @RequestBody Input input) {
        checkIns.checkIn(code, input.phone);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> checkInError(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatus()).cacheControl(CacheControl.noStore())
                .body(Collections.singletonMap("code", error.getReason()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalidPhone() {
        return ResponseEntity.badRequest().body(Collections.singletonMap("code", "INVALID_CHECK_IN_PHONE"));
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    public static final class Input {
        @NotBlank
        @Pattern(regexp = "1[3-9][0-9]{9}")
        private String phone;

        public void setPhone(String phone) {
            this.phone = phone == null ? null : phone.trim();
        }
    }
}
