package com.example.AdminApi.services;

import com.example.AdminApi.models.User;
import com.example.AdminApi.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Custom UserDetailsService for Spring Security authentication.
 * Loads user data from database for authentication and authorization.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.info("🔐 [AUTH STEP 3] CustomUserDetailsService.loadUserByUsername() called for username: {}", username);
        log.debug("Attempting to load user from database...");

        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> {
                log.error("❌ [AUTH FAILED] User not found in database: {}", username);
                return new UsernameNotFoundException("User not found: " + username);
            });

        log.info("✅ [AUTH STEP 4] User loaded from database successfully");
        log.debug("User details: username={}, role={}, enabled={}, accountNonLocked={}",
            user.getUsername(), user.getRole(), user.isEnabled(), user.isAccountNonLocked());

        return user;
    }
}
