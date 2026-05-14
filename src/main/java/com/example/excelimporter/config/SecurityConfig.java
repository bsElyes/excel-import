package com.example.excelimporter.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Arrays;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final ImportSecurityProperties importSecurityProperties;
    private final Environment environment;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        if (isDevProfile()) {
            http.csrf().disable()
                    .headers().frameOptions().sameOrigin()
                    .and()
                    .authorizeRequests()
                    .antMatchers("/h2-console/**").permitAll()
                    .antMatchers("/api/imports/**").hasRole(importSecurityProperties.getImportRole().trim())
                    .anyRequest().denyAll()
                    .and()
                    .httpBasic();
            return http.build();
        }

        http.csrf().disable()
                .headers().frameOptions().deny()
                .and()
                .authorizeRequests()
                .antMatchers("/api/imports/**").hasRole(importSecurityProperties.getImportRole().trim())
                .anyRequest().denyAll()
                .and()
                .httpBasic();
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        String username = requireText(importSecurityProperties.getImportUsername(), "app.security.import-username");
        String password = requireText(importSecurityProperties.getImportPassword(), "app.security.import-password");
        String role = requireText(importSecurityProperties.getImportRole(), "app.security.import-role");
        if (role.startsWith("ROLE_")) {
            throw new IllegalStateException("app.security.import-role must not start with ROLE_.");
        }

        UserDetails importAdmin = User.withUsername(username)
                .password(passwordEncoder.encode(password))
                .roles(role)
                .build();
        return new InMemoryUserDetailsManager(importAdmin);
    }

    private String requireText(String value, String propertyName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(propertyName + " must be configured.");
        }
        return value.trim();
    }

    private boolean isDevProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("dev");
    }
}
