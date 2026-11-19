package com.campus.activity.config;

import javax.servlet.DispatcherType;
import javax.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeRequests()
                .requestMatchers(request -> request.getDispatcherType() == DispatcherType.ERROR).permitAll()
                .antMatchers(HttpMethod.GET, "/api/health", "/api/admin/auth/csrf").permitAll()
                .antMatchers(HttpMethod.GET, "/api/public/csrf").permitAll()
                .antMatchers(HttpMethod.GET, "/api/public/activities/*").permitAll()
                .antMatchers(HttpMethod.POST, "/api/public/activities/*/registrations").permitAll()
                .antMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().denyAll()
                .and()
            .requestCache().disable()
            .formLogin()
                .loginPage("/api/admin/auth/login")
                .loginProcessingUrl("/api/admin/auth/login")
                .successHandler((request, response, auth) -> response.setStatus(HttpServletResponse.SC_NO_CONTENT))
                .failureHandler((request, response, error) -> response.setStatus(HttpServletResponse.SC_UNAUTHORIZED))
                .and()
            .logout()
                .logoutUrl("/api/admin/auth/logout")
                .logoutSuccessHandler((request, response, auth) -> response.setStatus(HttpServletResponse.SC_NO_CONTENT))
                .and()
            .exceptionHandling()
                .authenticationEntryPoint((request, response, error) ->
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED));
        return http.build();
    }
}
