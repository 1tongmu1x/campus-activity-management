package com.campus.activity.activity;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import javax.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/activities")
public class AdminActivityController {

    private final ActivityService activities;
    private final RegistrationService registrations;

    AdminActivityController(ActivityService activities, RegistrationService registrations) {
        this.activities = activities;
        this.registrations = registrations;
    }

    @GetMapping
    public List<ActivityView> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) ActivityStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return activities.list(type, status, from, to, page, size);
    }

    @GetMapping("/{id}")
    public ActivityView get(@PathVariable long id) {
        return activities.get(id);
    }

    @GetMapping("/{id}/registrations")
    public ResponseEntity<List<RegistrationListItem>> registrations(
            @PathVariable long id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(registrations.listForAdmin(id, page, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ActivityView create(@Valid @RequestBody ActivityInput input, Principal admin) {
        return activities.create(input, admin.getName());
    }

    @PutMapping("/{id}")
    public ActivityView update(@PathVariable long id, @RequestParam long version,
                               @Valid @RequestBody ActivityInput input) {
        return activities.update(id, version, input);
    }

    @PostMapping("/{id}/publish")
    public ActivityView publish(@PathVariable long id) {
        return activities.publish(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id, @RequestParam long version) {
        activities.delete(id, version);
    }
}
