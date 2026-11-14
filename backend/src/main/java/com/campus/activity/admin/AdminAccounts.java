package com.campus.activity.admin;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminAccounts {

    @Bean
    UserDetailsService adminUserDetails(JdbcTemplate jdbc) {
        return username -> jdbc.query(
                "SELECT username, password_hash, enabled FROM admin_user WHERE username = ?",
                (rs, row) -> User.withUsername(rs.getString("username"))
                        .password(rs.getString("password_hash"))
                        .disabled(!rs.getBoolean("enabled"))
                        .roles("ADMIN")
                        .build(),
                username)
                .stream().findFirst()
                .orElseThrow(() -> new UsernameNotFoundException("Administrator not found"));
    }

    @Bean
    ApplicationRunner initializeAdmin(
            JdbcTemplate jdbc, PasswordEncoder encoder,
            @Value("${campus.admin.username}") String configuredUsername,
            @Value("${campus.admin.password}") String password) {
        return args -> {
            if (configuredUsername.isEmpty() && password.isEmpty()) {
                return;
            }
            if (Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM admin_user)", Boolean.class))) {
                return;
            }
            String username = configuredUsername.trim();
            if (!username.matches("[a-zA-Z0-9_.-]{1,64}") || password.length() < 12
                    || password.getBytes(StandardCharsets.UTF_8).length > 72) {
                throw new IllegalStateException(
                        "Set a valid CAMPUS_ADMIN_USERNAME and a password of 12 characters to 72 UTF-8 bytes.");
            }
            jdbc.update("INSERT INTO admin_user (username, password_hash, display_name) VALUES (?, ?, ?)",
                    username, encoder.encode(password), username);
        };
    }
}
